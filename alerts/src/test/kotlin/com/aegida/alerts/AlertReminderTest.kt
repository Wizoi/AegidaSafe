package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.Outcome
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.net.Endpoint
import com.aegida.core.net.HttpClient
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Reminders are one message per run, send before store, and a clock that went backwards holds nothing back (R8, R20, R14). */
class AlertReminderTest {
    private var now: Instant = Instant.now()
    private val topic = NtfyTopic("aegida_" + "r7T6y5U4".repeat(4))
    private val findings = Findings()
    private val owner = Owner()
    private val history = History()
    private val http = Http()
    private val outbox = FakeOutbox()
    private val settings = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    private val pipeline = AlertPipeline(findings, owner, history, settings, NtfySender(http), outbox) { now }

    private fun link(host: String, seen: Instant = now) = Finding(FindingKey(DetectorId.Link, Problem.ScamLink, host), emptyMap(), seen, seen)
    private fun after(d: Duration) { now = now.plus(d) }
    private fun reminders() = http.bodies.filter { it == Texts.Guardian.STILL_OPEN.format("Ruth") }

    @Test fun fiveUntappedWarningsGiveOneReminderAndAllMoveToEscalated() = runTest {
        val all = (1..5).map { link("h$it.example") }
        pipeline.handle(all, setOf(DetectorId.Link))
        after(Duration.ofHours(25))
        pipeline.handle(all.map { it.copy(lastSeen = now) }, setOf(DetectorId.Link))
        assertEquals(1, reminders().size)
        assertTrue(all.all { findings.rows[it.key]!!.state == FindingState.Escalated })
        after(Duration.ofHours(1))
        pipeline.handle(all.map { it.copy(lastSeen = now) }, setOf(DetectorId.Link))
        assertEquals(1, reminders().size)
    }

    /** Kevin, Oct 9: only the serious ones say "please call"; the rest leave the call to the guardian. */
    @Test fun aWaitingUrgentWarningGetsTheFirmReminderAndAnythingElseTheGentleOne() = runTest {
        val urgent = Finding(FindingKey(DetectorId.Drift, Problem.RemoteControlApp, "org.remote.app"), emptyMap(), now, now)
        pipeline.handle(listOf(urgent), setOf(DetectorId.Drift))
        after(Duration.ofHours(25))
        pipeline.handle(listOf(urgent.copy(lastSeen = now)), setOf(DetectorId.Drift))
        assertEquals(1, http.bodies.count { it == Texts.Guardian.STILL_OPEN_URGENT.format("Ruth") })
        assertEquals("the gentle reminder is for warnings that are not urgent", 0, reminders().size)
    }

    @Test fun aGuardianRemindedInTheLastDayIsNotRemindedAgainByALaterRun() = runTest {
        val a = link("a.example")
        pipeline.handle(listOf(a), setOf(DetectorId.Link))
        after(Duration.ofHours(2))
        val c = link("c.example")
        pipeline.handle(listOf(a.copy(lastSeen = now), c), setOf(DetectorId.Link))
        after(Duration.ofHours(23)) // a is 25 h old and due, c is 23 h old and not yet
        pipeline.handle(listOf(a.copy(lastSeen = now), c.copy(lastSeen = now)), setOf(DetectorId.Link))
        assertEquals(1, reminders().size)
        after(Duration.ofHours(2)) // c is now due, but the guardian was reminded 2 h ago
        pipeline.handle(listOf(a.copy(lastSeen = now), c.copy(lastSeen = now)), setOf(DetectorId.Link))
        assertEquals(1, reminders().size)
        after(Duration.ofHours(23)) // a day and an hour after the reminder
        pipeline.handle(listOf(a.copy(lastSeen = now), c.copy(lastSeen = now)), setOf(DetectorId.Link))
        assertEquals(2, reminders().size)
    }

    @Test fun aReminderIsRepeatedIfTheProcessDiesWhileSendingIt() = runTest {
        val f = link("kill.example")
        pipeline.handle(listOf(f), setOf(DetectorId.Link))
        after(Duration.ofHours(25))
        val dying = object : HttpClient by http {
            var die = true
            override suspend fun post(endpoint: Endpoint, body: String, headers: Map<String, String>): HttpResult {
                if (die) { die = false; throw IllegalStateException("process killed") }
                return http.post(endpoint, body, headers)
            }
        }
        val killable = AlertPipeline(findings, owner, history, settings, NtfySender(dying), outbox) { now }
        try { killable.handle(listOf(f.copy(lastSeen = now)), setOf(DetectorId.Link)) } catch (e: IllegalStateException) { /* died here */ }
        killable.handle(listOf(f.copy(lastSeen = now)), setOf(DetectorId.Link))
        assertEquals(1, reminders().size)
        assertEquals(FindingState.Escalated, findings.rows[f.key]!!.state)
    }

    @Test fun aGuardianToldWhileTheClockWasAheadIsStillToldAgainAfterADayOfRealTime() = runTest {
        val future = now.plus(Duration.ofDays(30))
        history.add(HistoryEntry(future, Problem.ScamLink, Outcome.GuardianTold))
        after(Duration.ofHours(25))
        pipeline.handle(listOf(link("late.example")), setOf(DetectorId.Link))
        assertEquals(1, http.bodies.size)
    }

    @Test fun aFindingStampedInTheFutureIsStillRemindedAfterADay() = runTest {
        val f = link("ahead.example", seen = now.plus(Duration.ofDays(30)))
        findings.put(f.copy(state = FindingState.Open))
        pipeline.handle(listOf(f.copy(lastSeen = now)), setOf(DetectorId.Link))
        after(Duration.ofHours(25))
        pipeline.handle(listOf(f.copy(lastSeen = now)), setOf(DetectorId.Link))
        assertEquals(1, reminders().size)
    }

    @Test fun aBlankOwnerNameTitlesTheGuardianMessageWithTheAppName() = runTest {
        settings.value = settings.value.copy(ownerName = "")
        pipeline.handle(listOf(link("blank.example")), setOf(DetectorId.Link))
        assertEquals(Texts.APP_NAME, http.posts.single().third["Title"])
    }
}
