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
    private val guardians = FakeGuardians(Guardians(alertsOn = true, momName = "Ruth", firstTopic = topic))
    private val pipeline = AlertPipeline(findings, Mom(), history, guardians, NtfySender(http), outbox) { now }
    private val flusher = OutboxFlusher(outbox, NtfySender(http), history) { now }
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

    @Test fun aMessageAWeekOldIsDroppedWithoutSending() = runTest {
        outbox.add(queued(ageHours = 24 * 8))
        flusher.flush()
        assertTrue(outbox.rows.isEmpty())
        assertEquals(0, http.posts.size)
    }
}
