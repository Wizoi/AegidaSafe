package com.aegida.alerts

import com.aegida.testkit.FakeSetupStore
import com.aegida.testkit.FakeFeedStore
import com.aegida.testkit.FakeCheckLog
import com.aegida.testkit.FakeOutbox
import com.aegida.testkit.FakeBaselines
import com.aegida.core.alert.Guardians
import com.aegida.core.health.Trigger
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.NotificationEvent
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Setup
import com.aegida.core.model.Snapshot
import com.aegida.core.net.NtfyTopic
import com.aegida.core.oem.Reading
import com.aegida.detectors.Evaluator
import com.aegida.detectors.EvaluatorMemory
import com.aegida.detectors.LinkDetector
import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.ThreatFeed
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Triggers overlap on a real phone (a burst of notifications, the daily job, an app resume, a tap): findings #49. */
class ConcurrencyTest {
    private val now = Instant.now()
    private val topic = NtfyTopic("aegida_" + "c0N1c2U3".repeat(4))
    private val findings = Findings()
    private val owner = Owner()
    private val history = History()
    private val http = Http().apply { onPost = { repeat(5) { yield() } } } // a send takes a while
    private val outbox = FakeOutbox()
    private val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    private val gate = AlertGate()
    private val feedStore = FakeFeedStore(
        ThreatFeed.of(now.minus(Duration.ofHours(3)), listOf(FeedSource("test", "test", 1)), listOf("evil.example"), emptyList()),
    )
    private val pipeline = AlertPipeline(findings, owner, history, GuardianLine(guardians, NtfySender(http), outbox)) { now }
    private val phone = Snapshot(now, PrivateDns.Automatic, "com.android.chrome", true, true, Reading.NotPresent, Reading.NotPresent)
    private val runner = CheckRunner(
        { phone },
        Evaluator(setOf(LinkDetector()), { now }, EvaluatorMemory(FakeSetupStore(Setup.NotDone), feedStore, FakeCheckLog(), outbox, FakeBaselines())),
        pipeline, BaselineKeeper(FakeSetupStore(), FakeBaselines()), OutboxFlusher(outbox, NtfySender(http), history, guardians, { now }), CheckGate(gate),
    )
    private val seen = SeenMarker(findings, history, owner)
    private val actions = FindingActions(
        seen,
        AppAllowing(findings, history, pipeline, seen) { now },
        ChangeAcceptor(findings, FakeBaselines(), { phone }, pipeline, seen) { now },
        gate,
    ) { now }

    private fun scam(n: Int) = NotificationEvent("k$n", "com.android.shell", "c", "Bank", "Verify at https://login.evil.example/x$n", now)

    @Test fun aBurstOfNotificationsAboutTheSameThingTellsTheGuardianOnce() = runTest {
        List(4) { n -> async { runner.run(Trigger.NotificationPosted, scam(n)) } }.awaitAll()
        assertEquals(1, owner.shown.size)
        assertEquals(1, http.bodies.size)
    }

    @Test fun aTapDuringACheckIsNotUndoneByTheCheckFinishingLater() = runTest {
        val check = async { runner.run(Trigger.NotificationPosted, scam(1)) }
        yield()
        val key = FindingKey(com.aegida.core.model.DetectorId.Link, com.aegida.core.text.Problem.ScamLink, "login.evil.example")
        val tapped = async { actions.acknowledge(key) }
        check.await()
        assertEquals(true, tapped.await())
        assertEquals(FindingState.Acknowledged, findings.rows[key]!!.state)
    }
}
