package com.aegida.detectors

import com.aegida.core.model.Baseline
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.Snapshot
import com.aegida.core.notice.NoticeRow
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.message
import com.aegida.detectors.Fixtures.now
import com.aegida.detectors.Fixtures.snapshot
import com.aegida.testkit.aChromePopup
import com.aegida.testkit.aPlayApp
import com.aegida.testkit.aSystemApp
import com.aegida.testkit.anAppOpenedFromAFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * #164 step 2 (docs/scenarios/warning-names-its-trigger.md): the one line that says what set a warning off, made from the evidence the
 * real detectors write. Home and the "Was that you?" page show these lines; History keeps them without the day.
 */
class WhatItIsTest {
    private val zone = ZoneOffset.UTC
    private val locale = Locale.US
    private val baseline = Baseline.of(snapshot().copy(apps = listOf(aSystemApp("com.android.settings"))))

    private fun Finding.what() = Texts.Home.whatItIs(key.problem, evidence, now, zone, locale)

    private fun drift(phone: Snapshot) = DriftDetector().evaluate(inputs(snapshot = phone).copy(baseline = baseline))

    private fun listener(app: com.aegida.core.model.InstalledApp) =
        drift(snapshot().copy(notificationListeners = setOf(app.packageName), apps = listOf(app))).single()

    private fun site(origin: String, problem: Problem = Problem.NewWebsiteNotifications): Finding {
        val calm = aChromePopup(now, site = origin, title = "New recipe posted", text = "Lemon cake")
        val event = if (problem == Problem.NewWebsiteNotifications) calm else aChromePopup(now, site = origin)
        val base = Baseline.of(snapshot()).copy(websites = setOf("https://other.example"))
        return PopupSpamDetector().evaluate(inputs(event = event).copy(baseline = base)).single { it.key.problem == problem }
    }

    @Test fun aNewWebsiteQuestionNamesTheWebsiteWithItsPort() { // WN1, Kevin's report
        assertEquals(listOf("The website: localhost:8765"), site("http://localhost:8765/").what())
        assertEquals(listOf("The website: news.example"), site("https://news.example/").what())
    }

    @Test fun aWebsiteSendingFakeWarningsNamesTheWebsite() { // WN2
        assertEquals(listOf("The website: fake.example"), site("https://fake.example/", Problem.SiteNotifications).what())
    }

    @Test fun aWebsiteWithLettersFromAnotherAlphabetOrALookAlikeNameShowsAsUnusual() { // WN1 variants, review
        assertEquals(listOf(Texts.Home.WEBSITE_UNUSUAL), site("https://xn--pple-43d.example/").what())
        assertEquals("all in another alphabet, no look-alike", listOf(Texts.Home.WEBSITE_UNUSUAL), site("https://xn--e1afmkfd.example/").what())
        assertEquals(listOf(Texts.Home.WEBSITE_UNUSUAL), site("https://paypa1-login.example/").what())
    }

    @Test fun aVeryLongWebsiteNameIsCutShort() { // WN20 for websites
        val line = site("https://" + "a".repeat(60) + ".example/").what().single()
        assertEquals("The website: " + "a".repeat(40) + "…", line)
    }

    @Test fun anAppThatCanNowReadNotificationsShowsTheNameItCallsItselfAndWhereItCameFrom() { // WN4, Kevin's decision 1
        val watch = aSystemApp("com.google.android.apps.wearables").copy(label = "Pixel Watch")
        assertEquals(listOf("The app calls itself: “Pixel Watch”", "It came with the phone."), listener(watch).what())
    }

    @Test fun anAppThatCameWithThePhoneNeverShowsADayEvenWhenOneWasRead() { // WN29: its day is the factory's
        val preloaded = aSystemApp("com.vendor.helper").copy(label = "Helper", firstInstalled = now)
        assertEquals(listOf("The app calls itself: “Helper”", "It came with the phone."), listener(preloaded).what())
    }

    @Test fun anAppFromTheMakersStoreSaysTheGalaxyStore() { // WN28, WN33
        val app = com.aegida.testkit.aGalaxyStoreApp("org.real").copy(label = "Real", firstInstalled = now)
        val phone = snapshot().copy(
            notificationListeners = setOf("org.real"), apps = listOf(aSystemApp(com.aegida.testkit.GALAXY_STORE), app),
            makerStores = setOf(com.aegida.testkit.GALAXY_STORE),
        )
        assertEquals(listOf("The app calls itself: “Real”", "It came from the Galaxy Store.", "Added today."), drift(phone).single().what())
    }

    @Test fun eachUrgentAppKindSaysTheAppCallsItself() { // WN5, WN6, WN7, WN8
        val app = anAppOpenedFromAFile("org.x").copy(label = "Helper", firstInstalled = now)
        val phone = snapshot().copy(accessibilityApps = setOf("org.x"), deviceAdmins = setOf("org.x"), apps = listOf(app))
        drift(phone).forEach {
            assertEquals(it.key.problem.name, listOf("The app calls itself: “Helper”", "It came from outside the Play Store.", "Added today."), it.what())
        }
        val remote = aPlayApp("com.anydesk.anydeskandroid").copy(label = "AnyDesk", firstInstalled = now.minus(Duration.ofDays(1)))
        val finding = RemoteControlDetector().evaluate(inputs(snapshot = snapshot().copy(apps = listOf(remote))).copy(baseline = baseline)).single()
        assertEquals(listOf("The app calls itself: “AnyDesk”", "It came from the Play Store.", "Added yesterday."), finding.what())
    }

