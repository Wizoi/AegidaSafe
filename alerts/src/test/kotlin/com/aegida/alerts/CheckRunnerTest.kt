package com.aegida.alerts

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
        setOf(LinkDetector(), PopupSpamDetector(), DnsDetector(), HealthDetector()), { now }, setupStore, feedStore, checkLog, outbox, baselines,
    )
    private val pipeline = AlertPipeline(findings, owner, history, guardians, NtfySender(http), outbox) { now }
    private val runner = CheckRunner({ snapshot() }, evaluator, pipeline, BaselineKeeper(setupStore, baselines), OutboxFlusher(outbox, NtfySender(http), history, guardians, { now }), AlertGate())

    private fun snapshot() = Snapshot(now, dns, "com.android.chrome", true, true, Reading.NotPresent, Reading.NotPresent)

    // The shapes the emulator produced (findings #18): Chrome's site channel, and a shell-posted message.
    private fun siteNotification(n: Int) = NotificationEvent(
        key = "0|com.android.chrome|-1|p#http://localhost:8765/#$n|10150",
        packageName = "com.android.chrome",
        channelId = "web:http://localhost:8765;13435952006713133",
        title = "VIRUS DETECTED! Your phone is infected",
        text = "Tap now to clean your device",
        postedAt = now,
    )

    private val scamText = NotificationEvent(
        "0|com.android.shell|0|spike1|2000", "com.android.shell", "shell_cmd", "Bank alert",
        "Locked! Verify at https://login.evil.example/x", now,
    )

    @Test fun aFakeVirusPopUpIsHiddenAndOwnerAndTheGuardianAreTold() = runTest {
        val outcome = runner.run(Trigger.NotificationPosted, siteNotification(1))
        assertEquals(listOf(Problem.SiteNotifications), outcome.findings.map { it.key.problem })
        assertEquals(listOf(siteNotification(1).key), outcome.notificationsToHide)
        assertEquals(1, owner.shown.size)
        assertEquals(listOf("A website was sending fake warnings to Ruth's phone. Aegida hid them."), http.bodies)
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
