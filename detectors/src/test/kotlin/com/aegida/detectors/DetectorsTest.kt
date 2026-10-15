package com.aegida.detectors

import com.aegida.core.model.Aegida
import com.aegida.core.model.Baseline
import com.aegida.core.model.Evidence
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Setup
import com.aegida.core.model.SetupStep
import com.aegida.core.text.Fix
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
        val expected = setOf(Evidence.NOTIFICATION_KEY, Evidence.PACKAGE, Evidence.HOST, Evidence.MESSAGE_TITLE, Evidence.MESSAGE_TEXT, Evidence.MESSAGE_AT)
        assertEquals(expected, finding.evidence.keys)
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
        val finding = detector.evaluate(inputs(event = calm).copy(baseline = baselineWith(setOf("https://news.example")))).single()
        assertEquals(Problem.NewWebsiteNotifications, finding.key.problem)
        assertEquals(site, finding.key.subject)
        assertEquals("com.android.chrome", finding.evidence[Evidence.PACKAGE])
        assertEquals(calm.channelId, finding.evidence[Evidence.CHANNEL_ID])
    }

    @Test fun aSiteSheAlreadyHadWhenSetupEndedIsLeftAloneUnlessItTurnsScary() {
        val known = baselineWith(setOf(site))
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(event = calm).copy(baseline = known)))
        assertEquals(Problem.SiteScaryNews, detector.evaluate(inputs(event = chromeSiteNotification).copy(baseline = known)).single().key.problem)
    }

    @Test fun aNewSiteWithFakeVirusWordsIsCalledFakeNotAskedAbout() {
        // "Was that you?" would offer to allow a fake virus warning; the plain "these are not real" message fits.
        val finding = detector.evaluate(inputs(event = chromeSiteNotification).copy(baseline = baselineWith(emptySet()))).single()
        assertEquals(Problem.SiteNotifications, finding.key.problem)
    }

    @Test fun beforeTheListOfSitesIsRecordedItBehavesAsBefore() {
        assertEquals(Problem.SiteNotifications, detector.evaluate(inputs(event = calm)).single().key.problem)
        assertEquals(Problem.SiteNotifications, detector.evaluate(inputs(event = calm).copy(baseline = baselineWith(null))).single().key.problem)
    }

    // Scenario table WP17 (docs/scenarios/website-popups.md; Kevin, Oct 15, findings #148): for a site she allowed, alarming words
    // are hidden and only she is told; a site she did not allow is unchanged.
    private val headline = chromeSiteNotification.copy(title = "Retailer hacked", text = "Millions of customer records stolen in data breach")

    /** What a site she allowed gets for these words: once with the site in the recorded list, once allowed in setup before it. */
    private fun fromAnAllowedSite(title: String, text: String = ""): List<Problem?> {
        val event = headline.copy(title = title, text = text)
        return listOf(
            detector.evaluate(inputs(event = event).copy(baseline = baselineWith(setOf(site)))).singleOrNull()?.key?.problem,
            detector.evaluate(inputs(event = event, setup = Setup(null, setOf(site)))).singleOrNull()?.key?.problem,
        )
    }

    /** WP17 A1, A5, WP11, WP15: news or fake, alarming words from a site she allowed are hidden, and she is told in plain words. */
    @Test fun anAlarmingNoticeFromASiteSheAllowedIsHiddenAndOnlySheIsTold() {
        for ((title, text) in listOf(
            "Retailer hacked" to "Millions of customer records stolen in data breach",
            "Your phone is infected" to "Tap here",
            "Is your phone slow? It may have a virus" to "",
            "How to tell if your phone is hacked" to "Five signs to look for",
        )) {
            assertEquals(title, listOf(Problem.SiteScaryNews, Problem.SiteScaryNews), fromAnAllowedSite(title, text))
        }
        val told = Problem.SiteScaryNews
        assertEquals("A website you allowed sent an alarming message. Aegida hid it. Tap to stop that website.", told.forMom)
        assertEquals("A website you allowed sent an alarming message", told.whatHappened)
        assertEquals("hidden from the shade", true, told.hidesNotification)
        assertEquals("the tap opens that site's switch", Fix.SettingsThenAcknowledge, told.fix)
        assertEquals("a news site sending every day is told once a week", Duration.ofDays(7), told.quietAfterSeen)
    }

    /** WP17 M2: a bank or news site she allowed is never reported to the guardian, whatever its words. */
    @Test fun aSiteSheAllowedIsNeverReportedToTheGuardianWhateverItSays() {
        for ((title, text) in listOf(
            "Your bank was hacked: what to do next" to "Experts explain",
            "3 viruses found! Remove now" to "Tap here",
            "Your device has been hacked" to "Tap to fix",
            "Security warning" to "Battery damaged by virus. Clean now",
            "Virus detected! Your phone is at risk" to "",
            "VIRUS DETECTED! Your phone is infected" to "Tap now to clean your device",
        )) {
            for (problem in fromAnAllowedSite(title, text)) {
                assertEquals(title, Problem.SiteScaryNews, problem)
                assertEquals(title, null, problem?.forGuardian("Mom", host = site))
            }
        }
        assertEquals("no guardian sentence, so no reminder either", null, Problem.SiteScaryNews.forGuardian("Mom", host = site))
    }

    /** WP17 A3: calm words, or words that miss the scare-word gate, from a site she allowed are left where they are. */
    @Test fun aCalmNoticeFromASiteSheAllowedIsLeftAlone() {
        for ((title, text) in listOf("New recipe posted" to "Lemon cake", "Is your phone at risk? What to know" to "")) {
            assertEquals(title, listOf<Problem?>(null, null), fromAnAllowedSite(title, text))
        }
    }

    /** WP17 A4 (hostile): look-alike letters, digits for letters and the gate's own phrasings are still caught. */
    @Test fun alarmingWordsInDisguiseFromASiteSheAllowedAreStillCaught() {
        val missed = listOf(
            "Vіrus found" to "Tap to remove", // a Cyrillic і
            "𝐕𝐢𝐫𝐮𝐬 found" to "Tap to remove", // maths-bold "Virus"
            "V1rus found" to "Tap to remove",
            "Your phone has 3 v1ruses" to "",
            "Your Samsung needs cleaning" to "",
            "Your battery is 87% damaged" to "",
            "Your phone will be damaged" to "",
            "Phone running hot? It may be damaged" to "",
            "Your Motorola is running slow" to "",
            "Your system is corrupted" to "",
        ).filter { (title, text) -> fromAnAllowedSite(title, text) != listOf(Problem.SiteScaryNews, Problem.SiteScaryNews) }
        assertEquals("left alone, but alarming", emptyList<Pair<String, String>>(), missed)
    }

    /** WP17 M1: a site she did not allow that sends alarming words is called fake and the guardian is told, even when it reads like news. */
    @Test fun aNewSitesFakeWarningAlwaysTellsTheGuardian() {
        val notAllowed = baselineWith(setOf("https://news.example"))
        for ((title, text) in listOf(
            "Retailer hacked" to "Millions of customer records stolen in data breach",
            "How to tell if your phone is hacked" to "Five signs to look for",
            "Your phone is infected" to "Tap here",
        )) {
            val problem = detector.evaluate(inputs(event = headline.copy(title = title, text = text)).copy(baseline = notAllowed)).single().key.problem
            assertEquals(title, Problem.SiteNotifications, problem)
            assertEquals(title, true, problem.forGuardian("Mom", host = site) != null)
        }
    }

    @Test fun aNewSitePostingAScareHeadlineIsStillCalledFake() {
        val known = baselineWith(setOf("https://news.example"))
        assertEquals(Problem.SiteNotifications, detector.evaluate(inputs(event = headline).copy(baseline = known)).single().key.problem)
    }

    @Test fun ordinaryAppNotificationsAreIgnored() {
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(event = message("VIRUS DETECTED"))))
    }
}

