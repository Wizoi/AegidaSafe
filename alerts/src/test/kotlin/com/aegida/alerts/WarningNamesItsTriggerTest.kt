package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryDetail
import com.aegida.core.alert.Outcome
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.Severity
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import com.aegida.testkit.FakeOutbox
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * #164 step 2 (docs/scenarios/warning-names-its-trigger.md): the owner's new "what it was" lines never reach the guardian (WN19), and a
 * warning the guardian was told about before the update still gets its all-clear when she says "Yes, that was me" after it (WN24, Kevin).
 */
class WarningNamesItsTriggerTest {
    private val now = Instant.now()
    private val topic = NtfyTopic.generate("Ruth", java.util.Random(3))

    private class Phone(now: Instant, topic: NtfyTopic) {
        val history = History()
        val http = Http()
        val findings = Findings()
        val pipeline = AlertPipeline(
            findings, Owner(), history,
            GuardianLine(FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic)), NtfySender(http), FakeOutbox()),
        ) { now }
    }

    private val facts = mapOf(Evidence.APP_SOURCE to "outside", Evidence.APP_ADDED to now.toEpochMilli().toString())

    private fun finding(problem: Problem, evidence: Map<String, String>, subject: String = "org.helper") =
        Finding(FindingKey(DetectorId.Drift, problem, subject), evidence, now, now)

    /** WN19: for every kind, what the guardian receives is word for word the same with or without the new facts, and holds none of her lines. */
    @Test fun theGuardianGetsTheSameWordsForEveryKindAndNoneOfTheOwnersNewLines() = runTest {
        val base = mapOf(
            Evidence.APP_LABEL to "Tap Yes Helper", Evidence.SITE to "https://news.example", Evidence.HOST to "evil.example",
            Evidence.MESSAGE_TITLE to "Chase Bank",
        )
        Problem.entries.forEach { problem ->
            val before = Phone(now, topic).also { it.pipeline.handle(listOf(finding(problem, base)), DetectorId.entries.toSet()) }
            val after = Phone(now, topic).also {
                it.pipeline.handle(listOf(finding(problem, base + facts + (Evidence.PACKAGE to "org.helper"))), DetectorId.entries.toSet())
            }
            assertEquals(problem.name, before.http.bodies, after.http.bodies)
            val ownerLines = Texts.Home.whatItIs(problem, base + facts, now, ZoneOffset.UTC, Locale.US)
            after.http.bodies.forEach { body ->
                ownerLines.forEach { line -> assertFalse("${problem.name}: $body", body.contains(line)) }
                if (problem.severity == Severity.Urgent) assertFalse("${problem.name}: $body", body.contains("Helper"))
            }
        }
    }

    /** WN24: an urgent warning told before the update has a told-line with no words (the name was hidden then); her Yes still reaches them. */
    @Test fun anUrgentWarningToldBeforeTheUpdateStillGetsItsAllClearAfterHerYes() = runTest {
        val phone = Phone(now, topic)
        val listener = finding(Problem.ListenerAdded, mapOf(Evidence.APP_LABEL to "Pixel Watch", Evidence.PACKAGE to "org.helper") + facts)
        phone.pipeline.handle(listOf(listener), setOf(DetectorId.Drift))
        assertEquals(1, phone.http.bodies.size)
        // What 0.4.1 wrote for this warning: no words at all.
        phone.history.entries.replaceAll { it.copy(detail = null) }
        phone.http.bodies.clear()
        phone.pipeline.tellConfirmed(listener)
        assertEquals(listOf(Texts.Guardian.CONFIRMED_URGENT.format("Ruth")), phone.http.bodies)
    }

    /** WN24: a default-app question told before the update kept the old words ("The app is called: “Firefox”"); it still matches. */
    @Test fun aQuestionToldBeforeTheUpdateWithTheOldWordsStillGetsItsAllClear() = runTest {
        val phone = Phone(now, topic)
        val browser = finding(Problem.BrowserChanged, mapOf(Evidence.APP_LABEL to "Firefox", Evidence.PACKAGE to "org.helper") + facts)
        phone.pipeline.handle(listOf(browser), setOf(DetectorId.Drift))
        val old = HistoryDetail.of(Problem.BrowserChanged, mapOf(Evidence.APP_LABEL to "Firefox", Evidence.PACKAGE to "org.helper"))
        phone.history.entries.replaceAll { it.copy(detail = old) }
        phone.http.bodies.clear()
        phone.pipeline.tellConfirmed(browser)
        assertEquals(listOf(Texts.Guardian.CONFIRMED.format("Ruth")), phone.http.bodies)
    }

    /** WN23: the app was removed before she looked; the closing line names it as the warning did, so History's row turns green. */
    @Test fun anAppRemovedBeforeSheLookedClosesWithALineThatNamesIt() = runTest {
        val phone = Phone(now, topic)
        val listener = finding(Problem.ListenerAdded, mapOf(Evidence.APP_LABEL to "Pixel Watch", Evidence.PACKAGE to "org.helper") + facts)
        phone.pipeline.handle(listOf(listener), setOf(DetectorId.Drift))
        phone.pipeline.handle(emptyList(), setOf(DetectorId.Drift))
        val warned = phone.history.entries.first { it.outcome == Outcome.OwnerWarned }
        val closed = phone.history.entries.last()
        assertFalse(closed.outcome == Outcome.OwnerWarned || closed.outcome == Outcome.GuardianTold)
        assertEquals("The app calls itself: “Pixel Watch”. It came from outside the Play Store.", HistoryDetail.display(closed.detail))
        assertEquals(true, HistoryDetail.same(warned.detail, closed.detail))
    }

    /**
     * WN35 (must never, #166): two apps with no name Aegida can read became the browser; the guardian heard about A, and B came the same
     * day so the repeat back-off held it. Her Yes to A sends its all-clear; that all-clear's own History line must not count as "told
     * about B", so her Yes to B sends nothing.
     */
    @Test fun aYesToAnAppTheGuardianHeardAboutThenToOneHeldBackSendsOnlyOneAllClear() = runTest {
        val phone = Phone(now, topic)
        val a = finding(Problem.BrowserChanged, mapOf(Evidence.PACKAGE to "org.a") + facts, "org.a")
        val b = finding(Problem.BrowserChanged, mapOf(Evidence.PACKAGE to "org.b") + facts, "org.b")
        phone.pipeline.handle(listOf(a), setOf(DetectorId.Drift))
        phone.pipeline.handle(listOf(a, b), setOf(DetectorId.Drift))
        assertEquals(listOf(Outcome.OwnerWarned, Outcome.GuardianTold, Outcome.OwnerWarned), phone.history.entries.map { it.outcome })
        phone.http.bodies.clear()
        phone.pipeline.tellConfirmed(a)
        phone.pipeline.tellConfirmed(b)
        assertEquals(listOf(Texts.Guardian.CONFIRMED.format("Ruth")), phone.http.bodies)
    }

    /** WN21 (must never): two apps of one kind; the guardian was told about A only, so her Yes to B sends nothing. */
    @Test fun aYesToAnotherAppOfTheSameKindSendsNoAllClear() = runTest {
        val phone = Phone(now, topic)
        val a = finding(Problem.ListenerAdded, mapOf(Evidence.APP_LABEL to "Alpha", Evidence.PACKAGE to "org.a") + facts, "org.a")
        phone.pipeline.handle(listOf(a), setOf(DetectorId.Drift))
        assertEquals(listOf(Outcome.OwnerWarned, Outcome.GuardianTold), phone.history.entries.map { it.outcome })
        phone.http.bodies.clear()
        val b = finding(Problem.ListenerAdded, mapOf(Evidence.APP_LABEL to "Alpha", Evidence.PACKAGE to "org.b") + facts, "org.b")
        phone.pipeline.tellConfirmed(b)
        assertEquals(emptyList<String>(), phone.http.bodies)
    }
}
