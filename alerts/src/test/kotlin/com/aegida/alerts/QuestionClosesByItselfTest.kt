package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.HistoryRows
import com.aegida.core.alert.Outcome
import com.aegida.core.model.Baseline
import com.aegida.core.model.DetectorId
import com.aegida.core.model.FindingState
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Setup
import com.aegida.core.model.Snapshot
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import com.aegida.core.oem.Reading
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import com.aegida.detectors.Inputs
import com.aegida.detectors.NewAppDetector
import com.aegida.testkit.FakeBaselines
import com.aegida.testkit.FakeOutbox
import com.aegida.testkit.GALAXY_STORE
import com.aegida.testkit.aGalaxyStoreApp
import com.aegida.testkit.anAppOpenedFromAFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * A new-app question that closes by itself while something else is in flight (scenarios GS14-GS16, HC15 in
 * docs/scenarios/phone-changes.md): the guardian note still waiting, a reminder already sent, her Yes at the same moment, the process
 * killed half way. The question is made by the real [NewAppDetector] over a phone read before and after the update that trusts the
 * Galaxy Store: before it, a Galaxy Store app was a question; after it, it is not.
 */
class QuestionClosesByItselfTest {
    private val now = Instant.now()
    private var clockAt = now
    private val topic = NtfyTopic("aegida_" + "g5T6y7U8".repeat(4))
    private val findings = Findings()
    private val owner = Owner()
    private val history = History()
    private val http = Http()
    private val outbox = FakeOutbox()
    private val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    private val pipeline = pipelineWith(history)
    private val flusher = OutboxFlusher(outbox, NtfySender(http), history, guardians) { clockAt }
    private val offline = HttpResult.Failed(null, FailureReason.Offline)

    private fun pipelineWith(log: HistoryLog) = AlertPipeline(findings, owner, log, GuardianLine(guardians, NtfySender(http), outbox)) { clockAt }

    private val atSetup = Snapshot(now, PrivateDns.Automatic, null, true, true, Reading.NotPresent, Reading.NotPresent)
    private val baselines = FakeBaselines(Baseline.of(atSetup))
    private val temu = aGalaxyStoreApp("org.new.temu")

    /** The phone before the update: Aegida did not yet trust the Galaxy Store, so Temu is a question. */
    private val beforeUpdate = atSetup.copy(apps = listOf(temu))

    /** The same phone after the update: the Galaxy Store is a system app in the list, so Temu comes from a store Aegida trusts. */
    private val afterUpdate = beforeUpdate.copy(makerStores = setOf(GALAXY_STORE))

    private fun check(phone: Snapshot) = NewAppDetector().evaluate(Inputs(phone, clockAt, Setup.NotDone, null, null, null, baseline = baselines.value))

    private suspend fun runCheck(phone: Snapshot) = pipeline.handle(check(phone), setOf(DetectorId.NewApp), phone = phone)

    /** The key of the question, as the detector made it before the update. */
    private val questionKey = check(beforeUpdate).single().key

    @Test fun aGuardianNoteWaitingOfflineIsStillDeliveredAfterTheQuestionClosedByItself() = runTest { // GS14
        http.result = offline
        runCheck(beforeUpdate)
        val waiting = outbox.rows.single()
        assertEquals(Problem.NewAppOutsidePlay, waiting.problem)

        clockAt = now.plus(Duration.ofHours(1)) // the update arrives while she is still offline
        runCheck(afterUpdate)
        assertEquals(FindingState.Resolved, findings.rows[questionKey]!!.state)
        assertEquals(Outcome.NoLongerAConcern, history.entries.last().outcome)

        clockAt = now.plus(Duration.ofHours(2)) // back online: the outbox does not know the question closed, so the note goes (recorded, not changed)
        http.result = HttpResult.Ok(200, "{}")
        assertEquals(1, flusher.flush())
        assertEquals(waiting.body, http.bodies.last())
        assertTrue(outbox.rows.isEmpty())
        assertEquals(Outcome.GuardianTold, history.entries.last().outcome)
    }

