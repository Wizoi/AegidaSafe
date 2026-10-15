package com.aegida.alerts

import com.aegida.testkit.FakeSetupStore
import com.aegida.testkit.aScamText
import com.aegida.testkit.aChromePopup
import com.aegida.testkit.FakeFeedStore
import com.aegida.testkit.FakeCheckLog
import com.aegida.testkit.FakeOutbox
import com.aegida.testkit.FakeBaselines
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.Outcome
import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.health.CheckRun
import com.aegida.core.health.Trigger
import com.aegida.core.model.NotificationEvent
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Setup
import com.aegida.core.model.Snapshot
import com.aegida.core.net.NtfyTopic
import com.aegida.core.oem.Reading
import com.aegida.core.text.Problem
import com.aegida.detectors.DnsDetector
import com.aegida.detectors.Evaluator
import com.aegida.detectors.EvaluatorMemory
import com.aegida.detectors.HealthDetector
import com.aegida.detectors.LinkDetector
import com.aegida.detectors.PopupSpamDetector
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CheckRunnerTest {
    private val now = Instant.now()
    private val topic = NtfyTopic("aegida_" + "m4N5b6V7".repeat(4))

    private var dns: PrivateDns = PrivateDns.Hostname("security.cloudflare-dns.com")
    private val setupStore = FakeSetupStore(Setup("security.cloudflare-dns.com", emptySet()))
    private val feedStore = FakeFeedStore(
        ThreatFeed.of(now.minus(Duration.ofHours(3)), listOf(FeedSource("test", "test", 1)), listOf("evil.example"), emptyList()),
    )
    private val checkLog = FakeCheckLog()
    private val findings = Findings()
    private val owner = Owner()
    private val history = History()
    private val http = Http()
    private val outbox = FakeOutbox()
    private val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    private val baselines = FakeBaselines()

    private val evaluator = Evaluator(
        setOf(LinkDetector(), PopupSpamDetector(), DnsDetector(), HealthDetector()), { now },
        EvaluatorMemory(setupStore, feedStore, checkLog, outbox, baselines),
    )
    private val pipeline = AlertPipeline(findings, owner, history, GuardianLine(guardians, NtfySender(http), outbox)) { now }
    private val runner = CheckRunner(
        { snapshot() }, evaluator, pipeline, BaselineKeeper(setupStore, baselines),
        OutboxFlusher(outbox, NtfySender(http), history, guardians, { now }), CheckGate(AlertGate()),
    )

    private fun snapshot() = Snapshot(now, dns, "com.android.chrome", true, true, Reading.NotPresent, Reading.NotPresent)

    // The shapes the emulator produced (findings #18): Chrome's site channel, and a shell-posted message.
    private fun siteNotification(n: Int) = aChromePopup(now, n = n)

    private val scamText = aScamText(now, "https://login.evil.example/x", text = "Locked! Verify at https://login.evil.example/x", from = "Bank alert")

    @Test fun aFakeVirusPopUpIsHiddenAndOwnerAndTheGuardianAreTold() = runTest {
        val outcome = runner.run(Trigger.NotificationPosted, siteNotification(1))
        assertEquals(listOf(Problem.SiteNotifications), outcome.findings.map { it.key.problem })
        assertEquals(listOf(siteNotification(1).key), outcome.notificationsToHide)
        assertEquals(1, owner.shown.size)
        assertEquals(listOf("A website (lo*****) was sending fake warnings to Ruth's phone. Aegida hid them."), http.bodies)
    }

    @Test fun aTextMessageWithAScamLinkIsWarnedAboutButLeftInPlace() = runTest {
        val outcome = runner.run(Trigger.NotificationPosted, scamText)
        assertEquals(listOf(Problem.ScamLink), outcome.findings.map { it.key.problem })
        assertEquals(emptyList<String>(), outcome.notificationsToHide) // it may be from family
        assertEquals(1, owner.shown.size)
    }

    @Test fun everyRepeatedPopUpIsHiddenButOwnerIsOnlyAlertedOnce() = runTest {
        val outcomes = (1..3).map { runner.run(Trigger.NotificationPosted, siteNotification(it)) }
        assertEquals(listOf(siteNotification(1).key, siteNotification(2).key, siteNotification(3).key), outcomes.flatMap { it.notificationsToHide })
        assertEquals(1, owner.shown.size)
        assertEquals(1, http.bodies.size)
    }

    /** Scenario WP17 A2 (findings #148): a site she allowed is hidden each time; she is told once and the guardian never. */
    @Test fun aWebsiteSheAllowedThatKeepsSendingAlarmingMessagesIsHiddenEachTimeAndOnlySheIsTold() = runTest {
        setupStore.value = setupStore.value.copy(allowedSites = setOf("http://localhost:8765"))
        val outcomes = (1..3).map { runner.run(Trigger.NotificationPosted, siteNotification(it)) }
        assertEquals((1..3).map { siteNotification(it).key }, outcomes.flatMap { it.notificationsToHide })
        assertEquals(listOf(Problem.SiteScaryNews), owner.shown.map { it.problem })
        assertEquals(emptyList<String>(), http.bodies)
        assertEquals(emptyList<Any>(), outbox.rows)
    }

    /** Scenario WP17 A7: two sites she allowed each get their own warning; one site's quiet week does not silence the other. */
    @Test fun twoSitesSheAllowedThatSendAlarmingMessagesAreEachToldOnce() = runTest {
        setupStore.value = setupStore.value.copy(allowedSites = setOf("http://localhost:8765", "https://news.example"))
        runner.run(Trigger.NotificationPosted, siteNotification(1))
        runner.run(Trigger.NotificationPosted, aChromePopup(now, site = "https://news.example/", n = 2))
        runner.run(Trigger.NotificationPosted, siteNotification(3))
        assertEquals(listOf("http://localhost:8765", "https://news.example"), owner.shown.map { it.subject })
        assertEquals(emptyList<String>(), http.bodies)
    }

    /**
     * A fake warning raised a day and more ago, while the site was not allowed (or before findings #148), still open; the stored row
     * is the one a real check wrote, with its first-seen time moved back.
     */
    private suspend fun aDayOldOpenFakeWarning() {
        runner.run(Trigger.NotificationPosted, siteNotification(1))
        val stored = findings.open().single()
        assertEquals(Problem.SiteNotifications, stored.key.problem)
        findings.put(stored.copy(firstSeen = now.minus(Duration.ofHours(25))))
        http.bodies.clear()
    }

    /** Scenario WP17 A11 (findings #149): she now allows the site, so its old fake warning is not raised again with the guardian. */
    @Test fun anOpenFakeWarningForASiteSheNowAllowsDoesNotRemindTheGuardian() = runTest {
        aDayOldOpenFakeWarning()
        setupStore.value = setupStore.value.copy(allowedSites = setOf("http://localhost:8765"))
        runner.run(Trigger.AppStart)
        assertEquals(emptyList<String>(), http.bodies)
    }

    /** Scenario WP17 A11, must never: a fake warning from a site she has not allowed is still raised again after a day. */
    @Test fun anOpenFakeWarningForASiteSheHasNotAllowedStillRemindsTheGuardian() = runTest {
        aDayOldOpenFakeWarning()
        runner.run(Trigger.AppStart)
        assertEquals(listOf(com.aegida.core.text.Texts.Guardian.STILL_OPEN.format("Ruth")), http.bodies)
    }

    @Test fun anOrdinaryNotificationChangesNothing() = runTest {
        val outcome = runner.run(Trigger.NotificationPosted, scamText.copy(text = "See you at 6, bring the cake"))
        assertEquals(PipelineResult(0, 0, 0), outcome.pipeline)
        assertEquals(emptyList<String>(), outcome.notificationsToHide)
        assertEquals(emptyList<String>(), http.bodies)
    }

    @Test fun theWebsiteFilterBeingTurnedOffThenBackOnIsAlertedThenResolved() = runTest {
        dns = PrivateDns.Off
        assertEquals(1, runner.run(Trigger.SettingChanged).pipeline.alerted)
        dns = PrivateDns.Hostname("security.cloudflare-dns.com")
        val fixed = runner.run(Trigger.SettingChanged)
        assertEquals(1, fixed.pipeline.resolved)
        assertEquals(listOf(Outcome.OwnerWarned, Outcome.GuardianTold, Outcome.Fixed), history.entries.map { it.outcome })
    }

    @Test fun everyRunIsRecordedWithItsTrigger() = runTest {
        runner.run(Trigger.AppStart)
        runner.run(Trigger.NotificationPosted, scamText)
        assertEquals(listOf(Trigger.AppStart, Trigger.NotificationPosted), checkLog.runs.map(CheckRun::trigger))
    }
}
