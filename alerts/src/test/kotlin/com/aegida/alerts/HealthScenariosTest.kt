package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.testkit.aScamText
import com.aegida.testkit.aChromePopup
import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.health.Trigger
import com.aegida.core.model.Baseline
import com.aegida.core.model.HomeState
import com.aegida.core.model.NotificationEvent
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Setup
import com.aegida.core.model.SetupStep
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
import com.aegida.testkit.FakeBaselines
import com.aegida.testkit.FakeCheckLog
import com.aegida.testkit.FakeFeedStore
import com.aegida.testkit.FakeNotificationShade
import com.aegida.testkit.FakeOutbox
import com.aegida.testkit.FakeSetupStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * docs/scenarios/health.md: what Android itself does to the listener (snooze returning, unbinding, binding again) and what
 * Home says then. The listener here does what AegidaListenerService does (that class needs Hilt, so it is not run on the
 * JVM): connected runs an app-start check, a posted notice runs a check and hides or snoozes what the check names.
 * Rows marked "today" pin today's behaviour where it leaves Mom with a wrong picture (findings #111, #112): Kevin decides.
 */
class HealthScenariosTest {
    private var now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val topic = NtfyTopic("aegida_" + "h3J4k5L6".repeat(4))
    private val setupStore = FakeSetupStore(Setup("security.cloudflare-dns.com", emptySet(), stepsDone = SetupStep.entries.size))
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
    private val baselines = FakeBaselines(
        Baseline("com.android.chrome", null, null, emptySet(), emptySet(), emptySet(), websites = setOf("https://news.example")),
    )
    private var listenerEnabled = true

    private val evaluator = Evaluator(
        setOf(LinkDetector(), PopupSpamDetector(), DnsDetector(), HealthDetector()), { now },
        EvaluatorMemory(setupStore, feedStore, checkLog, outbox, baselines),
    )
    private val pipeline = AlertPipeline(findings, owner, history, GuardianLine(guardians, NtfySender(http), outbox)) { now }
    private val runner = CheckRunner(
        {
            Snapshot(
                now, PrivateDns.Hostname("security.cloudflare-dns.com"), "com.android.chrome", listenerEnabled, true,
                Reading.NotPresent, Reading.NotPresent,
            )
        },
        evaluator, pipeline, BaselineKeeper(setupStore, baselines), OutboxFlusher(outbox, NtfySender(http), history, guardians, { now }), AlertGate(),
    )
    private val shade = FakeNotificationShade(now)

    /** What AegidaListenerService does with each callback. */
    private val listener = object : FakeNotificationShade.Listener {
        override suspend fun onConnected() { runner.run(Trigger.AppStart) }
        override suspend fun onPosted(event: NotificationEvent) {
            val outcome = runner.run(Trigger.NotificationPosted, event)
            outcome.notificationsToHide.forEach(shade::cancel)
            outcome.notificationsToSnooze.forEach { shade.snooze(it, Duration.ofHours(1)) }
        }
    }

    private suspend fun advance(by: Duration) { now = now.plus(by); shade.advance(by) }

    private suspend fun home() = HomeState.of(findings.open(), checkLog.latest())

    private val scamText = aScamText(now)

    private fun newSite() = aChromePopup(now, site = "https://shop.example/", title = "Spring sale", text = "Our new collection is here")

    @Test fun aNewWebsitesNoticeThatComesBackFromItsSnoozeIsPutAsideAgainWithoutTellingAnyoneTwice() = runTest {
        shade.connect(listener)
        shade.post(newSite())
        assertTrue("asked about, so put aside", shade.isSnoozed(newSite().key))
        val toldAfterFirst = http.posts.size
        advance(Duration.ofMinutes(61))
        assertEquals("the phone brought it back and Aegida saw it again", 2, shade.delivered.count { it.key == newSite().key })
        assertTrue("she has not answered yet, so it is put aside again", shade.isSnoozed(newSite().key))
        assertEquals("one question for Mom", 1, owner.shown.count { it.problem == Problem.NewWebsiteNotifications })
        assertEquals("nothing more to the guardian", toldAfterFirst, http.posts.size)
    }

    @Test fun afterAPhoneRestartOrAppUpdateTheListenerBindsAgainAndAegidaChecksThePhone() = runTest {
        shade.connect(listener)
        shade.disconnect()
        val before = checkLog.runs.size
        advance(Duration.ofMinutes(5))
        shade.connect(listener)
        assertEquals(before + 1, checkLog.runs.size)
        assertEquals(Trigger.AppStart, checkLog.latest()!!.trigger)
        shade.post(scamText.copy(postedAt = now))
        assertEquals("notices are heard again once it is bound", listOf(Problem.ScamLink), owner.shown.map { it.problem })
    }

    /** Today: Android does not replay what was posted while unbound and Aegida never asks for it (finding #111). */
    @Test fun todayAScamTextThatArrivedWhileAegidaWasNotListeningIsNeverChecked() = runTest {
        shade.connect(listener)
        shade.disconnect()
        shade.post(scamText)
        advance(Duration.ofMinutes(10))
        shade.connect(listener)
        assertTrue("the text is still on her screen", shade.active().any { it.key == scamText.key })
        assertEquals("never checked: no warning", emptyList<Any>(), owner.shown)
        assertEquals("the guardian hears nothing", 0, http.posts.size)
        assertTrue("and Home says no known problems", home() is HomeState.NoKnownProblems)
    }

    /**
     * Today: notification access is on in Settings, so the snapshot says the listener is enabled, but the system has unbound it.
     * The daily job runs, no notice is heard, and Home says no known problems (finding #112).
     */
    @Test fun todayWhenTheListenerIsOnButUnboundDuringTheDailyCheckHomeStillSaysNoKnownProblems() = runTest {
        shade.connect(listener)
        shade.disconnect()
        shade.post(scamText)
        advance(Duration.ofHours(20))
        runner.run(Trigger.Daily)
        assertEquals("nothing says Aegida cannot hear notices", emptyList<Any>(), findings.open())
        assertTrue(home() is HomeState.NoKnownProblems)
        assertEquals(0, http.posts.size)
    }

    @Test fun whenNotificationAccessIsTurnedOffHomeAsksHerToTurnItBackOn() = runTest {
        listenerEnabled = false
        runner.run(Trigger.Daily)
        assertEquals(listOf(Problem.ListenerOff), findings.open().map { it.key.problem })
        assertTrue(home() is HomeState.NeedsTaps)
    }
}
