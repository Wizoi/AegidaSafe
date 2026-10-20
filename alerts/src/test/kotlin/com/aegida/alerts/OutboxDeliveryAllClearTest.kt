package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
import com.aegida.testkit.FakeOutbox
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Notes that waited in the outbox (docs/scenarios/guardian-notes.md GN19-GN21, findings #116, #131): once delivered later they count for
 * exactly the app, or the warnings, they were about, so "Was that you? Yes" is told to the guardian only when they really heard of it.
 */
class OutboxDeliveryAllClearTest {
    private var now = Instant.now()
    private val findings = Findings()
    private val history = History()
    private val http = Http()
    private val outbox = FakeOutbox()
    private val topic = NtfyTopic.generate("Ruth", java.util.Random(3))
    private val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    private val pipeline = AlertPipeline(findings, Owner(), history, GuardianLine(guardians, NtfySender(http), outbox)) { now }
    private val flusher = OutboxFlusher(outbox, NtfySender(http), history, guardians) { now }
    private val offline = HttpResult.Failed(null, FailureReason.Offline)
    private val ok = HttpResult.Ok(200, "{}")

    private val noisy = Finding(
        FindingKey(DetectorId.NoisyApp, Problem.NoisyApp, "com.example.pdf"), mapOf(Evidence.APP_LABEL to "Image to PDF"), now, now,
    )

    private fun newAppQuestionFor(pkg: String, label: String, seen: Instant = now) =
        Finding(FindingKey(DetectorId.NewApp, Problem.NewAppOutsidePlay, pkg), mapOf(Evidence.APP_LABEL to label), seen, seen)

    /** GN19, findings #116: A's note was delivered later from the outbox, B's (queued a day later) never was; B's marker is long gone. */
    @Test fun twoAppsNotesWaitedOfflineOnlyAsIsWasDeliveredAndSheSaysYesToBTheGuardianHearsNothing() = runTest {
        val a = newAppQuestionFor("org.a.app", "Alpha")
        http.result = offline
        pipeline.handle(listOf(a), setOf(DetectorId.NewApp))
        findings.rows[a.key] = findings.rows.getValue(a.key).copy(state = FindingState.Escalated) // already reminded: no reminder muddies this
        now = now.plus(Duration.ofHours(25)) // the repeat back-off for the kind is over
        val b = newAppQuestionFor("org.b.app", "Bravo")
        pipeline.handle(listOf(b), setOf(DetectorId.NewApp))
        assertEquals(2, outbox.rows.size)
        // Back online for exactly one note: A's, the oldest; then the network drops again.
        http.posts.clear()
        http.onPost = {
            http.result = if (http.posts.isEmpty()) ok
            else offline
        }
        now = now.plus(Duration.ofMinutes(16))
        assertEquals(1, flusher.flush())
        now = now.plus(Duration.ofDays(8)) // B is given up on; History says so
        flusher.flush()
        now = now.plus(Duration.ofDays(31)) // and the marker goes
        flusher.flush()
        assertEquals(emptyList<Any>(), outbox.rows)
        http.onPost = {}
        http.result = ok
        http.posts.clear(); http.bodies.clear()
        pipeline.tellConfirmed(b)
        assertEquals(0, http.bodies.size)
        pipeline.tellConfirmed(a)
        assertEquals(1, http.bodies.size)
    }

    /** GN20, findings #131: one reminder covers an ads-app question and a new-app question; it waited offline, then went. */
    @Test fun aReminderAboutTwoKindsWaitedOfflineThenWasDeliveredAndSheSaysYesToEachSoEachAllClearIsSent() = runTest {
        guardians.value = guardians.value.copy(alertsOn = false) // no alarms, so the reminder is the first they hear
        val a = noisy
        val b = newAppQuestionFor("org.b.app", "Bravo")
        pipeline.handle(listOf(a, b), setOf(DetectorId.NoisyApp, DetectorId.NewApp))
        guardians.value = guardians.value.copy(alertsOn = true)
        now = now.plus(Duration.ofHours(25))
        http.result = offline
        pipeline.handle(listOf(a.copy(lastSeen = now), b.copy(lastSeen = now)), setOf(DetectorId.NoisyApp, DetectorId.NewApp))
        assertEquals(1, outbox.rows.size) // one reminder for both
        http.result = ok
        now = now.plus(Duration.ofMinutes(16))
        assertEquals(1, flusher.flush())
        http.posts.clear(); http.bodies.clear()
        pipeline.tellConfirmed(a)
        pipeline.tellConfirmed(b)
        assertEquals(2, http.bodies.size)
    }

    /** GN21, findings #116: a note queued by the build before Room version 4 has no detail; its delivery proves nothing about any one app. */
    @Test fun aNoteQueuedBeforeTheUpdateIsDeliveredButSheSaysYesAndNoAllClearIsSent() = runTest {
        val a = newAppQuestionFor("org.a.app", "Alpha")
        http.result = offline
        pipeline.handle(listOf(a), setOf(DetectorId.NewApp))
        outbox.rows[0] = outbox.rows[0].copy(detail = null) // what the old build stored
        http.result = ok
        now = now.plus(Duration.ofMinutes(16))
        assertEquals(1, flusher.flush())
        http.posts.clear(); http.bodies.clear()
        pipeline.tellConfirmed(a)
        assertEquals(0, http.bodies.size) // fails closed
    }
}
