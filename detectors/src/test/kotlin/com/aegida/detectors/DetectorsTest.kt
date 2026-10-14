package com.aegida.detectors

import com.aegida.core.model.Aegida
import com.aegida.core.model.Baseline
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

    @Test fun theFindingNamesTheAppTheMessageCameFromWhenThePhoneListsIt() { // Kevin, Oct 10: "no idea what it is talking about"
        val apps = listOf(com.aegida.core.model.InstalledApp("com.android.shell", "Messages", "com.android.vending", true))
        val finding = detector.evaluate(inputs(snapshot = snapshot().copy(apps = apps), event = message("Refund: paypa1-help.example/claim"))).single()
        assertEquals("Messages", finding.evidence[Evidence.APP_LABEL])
        assertEquals(null, detector.evaluate(inputs(event = message("Refund: paypa1-help.example/claim"))).single().evidence[Evidence.APP_LABEL])
    }

    /** Kevin, Oct 10: the owner needs to know what it was, so the sender, the words and the time stay on the phone with the finding. */
    @Test fun evidenceKeepsWhoSentItWhatItSaidAndWhenForTheOwnerToSee() {
        val event = message("https://evil.example/pay", title = "Chase Bank")
        val finding = detector.evaluate(inputs(event = event)).single()
        assertEquals(setOf(Evidence.NOTIFICATION_KEY, Evidence.PACKAGE, Evidence.HOST, Evidence.MESSAGE_TITLE, Evidence.MESSAGE_TEXT, Evidence.MESSAGE_AT), finding.evidence.keys)
        assertEquals("Chase Bank", finding.evidence[Evidence.MESSAGE_TITLE])
        assertEquals("https://evil.example/pay", finding.evidence[Evidence.MESSAGE_TEXT])
        assertEquals(event.postedAt.toEpochMilli().toString(), finding.evidence[Evidence.MESSAGE_AT])
    }

    @Test fun aVeryLongMessageIsCutShortBeforeItIsKept() {
        val finding = detector.evaluate(inputs(event = message("https://evil.example/pay " + "word ".repeat(500)))).single()
        assertEquals(true, finding.evidence.getValue(Evidence.MESSAGE_TEXT).length <= 200)
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

    @Test fun theFindingKeepsWhatTheWebsiteSaidAndWhenForHistory() { // Kevin, Oct 10
        val finding = detector.evaluate(inputs(event = chromeSiteNotification)).single()
        assertEquals(chromeSiteNotification.title, finding.evidence[Evidence.MESSAGE_TITLE])
        assertEquals(chromeSiteNotification.postedAt.toEpochMilli().toString(), finding.evidence[Evidence.MESSAGE_AT])
    }

    @Test fun aSiteFromTheBaselineIsLeftAloneUnlessItTurnsScary() {
        val setup = Setup(null, setOf("http://localhost:8765"))
        val calm = chromeSiteNotification.copy(title = "New recipe posted", text = "Lemon cake")
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(setup = setup, event = calm)))
        assertEquals(1, detector.evaluate(inputs(setup = setup, event = chromeSiteNotification)).size)
    }

    // Kevin, Oct 9: sites she already had when setup ended are left alone; a new one is asked about, not simply blocked.
    private val site = "http://localhost:8765"
    private val calm = chromeSiteNotification.copy(title = "New recipe posted", text = "Lemon cake")
    private fun baselineWith(websites: Set<String>?) = Baseline(
        browser = null, smsApp = null, dialerApp = null, notificationListeners = emptySet(), accessibilityApps = emptySet(),
        deviceAdmins = emptySet(), websites = websites,
    )

    @Test fun aSiteThatAppearedAfterSetupIsAskedAboutAndNotSimplyBlocked() {
        val finding = detector.evaluate(inputs(event = calm, baseline = baselineWith(setOf("https://news.example")))).single()
        assertEquals(Problem.NewWebsiteNotifications, finding.key.problem)
        assertEquals(site, finding.key.subject)
        assertEquals("com.android.chrome", finding.evidence[Evidence.PACKAGE])
        assertEquals(calm.channelId, finding.evidence[Evidence.CHANNEL_ID])
    }

    @Test fun aSiteSheAlreadyHadWhenSetupEndedIsLeftAloneUnlessItTurnsScary() {
        val known = baselineWith(setOf(site))
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(event = calm, baseline = known)))
        assertEquals(Problem.SiteNotifications, detector.evaluate(inputs(event = chromeSiteNotification, baseline = known)).single().key.problem)
    }

    @Test fun aNewSiteWithFakeVirusWordsIsCalledFakeNotAskedAbout() {
        // "Was that you?" would offer to allow a fake virus warning; the plain "these are not real" message fits.
        val finding = detector.evaluate(inputs(event = chromeSiteNotification, baseline = baselineWith(emptySet()))).single()
        assertEquals(Problem.SiteNotifications, finding.key.problem)
    }

    @Test fun beforeTheListOfSitesIsRecordedItBehavesAsBefore() {
        assertEquals(Problem.SiteNotifications, detector.evaluate(inputs(event = calm)).single().key.problem)
        assertEquals(Problem.SiteNotifications, detector.evaluate(inputs(event = calm, baseline = baselineWith(null))).single().key.problem)
    }

    // Scenario rows WP11, WP15, WP16 (docs/scenarios/website-popups.md); findings #109.
    private val headline = chromeSiteNotification.copy(title = "Retailer hacked", text = "Millions of customer records stolen in data breach")

    @Test fun aNewsHeadlineFromASiteSheSaidYesToIsLeftInPlace() {
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(event = headline, baseline = baselineWith(setOf(site)))))
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(event = headline, setup = Setup(null, setOf(site)))))
        val malware = headline.copy(title = "New Android malware found in 40 apps", text = "Researchers say the virus spreads by text")
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(event = malware, baseline = baselineWith(setOf(site)))))
    }

    @Test fun aSiteSheSaidYesToThatWarnsAboutHerPhoneIsStillHidden() {
        val known = baselineWith(setOf(site))
        for (scare in listOf(
            chromeSiteNotification,
            headline.copy(title = "Your device has been hacked", text = "Tap to fix"),
            headline.copy(title = "Security warning", text = "Battery damaged by virus. Clean now"),
            headline.copy(title = "Alert", text = "Trojan detected on this phone"),
            headline.copy(title = "Warning", text = "You are infected"),
        )) {
            assertEquals(scare.title, Problem.SiteNotifications, detector.evaluate(inputs(event = scare, baseline = known)).single().key.problem)
            assertEquals(scare.title, Problem.SiteNotifications, detector.evaluate(inputs(event = scare, setup = Setup(null, setOf(site)))).single().key.problem)
        }
    }

    @Test fun aNewSitePostingAScareHeadlineIsStillCalledFake() {
        assertEquals(Problem.SiteNotifications, detector.evaluate(inputs(event = headline, baseline = baselineWith(setOf("https://news.example")))).single().key.problem)
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
