package com.aegida.detectors

import com.aegida.core.model.Baseline
import com.aegida.core.model.Snapshot
import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class DriftDetectorTest {
    private val atSetup: Snapshot = snapshot()
    private val baseline = Baseline.of(atSetup)

    private fun found(now: Snapshot, base: Baseline? = baseline) =
        DriftDetector().evaluate(inputs(snapshot = now).copy(baseline = base)).map { it.key.problem to it.key.subject }

    @Test fun aPhoneThatMatchesItsBaselineHasNoDrift() = assertEquals(emptyList<Pair<Problem, String>>(), found(atSetup))

    @Test fun beforeSetupFinishesThereIsNothingToCompareWith() = assertEquals(emptyList<Pair<Problem, String>>(), found(snapshot(browser = "org.evil.browser"), base = null))

    @Test fun anotherDefaultBrowserTextAppOrPhoneAppIsAChange() {
        assertEquals(listOf(Problem.BrowserChanged to "org.evil.browser"), found(snapshot(browser = "org.evil.browser")))
        assertEquals(listOf(Problem.SmsAppChanged to "org.evil.sms"), found(snapshot(smsApp = "org.evil.sms")))
        assertEquals(listOf(Problem.DialerChanged to "org.evil.dialer"), found(snapshot(dialerApp = "org.evil.dialer")))
        assertEquals(listOf(Problem.AssistantChanged to "org.evil.assistant"), found(snapshot(assistant = "org.evil.assistant")))
    }

    @Test fun aDefaultThatIsNowUnsetIsNotAChange() =
        assertEquals(emptyList<Pair<Problem, String>>(), found(snapshot(browser = null, smsApp = null, dialerApp = null)))

    @Test fun eachNewAppWithNotificationAccessScreenControlOrAdminRightsIsOneFinding() {
        val now = snapshot(
            notificationListeners = setOf("app.b", "app.a"),
            accessibilityApps = setOf("app.screen"),
            deviceAdmins = setOf("app.admin"),
        )
        assertEquals(
            listOf(
                Problem.ListenerAdded to "app.a", Problem.ListenerAdded to "app.b",
                Problem.AccessibilityAdded to "app.screen", Problem.AdminAdded to "app.admin",
            ),
            found(now),
        )
    }

    @Test fun anAppThatWasAlreadyThereAtSetupIsNotNew() {
        val base = Baseline.of(snapshot(notificationListeners = setOf("app.a"), accessibilityApps = setOf("app.screen")))
        assertEquals(emptyList<Pair<Problem, String>>(), found(snapshot(notificationListeners = setOf("app.a"), accessibilityApps = setOf("app.screen")), base))
    }

    @Test fun anAppThatLostItsAccessIsNotAProblem() {
        val base = Baseline.of(snapshot(accessibilityApps = setOf("app.screen")))
        assertEquals(emptyList<Pair<Problem, String>>(), found(snapshot(), base))
    }

    @Test fun sayingTheChangeWasHersMovesOnlyThatPartOfTheBaseline() {
        val now = snapshot(browser = "org.other.browser", smsApp = "org.other.sms")
        val accepted = baseline.accepting(Problem.BrowserChanged, "org.other.browser", now)
        assertEquals("org.other.browser", accepted.browser)
        assertEquals(baseline.smsApp, accepted.smsApp)
        assertEquals(listOf(Problem.SmsAppChanged to "org.other.sms"), found(now, accepted))
    }

    @Test fun eachKindOfChangeIsAcceptedOnItsOwn() {
        val everything = snapshot(
            browser = "b.new", smsApp = "s.new", dialerApp = "d.new", assistant = "as.new",
            notificationListeners = setOf("l.new"), accessibilityApps = setOf("a.new"), deviceAdmins = setOf("m.new"),
        )
        val expected = mapOf(
            Problem.BrowserChanged to baseline.copy(browser = "b.new"),
            Problem.SmsAppChanged to baseline.copy(smsApp = "s.new"),
            Problem.DialerChanged to baseline.copy(dialerApp = "d.new"),
            Problem.AssistantChanged to baseline.copy(assistant = "as.new"),
            Problem.ListenerAdded to baseline.copy(notificationListeners = setOf("l.new")),
            Problem.AccessibilityAdded to baseline.copy(accessibilityApps = setOf("a.new")),
            Problem.AdminAdded to baseline.copy(deviceAdmins = setOf("m.new")),
        )
        val subjects = mapOf(
            Problem.BrowserChanged to "b.new", Problem.SmsAppChanged to "s.new", Problem.DialerChanged to "d.new", Problem.AssistantChanged to "as.new",
            Problem.ListenerAdded to "l.new", Problem.AccessibilityAdded to "a.new", Problem.AdminAdded to "m.new",
        )
        expected.forEach { (problem, moved) -> assertEquals(problem.name, moved, baseline.accepting(problem, subjects.getValue(problem), everything)) }
        assertEquals(baseline, baseline.accepting(Problem.ScamLink, "x", everything))
    }

    @Test fun sayingOneAppWasHersLeavesAnotherNewOneFlaggedAndAWrongSubjectChangesNothing() { // findings #50
        val now = snapshot(
            notificationListeners = setOf("app.good", "app.spy"),
            accessibilityApps = setOf("screen.good", "screen.spy"),
            browser = "b.new",
        )
        val accepted = baseline.accepting(Problem.ListenerAdded, "app.good", now).accepting(Problem.AccessibilityAdded, "screen.good", now)
        assertEquals(setOf("app.good"), accepted.notificationListeners)
        assertEquals(setOf("screen.good"), accepted.accessibilityApps)
        assertEquals(
            listOf(Problem.BrowserChanged to "b.new", Problem.ListenerAdded to "app.spy", Problem.AccessibilityAdded to "screen.spy"),
            found(now, accepted),
        )
        assertEquals(baseline, baseline.accepting(Problem.ListenerAdded, "app.not.on.the.phone", now))
        assertEquals(baseline, baseline.accepting(Problem.AccessibilityAdded, "screen.not.on.the.phone", now))
        assertEquals(baseline, baseline.accepting(Problem.AdminAdded, "admin.not.on.the.phone", now))
        assertEquals(baseline, baseline.accepting(Problem.SmsAppChanged, "sms.not.on.the.phone", now))
        assertEquals(baseline, baseline.accepting(Problem.BrowserChanged, "someone.else", now))
    }
}
