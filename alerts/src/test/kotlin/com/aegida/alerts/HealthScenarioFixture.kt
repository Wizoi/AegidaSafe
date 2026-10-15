package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.testkit.aScamText
import com.aegida.testkit.aChromePopup
import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.FeedUpdater
import com.aegida.core.feed.FeedVerifier
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
import com.aegida.core.time.Clock
import com.aegida.detectors.DnsDetector
import com.aegida.detectors.Evaluator
import com.aegida.detectors.EvaluatorMemory
import com.aegida.detectors.HealthDetector
import com.aegida.detectors.LinkDetector
import com.aegida.detectors.PopupSpamDetector
import com.aegida.testkit.FakeBaselines
import com.aegida.testkit.FakeCheckLog
import com.aegida.testkit.FakeFeedStore
import com.aegida.testkit.FakeListenerBinding
import com.aegida.testkit.FakeListenerTimes
import com.aegida.testkit.FakeNotificationShade
import com.aegida.testkit.FakeOutbox
import com.aegida.testkit.FakeSetupStore
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * docs/scenarios/health.md: what Android itself does to the listener (snooze returning, unbinding, binding again) and what
 * Home says then. The listener here does what AegidaListenerService does (that class needs Hilt, so it is not run on the
 * JVM): binding and unbinding go to ListenerLink, a posted notice runs a check and hides or snoozes what the check names.
 * "Unbinding" here is the service being destroyed (onDestroy); nothing relies on onListenerDisconnected (finding #134).
 * The phone, the checks and the listener that HealthScenariosTest and ListenerGraceScenariosTest drive.
 */
abstract class HealthScenarioFixture {
    protected var now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    protected val topic = NtfyTopic("aegida_" + "h3J4k5L6".repeat(4))
    protected val setupStore = FakeSetupStore(Setup("security.cloudflare-dns.com", emptySet(), stepsDone = SetupStep.entries.size))
    protected val feedStore = FakeFeedStore(
        ThreatFeed.of(now.minus(Duration.ofHours(3)), listOf(FeedSource("test", "test", 1)), listOf("evil.example"), emptyList()),
    )
    protected val checkLog = FakeCheckLog()
    protected val findings = Findings()
    protected val owner = Owner()
    protected val history = History()
    protected val http = Http()
    protected val outbox = FakeOutbox()
    protected val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    protected val baselines = FakeBaselines(
        Baseline("com.android.chrome", null, null, emptySet(), emptySet(), emptySet(), websites = setOf("https://news.example")),
    )
    protected var listenerEnabled = true
    protected var bootedAt: Instant? = now.minus(Duration.ofDays(2))
    protected var updatedAt: Instant? = now.minus(Duration.ofDays(3))
    protected var processStartedAt: Instant? = null

    /** Time that passes while a check waits (the test scheduler's virtual time); zero unless a test sets it. */
    protected var waited: () -> Long = { 0 }
    protected val clock = object : Clock {
        override fun now() = now.plusMillis(waited())
        override fun bootedAt() = this@HealthScenarioFixture.bootedAt
        override fun processStartedAt() = this@HealthScenarioFixture.processStartedAt
    }
    protected val listenerTimes = FakeListenerTimes()
    protected val binding = FakeListenerBinding()

    protected val evaluator = Evaluator(
        setOf(LinkDetector(), PopupSpamDetector(), DnsDetector(), HealthDetector()), clock,
        EvaluatorMemory(setupStore, feedStore, checkLog, outbox, baselines, listenerTimes), findings = findings,
    )
    protected val pipeline = AlertPipeline(findings, owner, history, GuardianLine(guardians, NtfySender(http), outbox)) { now }
    protected val runner = CheckRunner(
        {
            Snapshot(
                now, PrivateDns.Hostname("security.cloudflare-dns.com"), "com.android.chrome", listenerEnabled, true,
                Reading.NotPresent, Reading.NotPresent, listenerBound = binding.boundNow, aegidaUpdatedAt = updatedAt,
            )
        },
        evaluator, pipeline, BaselineKeeper(setupStore, baselines), OutboxFlusher(outbox, NtfySender(http), history, guardians, { now }), AlertGate(),
    )
    protected val shade = FakeNotificationShade(now)
    protected val link = ListenerLink(runner, listenerTimes, binding)
    protected val daily = run {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val sender = NtfySender(http)
        val switch = DeadMansSwitch(guardians, sender) { now }
        DailyCheck(
            DailyFeed(FeedUpdater(http, FeedVerifier(keys.public.encoded), feedStore) { now }, feedStore) { now }, runner,
            DailyPruning(history, findings, checkLog) { now }, DailyHeartbeat(switch, GuardianAlerts(guardians, sender, switch, AlertGate()), AlertGate()),
            checkLog, ListenerRepair(binding, clock),
        )
    }

    /** What AegidaListenerService does with each callback. */
    protected val listener = object : FakeNotificationShade.Listener {
        override suspend fun onConnected() {
            val actions = link.connected(shade.active(), OWN_PACKAGE)
            actions.hide.forEach(shade::cancel)
            actions.snooze.forEach { shade.snooze(it, Duration.ofHours(1)) }
        }
        override suspend fun onDisconnected() = link.disconnected()
        override suspend fun onPosted(event: NotificationEvent) {
            val outcome = runner.run(Trigger.NotificationPosted, event)
            outcome.notificationsToHide.forEach(shade::cancel)
            outcome.notificationsToSnooze.forEach { shade.snooze(it, Duration.ofHours(1)) }
        }
    }

    protected suspend fun advance(by: Duration) { now = now.plus(by); shade.advance(by) }

    protected suspend fun home() = HomeState.of(findings.open(), checkLog.latest())

    protected val scamText = aScamText(now)

    protected fun newSite() = aChromePopup(now, site = "https://shop.example/", title = "Spring sale", text = "Our new collection is here")

    protected fun latePosts() = checkLog.runs.count { it.trigger == Trigger.NotificationPosted }

    /** The process is killed: the listener is not told, and a new process starts with the flag unset. */
    protected fun kill() { shade.lose(); binding.boundNow = false }

    protected suspend fun lostForOverAnHour() {
        shade.connect(listener)
        shade.disconnect()
        advance(Duration.ofMinutes(61))
        runner.run(Trigger.Daily)
    }

    protected companion object {
        const val OWN_PACKAGE = "com.aegida"
    }
}
