package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.alert.Outcome
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Repeat warnings back off, and a warning still open after a day is raised once more (fake clock throughout). */
class AlertBackoffTest {
    private var now: Instant = Instant.now()
    private val topic = NtfyTopic("aegida_" + "b4C5k6O7".repeat(4))
    private val findings = Findings()
    private val owner = Owner()
    private val history = History()
    private val http = Http()
    private val settings = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    private val pipeline = AlertPipeline(findings, owner, history, settings, NtfySender(http), FakeOutbox()) { now }
    private val actions = FindingActions(findings, history, owner, FakeBaselines(), { error("not used") }, pipeline, AlertGate()) { now }

    private fun link(host: String) = Finding(FindingKey(DetectorId.Link, Problem.ScamLink, host), emptyMap(), now, now)
    private fun after(d: Duration) { now = now.plus(d) }

    /** A message with a scam link arrives, she taps "Got it". */
    private suspend fun scamArrives(host: String): Finding {
        val f = link(host)
        pipeline.handle(listOf(f), setOf(DetectorId.Link))
        actions.acknowledge(f.key)
        return f
    }

    @Test fun aSecondScamMessageTheSameDayWarnsHerButDoesNotMessageTheGuardianAgain() = runTest {
        scamArrives("one.example")
        after(Duration.ofHours(2))
        scamArrives("two.example")
        assertEquals(2, owner.shown.size)
        assertEquals(1, http.bodies.size)
    }

    @Test fun aNewGuardianIsToldAboutAProblemThatWasAlreadySentToTheOldOne() = runTest {
        scamArrives("one.example")
        assertEquals(1, http.bodies.size)
        after(Duration.ofHours(2))
        settings.save(settings.current().copy(firstTopic = NtfyTopic("aegida_" + "n3W6u4A1".repeat(4)), pairedAt = now))
        after(Duration.ofMinutes(5))
        scamArrives("two.example")
        assertEquals("the new guardian is told, the old one's message does not count", 2, http.bodies.size)
    }

    @Test fun theGuardianIsToldAgainAfterADayThenAfterTwoThenAfterFour() = runTest {
        var told = 0
        for ((i, gap) in listOf(0L, 25L, 49L, 97L).withIndex()) {
            after(Duration.ofHours(gap))
            scamArrives("host$i.example")
            assertEquals("message ${i + 1}", i + 1, http.bodies.size.also { told = it })
        }
        // 30 hours after the last message is inside the two day wait that now applies
        after(Duration.ofHours(30))
        scamArrives("later.example")
        assertEquals(told, http.bodies.size)
    }

    @Test fun backOffNeverHoldsBackAnUrgentProblem() = runTest {
        val urgent = Finding(FindingKey(DetectorId.Drift, Problem.AccessibilityAdded, "org.a"), emptyMap(), now, now)
        val urgent2 = Finding(FindingKey(DetectorId.Drift, Problem.AccessibilityAdded, "org.b"), emptyMap(), now, now)
        pipeline.handle(listOf(urgent), setOf(DetectorId.Drift))
        after(Duration.ofMinutes(5))
        pipeline.handle(listOf(urgent, urgent2), setOf(DetectorId.Drift))
        assertEquals(2, http.bodies.size)
    }

    @Test fun aWarningStillOpenAfterADayRemindsTheGuardianOnceAndStaysOnHome() = runTest {
        val f = link("open.example")
        pipeline.handle(listOf(f), setOf(DetectorId.Link))
        assertEquals(1, http.bodies.size)

        after(Duration.ofHours(23))
        pipeline.handle(listOf(f.copy(lastSeen = now)), setOf(DetectorId.Link))
        assertEquals(1, http.bodies.size)

        after(Duration.ofHours(2))
        pipeline.handle(listOf(f.copy(lastSeen = now)), setOf(DetectorId.Link))
        assertEquals(2, http.bodies.size)
        assertEquals("Ruth hasn't dealt with a warning from Aegida yet. It has been a day. Please call her.", http.bodies.last())
        assertEquals(FindingState.Escalated, findings.rows[f.key]!!.state)
        assertTrue(findings.rows[f.key] in findings.open())
        assertTrue(Outcome.GuardianReminded in history.entries.map { it.outcome })

        after(Duration.ofHours(30))
        pipeline.handle(listOf(f.copy(lastSeen = now)), setOf(DetectorId.Link))
        assertEquals("one reminder, not one a day", 2, http.bodies.size)
        assertEquals(1, owner.shown.size)
    }

    @Test fun aWarningSheHasSeenIsNotRaisedAgain() = runTest {
        val f = scamArrives("seen.example")
        after(Duration.ofHours(30))
        pipeline.handle(emptyList(), setOf(DetectorId.Link))
        assertEquals(1, http.bodies.size)
        assertEquals(FindingState.Acknowledged, findings.rows[f.key]!!.state)
    }

    @Test fun aReminderThatCannotBeSentIsKeptForRetryAndNotRepeatedEveryCheck() = runTest {
        val f = link("offline.example")
        pipeline.handle(listOf(f), setOf(DetectorId.Link))
        http.result = com.aegida.core.net.HttpResult.Failed(null, com.aegida.core.net.FailureReason.Offline)
        after(Duration.ofHours(25))
        pipeline.handle(listOf(f.copy(lastSeen = now)), setOf(DetectorId.Link))
        val tries = http.posts.size
        after(Duration.ofHours(1))
        pipeline.handle(listOf(f.copy(lastSeen = now)), setOf(DetectorId.Link))
        assertEquals(tries, http.posts.size)
    }
}
