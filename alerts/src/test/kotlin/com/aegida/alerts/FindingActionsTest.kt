package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryRows
import com.aegida.core.alert.Outcome
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.HomeState
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Fix
import com.aegida.core.text.Problem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FindingActionsTest {
    private val now = Instant.now()
    private val findings = Findings()
    private val owner = Owner()
    private val history = History()
    private val http = Http()
    private val topic = NtfyTopic.generate(java.util.Random(3))
    private val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    private val pipeline = AlertPipeline(findings, owner, history, guardians, NtfySender(http), FakeOutbox()) { now }
    private val actions = FindingActions(findings, history, owner) { now }

    private val scam = Finding(FindingKey(DetectorId.Link, Problem.ScamLink, "evil.example"), emptyMap(), now.minus(Duration.ofMinutes(1)), now)

    @Test fun aSeenWarningLeavesHomeClearsItsBannerAndIsWrittenToHistory() = runTest {
        pipeline.handle(listOf(scam), setOf(DetectorId.Link))
        assertTrue(HomeState.of(findings.open(), null) is HomeState.NeedsTaps)

        assertTrue(actions.acknowledge(scam.key))

        assertEquals(FindingState.Acknowledged, findings.get(scam.key)!!.state)
        assertFalse(HomeState.of(findings.open(), null) is HomeState.NeedsTaps)
        assertEquals(listOf(scam.key), owner.cleared)
        val row = HistoryRows.of(history.entries).single { Outcome.Acknowledged.forMom in it.detail }
        assertEquals("A message had a scam link", row.title)
        assertTrue(row.handled)
    }

    @Test fun markingSomethingSeenTwiceOrSomethingUnknownDoesNothing() = runTest {
        pipeline.handle(listOf(scam), setOf(DetectorId.Link))
        assertTrue(actions.acknowledge(scam.key))
        assertFalse(actions.acknowledge(scam.key))
        assertFalse(actions.acknowledge(FindingKey(DetectorId.Link, Problem.ScamLink, "nothing.example")))
        assertEquals(1, history.entries.count { it.outcome == Outcome.Acknowledged })
    }

    @Test fun theSameScamComingBackAfterBeingSeenWarnsAgain() = runTest {
        pipeline.handle(listOf(scam), setOf(DetectorId.Link))
        actions.acknowledge(scam.key)
        assertEquals(1, pipeline.handle(listOf(scam), setOf(DetectorId.Link)).alerted)
        assertEquals(2, owner.shown.size)
    }

    @Test fun eachProblemHasTheFixThatMatchesHowItClears() {
        // Event findings never clear by themselves, so they must be acknowledgeable; setting findings clear when fixed.
        val eventProblems = Problem.entries.filter { it.fix == Fix.Acknowledge || it.fix == Fix.SettingsThenAcknowledge }
        assertEquals(setOf(Problem.ScamLink, Problem.LookalikeLink, Problem.SiteNotifications), eventProblems.toSet())
        assertTrue(Problem.entries.filter { it.severity >= com.aegida.core.model.Severity.NeedsTap }.none { it.fix == Fix.None })
        assertTrue(Problem.entries.filter { it.severity == com.aegida.core.model.Severity.Info }.all { it.fix == Fix.None })
    }
}
