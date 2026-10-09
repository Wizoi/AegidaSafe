package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.alert.Outcome
import com.aegida.core.alert.QueuedMessage
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class OutboxFlusherTest {
    private var now = Instant.now()
    private val topic = NtfyTopic("aegida_" + "q1W2e3R4".repeat(4))
    private val http = Http()
    private val outbox = FakeOutbox()
    private val history = History()
    private val findings = Findings()
    private val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    private val pipeline = AlertPipeline(findings, Owner(), history, guardians, NtfySender(http), outbox) { now }
    private val flusher = OutboxFlusher(outbox, NtfySender(http), history, guardians) { now }
    private val offline = HttpResult.Failed(null, FailureReason.Offline)

    private fun scam() = Finding(FindingKey(DetectorId.Link, Problem.ScamLink, "m1"), emptyMap(), now, now)
    private fun queued(ageHours: Long, tried: Instant = now.minus(Duration.ofHours(ageHours)), attempts: Int = 1) =
        QueuedMessage(0, topic.value, "Ruth", "text", false, Problem.ScamLink, now.minus(Duration.ofHours(ageHours)), attempts, tried)

    @Test fun aMessageThatCouldNotBeSentIsKeptAndHistorySaysGuardianNotReached() = runTest {
        http.result = offline
        pipeline.handle(listOf(scam()), setOf(DetectorId.Link))
        assertEquals(1, outbox.rows.size)
        assertEquals(Outcome.GuardianNotReached, history.entries.last().outcome)
    }

    @Test fun afterTheWaitItIsSentOnceDeliveredIsRemovedAndHistoryIsUpdated() = runTest {
        http.result = offline
        pipeline.handle(listOf(scam()), setOf(DetectorId.Link))
        http.result = HttpResult.Ok(200, "{}")
        assertEquals(0, flusher.flush()) // too soon: backing off
        now = now.plus(Duration.ofMinutes(16))
        assertEquals(1, flusher.flush())
        assertTrue(outbox.rows.isEmpty())
        assertEquals(Outcome.GuardianTold, history.entries.last().outcome)
        assertEquals(0, flusher.flush())
    }

    @Test fun aFailedRetryBacksOffLongerAndStopsTheRest() = runTest {
        http.result = offline
        outbox.add(queued(ageHours = 2)); outbox.add(queued(ageHours = 1))
        assertEquals(0, flusher.flush())
        assertEquals(1, http.posts.size) // stopped at the first failure
        assertEquals(listOf(2, 1), outbox.rows.map { it.attempts })
        now = now.plus(Duration.ofMinutes(20)) // 15 minutes was the first wait, 30 is the second
        http.posts.clear()
        flusher.flush()
        assertEquals(1, http.posts.size) // only the second is due again; the first is still waiting out its 30 minutes
        assertEquals(listOf(2, 2), outbox.rows.map { it.attempts })
    }

    @Test fun aMessageAWeekOldIsNotSentAndHistorySaysGuardianNeverReached() = runTest {
        outbox.add(queued(ageHours = 24 * 8))
        flusher.flush()
        assertEquals(0, http.posts.size)
        assertEquals(listOf(Outcome.GuardianNeverReached), history.entries.map { it.outcome })
        assertEquals(1, outbox.rows.size) // a marker, so the outbox is not "empty" and nothing reads as fixed
        flusher.flush()
        assertEquals(1, history.entries.size) // said once
        assertEquals(1, outbox.rows.size)
        assertEquals(0, http.posts.size)
    }

    @Test fun theGaveUpMarkerKeepsTheOldestUndeliveredTimeOldSoGuardianUnreachableStaysOpen() = runTest {
        val old = queued(ageHours = 24 * 8)
        outbox.add(old)
        flusher.flush()
        assertEquals(old.queuedAt, outbox.all().minOf { it.queuedAt })
    }

    @Test fun theMarkerGoesWhenALaterMessageIsDelivered() = runTest {
        outbox.add(queued(ageHours = 24 * 8))
        flusher.flush()
        http.result = HttpResult.Ok(200, "{}")
        outbox.add(queued(ageHours = 1))
        assertEquals(1, flusher.flush())
        assertTrue(outbox.rows.isEmpty())
        assertEquals(Outcome.GuardianTold, history.entries.last().outcome)
    }

    @Test fun theMarkerGoesAfterAMonth() = runTest {
        outbox.add(queued(ageHours = 24 * 8))
        flusher.flush()
        now = now.plus(Duration.ofDays(25))
        flusher.flush()
        assertTrue(outbox.rows.isEmpty())
    }

    @Test fun afterOneMessageGetsThroughTheOldestIsNotLeftWaitingOutItsBackOff() = runTest {
        // The oldest has failed many times (6 h wait, tried 1 h ago); the newer one is fresh and due.
        outbox.add(queued(ageHours = 30, tried = now.minus(Duration.ofHours(1)), attempts = 8))
        outbox.add(queued(ageHours = 2, tried = now.minus(Duration.ofHours(2))))
        http.result = HttpResult.Ok(200, "{}")
        assertEquals(2, flusher.flush())
        assertTrue(outbox.rows.isEmpty())
    }

    @Test fun messagesAreSentOldestFirstAndABackedOffOldestIsNotSkippedWhenNothingGoes() = runTest {
        outbox.add(queued(ageHours = 30, tried = now.minus(Duration.ofHours(1)), attempts = 8)) // waiting, nothing sent yet
        outbox.add(queued(ageHours = 2, tried = now.minus(Duration.ofHours(2))))
        http.result = offline
        flusher.flush()
        assertEquals(1, http.posts.size) // the newer one was tried, the oldest still backs off
        assertEquals(listOf(8, 2), outbox.rows.map { it.attempts })
    }

    @Test fun oneFailureAfterASuccessDoesNotStopTheRest() = runTest {
        outbox.add(queued(ageHours = 3)); outbox.add(queued(ageHours = 2)); outbox.add(queued(ageHours = 1))
        var n = 0
        http.onPost = { n++ ; http.result = if (n == 2) HttpResult.Failed(500, FailureReason.HttpError) else HttpResult.Ok(200, "{}") }
        assertEquals(2, flusher.flush())
        assertEquals(3, http.posts.size)
        assertEquals(1, outbox.rows.size)
    }

    @Test fun nothingIsSentWhileGuardianAlertsAreOff() = runTest {
        outbox.add(queued(ageHours = 2))
        guardians.value = guardians.value.copy(alertsOn = false)
        http.result = HttpResult.Ok(200, "{}")
        assertEquals(0, flusher.flush())
        assertEquals(0, http.posts.size)
        assertEquals(1, outbox.rows.size) // kept: it goes if she turns alerts back on
    }

    @Test fun nothingIsSentWithoutAGuardianTopic() = runTest {
        outbox.add(queued(ageHours = 2))
        guardians.value = guardians.value.copy(firstTopic = null)
        assertEquals(0, flusher.flush())
        assertEquals(0, http.posts.size)
    }

    @Test fun messagesForAReplacedGuardianAreDroppedNotSentToTheOldTopic() = runTest {
        val old = queued(ageHours = 2).copy(topic = "aegida_" + "Z9y8X7w6".repeat(4))
        outbox.add(old)
        outbox.add(queued(ageHours = 1))
        http.result = HttpResult.Ok(200, "{}")
        assertEquals(1, flusher.flush())
        assertEquals(1, http.posts.size)
        assertTrue(http.posts.single().first.path.contains(topic.value))
        assertTrue(outbox.rows.isEmpty())
    }

    @Test fun aMessageStampedWhileTheClockWasAheadStillExpires() = runTest {
        outbox.add(queued(ageHours = -24 * 30)) // queued "a month from now"
        flusher.flush()
        assertEquals(0, http.posts.size)
        assertEquals(listOf(Outcome.GuardianNeverReached), history.entries.map { it.outcome })
    }

    @Test fun aRetryTimeInTheFutureDoesNotBlockTheRetry() = runTest {
        outbox.add(queued(ageHours = 2, tried = now.plus(Duration.ofDays(2)), attempts = 3))
        http.result = HttpResult.Ok(200, "{}")
        assertEquals(1, flusher.flush())
    }
}