class DnsDetectorTest {
    private val detector = DnsDetector()
    private val chosen = Setup("security.cloudflare-dns.com", emptySet())
    private fun problems(dns: PrivateDns, setup: Setup = chosen) = detector.evaluate(inputs(snapshot().copy(privateDns = dns), setup)).map { it.key.problem }

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
        assertEquals(setOf(Problem.ListenerOff), problems(inputs(snapshot().copy(listenerEnabled = false), setup = finished)))
        assertEquals(setOf(Problem.NotificationsBlocked), problems(inputs(snapshot().copy(canPostNotifications = false), setup = finished)))
    }

    /** A bound listener is never "can't see your notices", even if the stored last-seen time is old (its write failed; finding #134). */
    @Test fun aListenerThatIsBoundNowIsNeverReportedAsNotListening() {
        val stale = com.aegida.core.health.ListenerTimes(lastSeenBound = Fixtures.now.minus(Duration.ofHours(5)))
        val bound = inputs(snapshot(), setup = finished).copy(listener = stale)
        assertEquals(emptySet<Problem>(), problems(bound))
        assertEquals(setOf(Problem.ListenerNotConnected), problems(bound.copy(snapshot = snapshot().copy(listenerBound = false))))
    }

    /**
     * Before setup is finished she has not been asked yet, or is half way through the step that asks. Raising "Aegida can no
     * longer see your notifications" then is untrue and alarming (findings #65). If she skips the step, it is raised the
     * moment setup finishes, and her guardian is told then.
     */
    @Test fun lostPermissionsAreNotRaisedWhileSetupIsStillBeingDone() {
        val notDone = Setup.NotDone.copy(stepsDone = SetupStep.Access.ordinal)
        assertEquals(emptySet<Problem>(), problems(inputs(snapshot().copy(listenerEnabled = false, canPostNotifications = false), setup = notDone)))
        assertEquals(emptySet<Problem>(), problems(inputs(snapshot().copy(listenerEnabled = false), setup = Setup.NotDone)))
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