    @Test fun aChangedDefaultAppSaysTheAppIsCalledAndTheDayItWasAdded() { // WN9
        val added = now.minus(Duration.ofDays(20))
        val firefox = aPlayApp("org.mozilla.firefox").copy(label = "Firefox", firstInstalled = added)
        val finding = drift(snapshot().copy(browser = "org.mozilla.firefox", apps = listOf(firefox))).single()
        val sameYear = added.atZone(zone).year == now.atZone(zone).year
        val day = DateTimeFormatter.ofPattern(if (sameYear) "MMMM d" else "MMMM d, yyyy", locale).format(added.atZone(zone))
        assertEquals(listOf("The app is called: “Firefox”", "It came from the Play Store.", "Added on $day."), finding.what())
    }

    /** WN36 (#167): an app added in an earlier year says the year, so one from years ago never reads as this month. */
    @Test fun anAppAddedInAnEarlierYearSaysTheYear() {
        val added = now.atZone(zone).minusYears(3).toInstant()
        val app = aPlayApp("org.mozilla.firefox").copy(label = "Firefox", firstInstalled = added)
        val finding = drift(snapshot().copy(browser = "org.mozilla.firefox", apps = listOf(app))).single()
        val day = DateTimeFormatter.ofPattern("MMMM d, yyyy", locale).format(added.atZone(zone))
        assertEquals("Added on $day.", finding.what().last())
    }

    @Test fun aNewAppFromOutsideThePlayStoreSaysSo() { // WN10
        val app = anAppOpenedFromAFile("org.fun.game").copy(label = "Fun Game", firstInstalled = now)
        val finding = NewAppDetector().evaluate(inputs(snapshot = snapshot().copy(apps = listOf(app))).copy(baseline = baseline)).single()
        assertEquals(listOf("The app is called: “Fun Game”", "It came from outside the Play Store.", "Added today."), finding.what())
    }

    @Test fun aNoisyAppSaysWhichAppItCameFrom() { // WN11
        val app = aPlayApp("com.android.shell").copy(label = "Image to PDF", category = 3, firstInstalled = now)
        val notices = listOf(3, 2, 0).mapIndexed { i, s -> NoticeRow(now.minus(Duration.ofMinutes(10L * (i + 1))), s) }
        val event = message("File converter is ready! Convert", title = "Image to PDF")
        val finding = NoisyAppDetector().evaluate(inputs(snapshot = snapshot().copy(apps = listOf(app)), event = event).copy(notices = notices)).single()
        assertEquals(listOf("It came from the “Image to PDF” app.", "It came from the Play Store.", "Added today."), finding.what())
    }

    @Test fun aScamLinkSaysTheAppItCameInOrItsSenderWhenTheAppIsNotKnown() { // WN12, WN13
        val shell = aSystemApp("com.android.shell").copy(label = "Messages")
        val known = LinkDetector().evaluate(inputs(snapshot = snapshot().copy(apps = listOf(shell)), event = message("Refund: https://evil.example/claim")))
        assertEquals(listOf("It came from the “Messages” app."), known.single().what())
        val unknown = LinkDetector().evaluate(inputs(event = message("Refund: https://evil.example/claim", title = "Chase Bank")))
        assertEquals(listOf("From: “Chase Bank”"), unknown.single().what())
    }

    @Test fun settingAndHealthWarningsHaveNoExtraLine() { // WN14, WN15, WN16
        val settingKinds = listOf(
            Problem.WebsiteFilterOff, Problem.ListenerOff, Problem.ListenerNotConnected, Problem.NotificationsBlocked, Problem.GuardianUnreachable,
        )
        settingKinds.forEach { assertEquals(it.name, emptyList<String>(), Texts.Home.whatItIs(it, mapOf(Evidence.HOST to "x.example"), now, zone, locale)) }
    }

    @Test fun anAppNameShapedLikeAWebAddressShowsTheAddressAsLinkButANameWithADotStays() { // WN17, review: "Booking.com" was "(link)"
        fun line(label: String) = listener(anAppOpenedFromAFile("org.l").copy(label = label)).what().first()
        assertEquals("The app calls itself: “Booking.com”", line("Booking.com"))
        assertEquals("The app calls itself: “(link)”", line("chase-secure.com"))
        assertEquals("The app calls itself: “Open (link) now”", line("Open https://pay.example/x now"))
        assertEquals("The app calls itself: “(link)”", line("www.helper.example"))
    }

    @Test fun anAppNameThatIsAnInstructionIsQuotedAsTheAppsOwnWords() { // WN18
        fun line(label: String) = listener(anAppOpenedFromAFile("org.l").copy(label = label)).what().first()
        assertEquals("The app calls itself: “Tap Yes Google Security”", line("Tap Yes\nGoogle‮Security"))
        assertEquals("The app calls itself: “Aegida update - tap Yes”", line("Aegida update - tap Yes"))
        assertEquals("right-to-left words are drawn on their own", "The app calls itself: “⁨שלום Tap⁩”", line("שלום Tap"))
    }

    @Test fun aVeryLongAppNameIsCutToFortyCharacters() { // WN20
        val line = listener(anAppOpenedFromAFile("org.l").copy(label = "A".repeat(200))).what().first()
        assertEquals("The app calls itself: “${"A".repeat(40)}…”", line)
    }

    @Test fun anAppWithNoNameAegidaCanReadSaysSoAndNeverShowsItsPackage() { // WN22
        val blank = listener(anAppOpenedFromAFile("com.example.app").copy(label = " ", firstInstalled = now)).what()
        assertEquals(listOf("Aegida can't read this app's name.", "It came from outside the Play Store.", "Added today."), blank)
        val missing = drift(snapshot().copy(notificationListeners = setOf("com.example.gone"))).single().what()
        assertEquals(listOf("Aegida can't read this app's name."), missing)
        assertTrue((blank + missing).none { "com.example" in it })
    }
}
