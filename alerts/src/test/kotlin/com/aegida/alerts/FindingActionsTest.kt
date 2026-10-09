package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryRows
import com.aegida.core.alert.Outcome
import com.aegida.core.model.Baseline
import com.aegida.core.model.DetectorId
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Snapshot
import com.aegida.core.oem.Reading
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
    private val outbox = FakeOutbox()
    private val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    private val pipeline = AlertPipeline(findings, owner, history, guardians, NtfySender(http), outbox) { now }
    private val baselines = FakeBaselines()
    private var phone = Snapshot(now, PrivateDns.Automatic, "com.android.chrome", true, true, Reading.NotPresent, Reading.NotPresent)
    private val actions = FindingActions(findings, history, owner, baselines, { phone }, pipeline) { now }

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

    @Test fun sayingAChangeWasHersMovesTheBaselineAndClearsTheQuestion() = runTest {
        baselines.value = Baseline.of(phone)
        phone = phone.copy(browser = "org.other.browser")
        val changed = Finding(FindingKey(DetectorId.Drift, Problem.BrowserChanged, "org.other.browser"), emptyMap(), now, now)
        pipeline.handle(listOf(changed), setOf(DetectorId.Drift))

        assertTrue(actions.acceptChange(changed.key))

        assertEquals("org.other.browser", baselines.value!!.browser)
        assertEquals(FindingState.Acknowledged, findings.get(changed.key)!!.state)
        assertFalse(actions.acceptChange(changed.key)) // nothing open any more
    }

    @Test fun onlyAWasThatYouQuestionCanBeAcceptedIntoTheBaseline() = runTest {
        baselines.value = Baseline.of(phone)
        pipeline.handle(listOf(scam), setOf(DetectorId.Link))
        assertFalse(actions.acceptChange(scam.key))
        assertEquals(FindingState.Open, findings.get(scam.key)!!.state)
    }

    private suspend fun listenerAdded(): Finding {
        baselines.value = Baseline.of(phone)
        phone = phone.copy(notificationListeners = setOf("org.spy.app"))
        val f = Finding(FindingKey(DetectorId.Drift, Problem.ListenerAdded, "org.spy.app"), emptyMap(), now, now)
        pipeline.handle(listOf(f), setOf(DetectorId.Drift))
        http.posts.clear(); http.bodies.clear()
        return f
    }

    @Test fun whenSheSaysItWasHersTheGuardianWhoWasToldHearsThat() = runTest {
        val f = listenerAdded()
        assertTrue(actions.acceptChange(f.key))
        assertEquals(listOf("Ruth said the change Aegida asked about was hers. Nothing more to do."), http.bodies)
        assertEquals(listOf(Outcome.OwnerWarned, Outcome.GuardianTold, Outcome.Acknowledged, Outcome.GuardianTold), history.entries.map { it.outcome })
    }

    @Test fun aGuardianWhoWasNeverToldAboutItHearsNothingAboutHerAnswer() = runTest {
        baselines.value = Baseline.of(phone)
        phone = phone.copy(browser = "org.other.browser")
        val f = Finding(FindingKey(DetectorId.Drift, Problem.BrowserChanged, "org.other.browser"), emptyMap(), now, now)
        pipeline.handle(listOf(f), setOf(DetectorId.Drift))
        assertEquals(0, http.bodies.size)
        assertTrue(actions.acceptChange(f.key))
        assertEquals(0, http.bodies.size)
    }

    @Test fun withGuardianAlertsOffNothingIsSent() = runTest {
        val f = listenerAdded()
        guardians.value = guardians.value.copy(alertsOn = false)
        assertTrue(actions.acceptChange(f.key))
        assertEquals(0, http.bodies.size)
    }

    @Test fun aFollowUpThatCannotBeSentIsKeptForRetryAndShownInHistory() = runTest {
        val f = listenerAdded()
        http.result = com.aegida.core.net.HttpResult.Failed(null, com.aegida.core.net.FailureReason.Offline)
        assertTrue(actions.acceptChange(f.key))
        assertEquals(1, outbox.rows.size)
        assertEquals(Outcome.GuardianNotReached, history.entries.last().outcome)
    }

    @Test fun justSeeingAScamWarningSendsNoFollowUp() = runTest {
        pipeline.handle(listOf(scam), setOf(DetectorId.Link))
        http.posts.clear(); http.bodies.clear()
        actions.acknowledge(scam.key)
        assertEquals(0, http.bodies.size)
    }
}