    @Test fun anEscalatedQuestionThatClosesByItselfIsNeverToldToTheGuardianAsClosed() = runTest { // GS15
        runCheck(beforeUpdate)
        clockAt = now.plus(Duration.ofHours(25))
        runCheck(beforeUpdate) // a day later, still not dealt with: the guardian is reminded
        assertEquals(FindingState.Escalated, findings.rows[questionKey]!!.state)
        assertEquals(Texts.Guardian.STILL_OPEN.format("Ruth"), http.bodies.last())
        val sentBefore = http.bodies.toList()

        clockAt = now.plus(Duration.ofHours(26))
        runCheck(afterUpdate)
        assertEquals(FindingState.Resolved, findings.rows[questionKey]!!.state)
        assertTrue("her banner goes", questionKey in owner.cleared)
        assertEquals("the guardian hears nothing about the close", sentBefore, http.bodies)
        assertTrue(outbox.rows.isEmpty())
    }

    private val gate = AlertGate()
    private val actions = run {
        val seen = SeenMarker(findings, history, owner)
        val allowing = AppAllowing(findings, history, pipeline, seen) { clockAt }
        FindingActions(seen, allowing, ChangeAcceptor(findings, baselines, { afterUpdate }, pipeline, seen) { clockAt }, gate) { clockAt }
    }

    @Test fun whenTheQuestionClosesJustBeforeHerYesTheGuardianGetsNoSheSaidSoMessage() = runTest { // GS16
        runCheck(beforeUpdate)
        val toldOfTheQuestion = http.bodies.toList()
        clockAt = now.plus(Duration.ofMinutes(5))
        gate.run { runCheck(afterUpdate) } // the check holds the one alert lock; her tap waits for it

        assertFalse("nothing is open to say Yes to: the page closes", actions.acceptChange(questionKey))
        assertEquals(toldOfTheQuestion, http.bodies)
        assertTrue(http.bodies.none { it == Texts.Guardian.CONFIRMED.format("Ruth") })
        assertEquals(listOf(Outcome.NoLongerAConcern), history.entries.filter { it.at == clockAt }.map { it.outcome })
    }

    @Test fun whenHerYesComesJustBeforeTheCheckItIsAnOrdinaryYesAndTheQuestionIsNotClosedAgain() = runTest { // GS16, the other order
        runCheck(beforeUpdate)
        clockAt = now.plus(Duration.ofMinutes(5))
        assertTrue(actions.acceptChange(questionKey))
        assertEquals(Texts.Guardian.CONFIRMED.format("Ruth"), http.bodies.last())
        val sent = http.bodies.toList()

        gate.run { runCheck(afterUpdate) }
        assertEquals(FindingState.Acknowledged, findings.rows[questionKey]!!.state)
        assertEquals(sent, http.bodies)
        assertTrue(history.entries.none { it.outcome == Outcome.NoLongerAConcern })
    }

    /** Like a process killed at the moment the closing line is written: the add never happens. */
    private class KilledAtTheClosingLine(private val real: History) : HistoryLog by real {
        override suspend fun add(entry: HistoryEntry) {
            if (entry.outcome == Outcome.Fixed || entry.outcome == Outcome.NoLongerAConcern) error("process killed")
            real.add(entry)
        }
    }

    @Test fun killedBetweenClosingTheQuestionAndWritingItsLineLeavesTheRowAmber() = runTest { // HC15
        runCheck(beforeUpdate)
        clockAt = now.plus(Duration.ofHours(1))
        val killed = runCatching { pipelineWith(KilledAtTheClosingLine(history)).handle(check(atSetup), setOf(DetectorId.NewApp), phone = atSetup) }
        assertTrue(killed.isFailure)

        clockAt = now.plus(Duration.ofHours(2)) // the next check, after Android started the process again
        runCheck(atSetup)
        assertTrue("no closing line", history.entries.none { it.outcome == Outcome.Fixed || it.outcome == Outcome.NoLongerAConcern })
        val question = HistoryRows.of(history.entries).single { it.at == now }
        assertFalse("the question's row stays amber (fail closed)", question.handled)
        assertTrue(findings.open().isEmpty())
    }
}
