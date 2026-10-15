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
        assertEquals(Problem.SiteNotifications, detector.evaluate(inputs(event = chromeSiteNotification).copy(baseline = known)).single().key.problem)
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

    // Scenario rows WP11, WP15, WP16 (docs/scenarios/website-popups.md); findings #109.
    private val headline = chromeSiteNotification.copy(title = "Retailer hacked", text = "Millions of customer records stolen in data breach")

    @Test fun aNewsHeadlineFromASiteSheSaidYesToIsLeftInPlace() {
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(event = headline).copy(baseline = baselineWith(setOf(site)))))
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(event = headline, setup = Setup(null, setOf(site)))))
        val malware = headline.copy(title = "New Android malware found in 40 apps", text = "Researchers say the virus spreads by text")
        assertEquals(emptyList<Any>(), detector.evaluate(inputs(event = malware).copy(baseline = baselineWith(setOf(site)))))
        for (news in listOf( // findings #119: the wider fake-virus words still leave news alone
            headline.copy(title = "Hackers stole card data", text = "Shoppers are urged to check their statements"),
            headline.copy(title = "New virus found in bats", text = "Scientists say there is no risk to people"),
            headline.copy(title = "Your favourite retailer was hacked", text = "What it means for shoppers"),
        )) {
            assertEquals(news.title, emptyList<Any>(), detector.evaluate(inputs(event = news).copy(baseline = baselineWith(setOf(site)))))
        }
    }

    /** WP15 variants (findings #119): the common fake-virus wordings are about her phone, even from a site she said Yes to. */
    @Test fun aSiteSheSaidYesToSayingVirusFoundOrYourPhoneIsInfectedIsStillHidden() {
        val known = baselineWith(setOf(site))
        for (scare in listOf(
            headline.copy(title = "3 viruses found! Remove now", text = "Tap here"),
            headline.copy(title = "Virus found", text = "Tap to remove"),
            headline.copy(title = "Malware detected. Remove immediately", text = ""),
            headline.copy(title = "Your Samsung is infected", text = "Tap here"),
            headline.copy(title = "Your Galaxy S25 is infected", text = "Tap here"),
            headline.copy(title = "Your iPhone is infected", text = "Tap here"),
        )) {
            assertEquals(scare.title, Problem.SiteNotifications, detector.evaluate(inputs(event = scare).copy(baseline = known)).singleOrNull()?.key?.problem)
        }
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
            assertEquals(scare.title, Problem.SiteNotifications, detector.evaluate(inputs(event = scare).copy(baseline = known)).single().key.problem)
            val found = detector.evaluate(inputs(event = scare, setup = Setup(null, setOf(site)))).single()
            assertEquals(scare.title, Problem.SiteNotifications, found.key.problem)
        }
    }

    // Scenario row WP17 (docs/scenarios/website-popups.md, findings #126): each case is a row of its table, named by its ID.
    private fun onAKnownSite(cases: List<Triple<String, String, String>>, expected: Problem?): List<String> = cases.filter { (_, title, text) ->
        val event = headline.copy(title = title, text = text)
        detector.evaluate(inputs(event = event).copy(baseline = baselineWith(setOf(site)))).singleOrNull()?.key?.problem != expected
    }.map { it.first }

    @Test fun aNewsStoryFromASiteSheSaidYesToStaysWhereItIs() {
        val news = listOf(
            Triple("S1", "New malware found: what to know", "Security researchers say the app was downloaded a million times"),
            Triple("S2", "Malware found.", "Researchers say 40 apps on the Play Store carried it"),
            Triple("S3", "Bird flu virus found", "Officials confirm the virus in a dairy herd in Texas"),
            Triple("S4", "Your favourite retailer has been infected with ransomware", "Here is what shoppers should know"),
            Triple("S5", "Malware found on Google Play", "Google has removed the apps from its store"),
            Triple("S6", "Trojan detected in banking apps", "Researchers warn the malware targets 60 banks"),
            Triple("S7", "Mpox virus detected in county", "Health officials urge vaccination"),
            Triple("S8", "Retailer hacked", "Millions of customer records stolen in data breach"),
            Triple("S9", "New Android malware found in 40 apps", "Researchers say the virus spreads by text"),
            Triple("S10", "New virus found in bats", "Scientists say there is no risk to people"),
            Triple("S11", "Your favourite retailer was hacked", "What it means for shoppers"),
            Triple("S12", "Hospital systems infected with ransomware", "Patients are being sent to other hospitals"),
            Triple("S13", "West Nile virus found in mosquitoes in three counties", "Residents are asked to use insect repellent"),
            Triple("S14", "City water system hacked", "Officials say drinking water was not affected"),
            Triple("S15", "Measles virus detected in wastewater", "The health department asks parents to check vaccination records"),
            Triple("S16", "Pharmacy chain hacked, prescriptions delayed", "Customers are told to call their store"),
            Triple("S17", "Police: man hacked ex-partner's email", "He faces three charges"),
            Triple("S18", "Spyware found on journalists' phones", "The group says the attack used a flaw in messaging apps"),
            Triple("S19", "Banking trojan spreads through fake delivery texts", "Experts say the malware asks for card numbers"),
            Triple("S20", "Norovirus outbreak: 200 infected on cruise ship", "The ship returned to port early"),
            Triple("S21", "Two new viruses found in deer", "Scientists say they pose no risk to humans"),
            Triple("S22", "3 new strains of the virus detected in Europe", "Scientists are tracking how fast they spread"),
            Triple("S23", "Election office computers infected with malware, officials say", "No votes were changed"),
            Triple("S24", "Teen charged after school website hacked", "Police say no student data was taken"),
            Triple("S25", "Bird flu virus detected in milk samples", "FDA says pasteurised milk carries no live virus"),
            Triple("S26", "Car maker infected by ransomware, plants shut", "Production should restart next week"),
            Triple("S27", "Fake shopping apps carry spyware, researchers warn", "The apps have been removed from the store"),
            Triple("S28", "Google to remove 40 infected apps from Play Store", "The apps showed ads and stole contacts"),
            Triple("S29", "Microsoft says staff email was hacked", "Customer data was not touched"),
            Triple("S30", "How a virus found in cats is changing vaccines", "Researchers at the university explain"),
            Triple("S31", "Zika virus found in local mosquitoes; spraying set for Tuesday", "Stay indoors at dusk, officials say"),
            Triple("S32", "Bank's app hit by trojan; customers told to change passwords", "The bank says no money was taken"),
            Triple("S33", "Clean-up continues after malware attack on port", "Ships are moving again"),
            // Findings #133: a noun that is also an order word, and "your" + a name that is not a phone.
            Triple("S34", "Click fraud malware found in 60 apps", "Researchers say the apps made fake ad taps"),
            Triple("S35", "Tap water tested for virus", "The city says the water is fine to drink"),
            Triple("S36", "Clean energy firm hacked", "Customer records may have been taken"),
            Triple("S37", "Press freedom group says reporters' phones hacked", "The group asks for an inquiry"),
            Triple("S38", "Retailer hacked", "Tap to read the full story"),
            Triple("S39", "Your pharmacy was hacked", "What customers should know"),
            Triple("S40", "Fix for Android malware released by Google", "The update reaches phones this month"),
            Triple("S41", "Scan of sewage finds polio virus", "Health officials ask parents to check vaccinations"),
            Triple("S42", "Your bank was hacked: what to do next", "Experts explain"),
            Triple("S43", "Experts tell users to remove the apps now", "The malware hides in fake games"),
            // Findings #138: "it" after a question about someone else's phone is not her phone.
            Triple("S44", "Is the new Pixel any good? It was hacked in a contest", "Researchers say the flaw is fixed"),
            // Findings #139: the words of a present-tense claim, said about other people's phones, banks or hospitals.
            Triple("S45", "Millions of phones at risk from new bug", "Researchers say the malware spreads by text"),
            Triple("S46", "Banks under attack from hackers", "Customer accounts hacked in three states"),
            Triple("S47", "Is your phone at risk? What to know", ""),
            Triple("S48", "Android phones are at risk from new malware", "Google says an update is coming"),
            Triple("S49", "Your bank is under attack: accounts hacked", "What customers should do"),
            Triple("S50", "Hospital computers locked by ransomware", "Officials say the malware came by email"),
            Triple("S51", "Retailer hacked", "Tap below to read the full story"),
        )
        assertEquals("hidden as fake, but news", emptyList<String>(), onAKnownSite(news, expected = null))
    }

    /** WP17 A rows (Kevin, Oct 14; findings #133): real news that speaks to the reader is hidden, but the guardian is not told. */
    @Test fun newsThatSpeaksToTheReaderFromASiteSheSaidYesToIsHiddenWithoutTellingTheGuardian() {
        val advice = listOf(
            Triple("A1", "Is your phone infected? Here's how to check", ""),
            Triple("A2", "Have you been hacked? Here's how to check", ""),
            Triple("A3", "Delete these apps now: malware found on Google Play", ""),
            Triple("A4", "Check your phone for this spyware", "Researchers found it in 12 apps"),
            Triple("A5", "Malware can take over your Android", "Here is how it spreads"),
            Triple("A6", "How to tell if your phone is hacked", "Five signs to look for"),
            Triple("A7", "New spyware found that can infect your phone", "Researchers say it hides in fake games"),
            // Findings #138: after a question about her phone, "it" with no bad state, not first, or with a condition is not a claim.
            Triple("A8", "Is your phone hacked? It could be, here's how to tell", ""),
            Triple("A9", "Is your phone listening to you? It probably isn't, but spyware can", ""),
            Triple("A10", "Is your phone slow? Experts say it may have a virus", ""),
            Triple("A11", "Is your phone slow? It may have a virus if it does these 5 things", ""),
            // Findings #139: a question, a hedge ("may be"), a condition, the past, or "protect your phone" is no claim about her phone.
            Triple("A12", "Is your phone at risk? What to know about the new spyware", ""),
            Triple("A13", "Your phone may be at risk from this new malware", ""),
            Triple("A14", "Your phone is at risk if you skip this update", "The malware spreads by text"),
            Triple("A15", "Why your phone is slow: 5 fixes", ""),
            Triple("A16", "Protect your phone from this new malware", ""),
            Triple("A17", "Your phone was at risk from this bug until the June update", "The spyware has been removed from the store"),
            Triple("A18", "Do experts say your phone is at risk from the new spyware?", ""),
        )
        assertEquals("not hidden as news to the reader", emptyList<String>(), onAKnownSite(advice, expected = Problem.SiteScaryNews))
        assertEquals(null, Problem.SiteScaryNews.forGuardian("Mom", host = site))
        assertEquals("hidden from the shade, as a fake warning is", true, Problem.SiteScaryNews.hidesNotification)
    }

    @Test fun aFakeVirusWarningFromASiteSheSaidYesToIsHidden() {
        val fake = listOf(
            Triple("H1", "Viruses found - tap to fix", ""),
            Triple("H2", "Virus found, clean up now", ""),
            Triple("H3", "13 viruses found", "Download cleaner"),
            Triple("H4", "Security alert: virus found", "Click OK"),
            Triple("H5", "(3) viruses were found", ""),
            Triple("H6", "System infected", "Remove virus immediately"),
            Triple("H7", "Your Samsung Galaxy A15 is infected", ""),
            Triple("H8", "Your Galaxy S25 Ultra 5G is infected", ""),
            Triple("H9", "Your Samsung is now infected", ""),
            Triple("H10", "Your Samsung may be infected", ""),
            Triple("H11", "Your iPhone has been hacked", ""),
            Triple("H12", "3 viruses found! Remove now", "Tap here"),
            Triple("H13", "Virus found", "Tap to remove"),
            Triple("H14", "Malware detected. Remove immediately", ""),
            Triple("H15", "Your phone is infected", "Tap here"),
            Triple("H16", "Your device has been hacked", "Tap to fix"),
            Triple("H17", "Security warning", "Battery damaged by virus. Clean now"),
            Triple("H18", "Alert", "Trojan detected on this phone"),
            Triple("H19", "Warning", "You are infected"),
            // H20 and H21 are now A1 and A2 (Kevin, Oct 14).
            Triple("H22", "Your Android is infected with 4 viruses", "Clean now"),
            Triple("H23", "(2) viruses detected", "Your phone may be damaged"),
            Triple("H24", "Your phone is infected with (5) viruses", "Scan now to remove them"),
            Triple("H25", "Virus alert!", "Your device is infected. Tap to clean"),
            Triple("H26", "Warning! Your mobile has been hacked", "Install the cleaner now"),
            Triple("H27", "Malware found", "Tap to scan and remove"),
            Triple("H28", "Your Pixel 8 is infected", ""),
            Triple("H29", "Spyware detected!", "Click here to delete it"),
            Triple("H30", "You have a virus", "Download the cleaner to fix it"),
            Triple("H31", "Battery 28% damaged by a virus", ""),
            Triple("H32", "Your iPhone has been infected with viruses", ""),
            Triple("H33", "Infected!", "Fix your device now"),
            Triple("H34", "Hacked!", "Your Chrome browser has been hacked"),
            Triple("H35", "WARNING: VIRUS FOUND!", "Clean up your phone now"),
            Triple("H36", "Your phone has been hacked!", "Press OK to clean it"),
            Triple("H37", "2 viruses detected", "Please remove them now"),
            // H38 is now A3 (Kevin, Oct 14). Findings #133:
            Triple("H39", "Your system is heavily damaged by four viruses", ""),
            Triple("H40", "Your Samsung needs cleaning", ""),
            Triple("H41", "Your Motorola has 3 viruses", ""),
            Triple("H42", "Android infected with 13 viruses!", ""),
            Triple("H43", "You may have a virus", ""),
            Triple("H44", "You've got a virus", ""),
            Triple("H45", "Your battery is 87% damaged", ""),
            Triple("H46", "Your phone will be damaged", ""),
            Triple("H47", "Vіrus found", "Tap to remove"), // a Cyrillic і
            Triple("H48", "𝐕𝐢𝐫𝐮𝐬 found", "Tap to remove"), // maths-bold "Virus"
            Triple("H49", "V1rus found", "Tap to remove"),
            Triple("H50", "Your phone has 3 v1ruses", ""),
            Triple("H51", "Virus found (tap to remove)", ""),
            Triple("H52", "Your Xiaomi got hacked", ""),
            Triple("H53", "Your system is infected", "Clean it now"),
            Triple("H54", "Your new Pixel 9 is infected", ""),
            Triple("H55", "Your Motorola is infected", ""),
        )
        assertEquals("left alone, but a fake warning", emptyList<String>(), onAKnownSite(fake, expected = Problem.SiteNotifications))
    }

    /** The fake warnings docs/site/how-it-works.html names, each on its own, as the page quotes them (finding #147). */
    @Test fun theFakeWarningsTheHowItWorksPageNamesAreHiddenOnASiteSheSaidYesTo() {
        val named = listOf(
            Triple("page 1", "your phone is infected", ""),
            Triple("page 2", "your phone is at risk", ""),
            Triple("page 3", "clean it now", ""), // "tap to clean" alone never passed the scare-word gate
        )
        assertEquals("left alone, but a fake warning", emptyList<String>(), onAKnownSite(named, expected = Problem.SiteNotifications))
    }

    /** WP17 H56-H62 (findings #138): a fake that asks about her phone first, then says something about "it" or "your device". */
    @Test fun aFakeThatAsksAboutHerPhoneFirstIsHidden() {
        val fake = listOf(
            Triple("H56", "Is your phone slow? It may have a virus", ""),
            Triple("H57", "Is your phone acting strange? It is infected with 3 viruses", ""),
            Triple("H58", "Phone running hot? It may be damaged", ""),
            Triple("H59", "Is your phone slow?", "Your device may have a virus"),
            Triple("H60", "Is your phone running slow?", "It has been hacked"),
            Triple("H61", "Is your Android slow!?", "It might have a virus"),
            Triple("H62", "Is your phone slow? It has a virus", ""),
        )
        assertEquals("left alone, but a fake warning", emptyList<String>(), onAKnownSite(fake, expected = Problem.SiteNotifications))
    }

    /** WP17 H63-H80 (findings #139): a present-tense claim about her phone ("is at risk", "is under attack"), or an order to protect it. */
    @Test fun aFakeThatSaysHerPhoneIsAtRiskOrTellsHerToProtectItIsHidden() {
        val fake = listOf(
            Triple("H63", "Virus detected! Your phone is at risk", ""),
            Triple("H64", "Your phone is under attack! Tap below to protect it", ""),
            Triple("H65", "Your device is not protected", "Download antivirus now"),
            Triple("H66", "Your phone is locked", ""),
            Triple("H67", "Your phone is running slow", ""),
            Triple("H68", "Your system is corrupted", ""),
            Triple("H69", "Virus found! Your phone's at risk", ""),
            Triple("H70", "Virus alert: this device is in danger", ""),
            Triple("H71", "Your Galaxy S25 is under attack by a virus", ""),
            Triple("H72", "Your phone is now unprotected", ""),
            Triple("H73", "Virus detected!", "Tap below to remove it"),
            Triple("H74", "Malware found", "Download antivirus now"),
            Triple("H75", "Spyware detected", "Install protection now"),
            Triple("H76", "Virus found", "Tap to protect your phone"),
            Triple("H77", "Virus found. Protect it now", ""),
            Triple("H78", "Your device is being attacked", ""),
            Triple("H79", "Your phone has been locked", ""),
            Triple("H80", "Your Android phone is at risk from new malware", ""),
        )
        assertEquals("left alone, but a fake warning", emptyList<String>(), onAKnownSite(fake, expected = Problem.SiteNotifications))
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
