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
        val accepted = baseline.accepting(Problem.BrowserChanged, now)
        assertEquals("org.other.browser", accepted.browser)
        assertEquals(baseline.smsApp, accepted.smsApp)
        assertEquals(listOf(Problem.SmsAppChanged to "org.other.sms"), found(now, accepted))
    }

    @Test fun eachKindOfChangeIsAcceptedOnItsOwn() {
        val everything = snapshot(
            browser = "b.new", smsApp = "s.new", dialerApp = "d.new",
            notificationListeners = setOf("l.new"), accessibilityApps = setOf("a.new"), deviceAdmins = setOf("m.new"),
        )
        val expected = mapOf(
            Problem.BrowserChanged to baseline.copy(browser = "b.new"),
            Problem.SmsAppChanged to baseline.copy(smsApp = "s.new"),
            Problem.DialerChanged to baseline.copy(dialerApp = "d.new"),
            Problem.ListenerAdded to baseline.copy(notificationListeners = setOf("l.new")),
            Problem.AccessibilityAdded to baseline.copy(accessibilityApps = setOf("a.new")),
            Problem.AdminAdded to baseline.copy(deviceAdmins = setOf("m.new")),
        )
        expected.forEach { (problem, moved) -> assertEquals(problem.name, moved, baseline.accepting(problem, everything)) }
        assertEquals(baseline, baseline.accepting(Problem.ScamLink, everything))
    }
}
