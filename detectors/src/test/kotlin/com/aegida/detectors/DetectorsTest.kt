package com.aegida.detectors

import com.aegida.core.model.Aegida
import com.aegida.core.model.Evidence
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Setup
import com.aegida.core.model.SetupStep
import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.chromeSiteNotification
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.message
import com.aegida.detectors.Fixtures.snapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class LinkDetectorTest {
    private val detector = LinkDetector()
    private fun problems(text: String, packageName: String = "com.android.shell") =
        detector.evaluate(inputs(event = message(text).copy(packageName = packageName))).map { it.key.problem to it.key.subject }

    @Test fun aListedLinkIsAScamLink() {
        assertEquals(listOf(Problem.ScamLink to "login.evil.example"), problems("Locked! Verify at https://login.evil.example/x"))
    }

    @Test fun aLookalikeIsFlaggedEvenWhenNotListed() {
        assertEquals(listOf(Problem.LookalikeLink to "paypa1-help.example"), problems("Refund: paypa1-help.example/claim"))
    }

    @Test fun plainAndShortenedLinksMakeNoFinding() {
        assertEquals(emptyList<Any>(), problems("Lunch at https://www.example.org/menu or bit.ly/abc?"))
    }

    @Test fun ownAndSiteNotificationsAreNotLinkChecked() {
        assertEquals(emptyList<Any>(), problems("https://evil.example/", packageName = Aegida.PACKAGE))
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(event = chromeSiteNotification.copy(text = "evil.example"))))
    }

    @Test fun evidenceNamesTheNotificationButNotTheMessage() {
        val finding = detector.evaluate(inputs(event = message("https://evil.example/pay"))).single()
        assertEquals(setOf(Evidence.NOTIFICATION_KEY, Evidence.PACKAGE, Evidence.HOST), finding.evidence.keys)
    }
}

class PopupSpamDetectorTest {
    private val detector = PopupSpamDetector()

    @Test fun theRealChromeSiteNotificationIsCaught() {
        val finding = detector.evaluate(inputs(event = chromeSiteNotification)).single()
        assertEquals(Problem.SiteNotifications, finding.key.problem)
        assertEquals("http://localhost:8765", finding.key.subject)
        assertEquals("web:http://localhost:8765;13435952006713133", finding.evidence[Evidence.CHANNEL_ID])
        assertEquals("com.android.chrome", finding.evidence[Evidence.PACKAGE])
    }

    @Test fun aSiteFromTheBaselineIsLeftAloneUnlessItTurnsScary() {
        val setup = Setup(null, setOf("http://localhost:8765"))
        val calm = chromeSiteNotification.copy(title = "New recipe posted", text = "Lemon cake")
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(setup = setup, event = calm)))
        assertEquals(1, detector.evaluate(inputs(setup = setup, event = chromeSiteNotification)).size)
    }

    @Test fun ordinaryAppNotificationsAreIgnored() {
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(event = message("VIRUS DETECTED"))))
    }
}

class DnsDetectorTest {
    private val detector = DnsDetector()
    private val chosen = Setup("security.cloudflare-dns.com", emptySet())
    private fun problems(dns: PrivateDns, setup: Setup = chosen) = detector.evaluate(inputs(snapshot(privateDns = dns), setup)).map { it.key.problem }

    @Test fun nothingToSayBeforeSetup() = assertEquals(emptyList<Problem>(), problems(PrivateDns.Off, Setup.NotDone))

    @Test fun theChosenFilterIsFine() = assertEquals(emptyList<Problem>(), problems(PrivateDns.Hostname("security.cloudflare-dns.com")))

    @Test fun offAutomaticOrAnotherHostIsOff() {
        assertEquals(listOf(Problem.WebsiteFilterOff), problems(PrivateDns.Off))
        assertEquals(listOf(Problem.WebsiteFilterOff), problems(PrivateDns.fromSettings(null, null))) // key absent (findings #14)
        assertEquals(listOf(Problem.WebsiteFilterOff), problems(PrivateDns.Hostname("dns.example")))
    }
}

class HealthDetectorTest {
    private val detector = HealthDetector()
    private fun problems(input: Inputs) = detector.evaluate(input).map { it.key.problem }.toSet()

    @Test fun allWellSaysNothing() = assertEquals(emptySet<Problem>(), problems(inputs()))

    private val finished = Setup.NotDone.copy(stepsDone = SetupStep.entries.size)

    @Test fun lostPermissionsAreFindings() {
        assertEquals(setOf(Problem.ListenerOff), problems(inputs(snapshot(listenerEnabled = false), setup = finished)))
        assertEquals(setOf(Problem.NotificationsBlocked), problems(inputs(snapshot(canPostNotifications = false), setup = finished)))
    }

    /**
     * Before setup is finished she has not been asked yet, or is half way through the step that asks. Raising "Aegida can no
     * longer see your notifications" then is untrue and alarming (findings #65). If she skips the step, it is raised the
     * moment setup finishes, and her guardian is told then.
     */
    @Test fun lostPermissionsAreNotRaisedWhileSetupIsStillBeingDone() {
        val notDone = Setup.NotDone.copy(stepsDone = SetupStep.Access.ordinal)
        assertEquals(emptySet<Problem>(), problems(inputs(snapshot(listenerEnabled = false, canPostNotifications = false), setup = notDone)))
        assertEquals(emptySet<Problem>(), problems(inputs(snapshot(listenerEnabled = false), setup = Setup.NotDone)))
    }

    @Test fun aMissedDailyCheckIsNoticedAfter26Hours() {
        val late = inputs(lastDailyCheck = inputs().lastDailyCheck!!.copy(startedAt = Fixtures.now.minus(Duration.ofHours(27))))
        assertEquals(setOf(Problem.MissedCheck), problems(late))
        assertEquals(emptySet<Problem>(), problems(inputs(lastDailyCheck = null))) // first day: nothing missed yet
    }

    @Test fun oldOrMissingListsAreNoticed() {
        assertEquals(setOf(Problem.ListsOld), problems(inputs(feed = null)))
        assertEquals(setOf(Problem.ListsOld), problems(inputs(feed = Fixtures.feed(age = Duration.ofDays(4)))))
    }
}
