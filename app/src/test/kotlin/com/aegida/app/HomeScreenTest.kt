package com.aegida.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aegida.app.ui.GuardianReach
import com.aegida.app.ui.HomeScreen
import com.aegida.app.ui.ShownAt
import com.aegida.core.alert.Guardians
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.Setup
import com.aegida.core.model.SetupStep
import com.aegida.alerts.FindingActions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import com.aegida.core.model.HomeState
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.time.Duration

/** Home, in light and dark at 100% and 200% font. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class HomeScreenTest(dark: Boolean, fontScale: Float) : ScreenTestBase(dark, fontScale) {

    @Test fun homeWithNoKnownProblemsSaysSoAndOffersACall() {
        var called = 0
        show {
            HomeScreen(
                HomeState.NoKnownProblems(now.minus(Duration.ofHours(3))), picture, ShownAt(now, zone, locale), {},
                GuardianReach("Sam", "555 0100", dialerExists = true, onCall = { called++ }),
            )
        }
        reachable(Texts.Home.ALL_GOOD)
        reachable(Texts.Home.NO_KNOWN_PROBLEMS)
        compose.onNodeWithText(Texts.Home.CALL_A_GUARDIAN).scrollIfInsideScroller().performClick()
        assertEquals(1, called)
    }

    @Test fun homeWithOneProblemShowsItsSentenceAndABigFixButton() {
        val dns = finding(Problem.WebsiteFilterOff)
        var fixed: Finding? = null
        show { HomeScreen(HomeState.NeedsTaps(listOf(dns)), picture, ShownAt(now, zone, locale), { fixed = it }, withNumber) }
        reachable(Texts.Home.NEEDS_ONE)
        reachable(Problem.WebsiteFilterOff.forMom)
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).scrollIfInsideScroller().performClick()
        assertEquals(dns, fixed)
        reachable(Texts.Home.CALL_A_GUARDIAN)
    }

    /** WN1 (#164, Kevin's report): a new website's question names the website on Home, under the sentence. */
    @Test fun homeNamesTheWebsiteANewWebsiteQuestionIsAbout() {
        val site = Finding(
            FindingKey(DetectorId.PopupSpam, Problem.NewWebsiteNotifications, "https://news.example"),
            mapOf(com.aegida.core.model.Evidence.SITE to "https://news.example"), now, now,
        )
        show { HomeScreen(HomeState.NeedsTaps(listOf(site)), picture, ShownAt(now, zone, locale), {}) }
        reachable(Problem.NewWebsiteNotifications.forMom)
        reachable("The website: news.example")
    }

    /**
     * WN4, WN20 (#164): an urgent warning names the app as it calls itself, with where it came from and when, and on a small phone at
     * 200% text the line does not push the big button off the screen.
     */
    @Test @org.robolectric.annotation.Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun homeNamesTheAppAnUrgentWarningIsAboutAndKeepsTheButtonOnScreen() {
        val e = com.aegida.core.model.Evidence
        val listener = Finding(
            FindingKey(DetectorId.Drift, Problem.ListenerAdded, "org.helper"),
            mapOf(e.APP_LABEL to "W".repeat(60), e.APP_SOURCE to "outside", e.APP_ADDED to now.toEpochMilli().toString()), now, now,
        )
        show { HomeScreen(HomeState.NeedsTaps(listOf(listener)), picture, ShownAt(now, zone, locale), {}) }
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).assertIsDisplayed()
        reachable("The app calls itself: “${"W".repeat(40)}…”")
        reachable("It came from outside the Play Store.")
        reachable("Added today.")
    }

    /** WN14: a setting warning has no extra line. */
    @Test fun homeShowsNoExtraLineForASettingWarning() {
        show { HomeScreen(HomeState.NeedsTaps(listOf(finding(Problem.WebsiteFilterOff))), picture, ShownAt(now, zone, locale), {}) }
        compose.onNodeWithText("The website", substring = true).assertDoesNotExistOrFail()
        compose.onNodeWithText("The app", substring = true).assertDoesNotExistOrFail()
    }

    /** WN26 (Kevin, Oct 22): tapping a "Was that you?" banner opens Aegida's question page for that warning, naming the website. */
    @Test fun aTappedQuestionBannerOpensItsQuestionPage() {
        val question = Finding(
            FindingKey(DetectorId.PopupSpam, Problem.NewWebsiteNotifications, "https://news.example"),
            mapOf(com.aegida.core.model.Evidence.SITE to "https://news.example"), now, now,
        )
        showApp(HomeState.NeedsTaps(listOf(question)), asked = question.key)
        reachable(Texts.Home.CONFIRM_YES)
        reachable("The website: news.example")
    }

    /** WN26: a banner for a warning that has closed since opens Home, not a question about nothing. */
    @Test fun aTappedBannerForAClosedWarningOpensHome() {
        val gone = FindingKey(DetectorId.PopupSpam, Problem.NewWebsiteNotifications, "https://gone.example")
        showApp(HomeState.NoKnownProblems(now), asked = gone)
        reachable(Texts.Home.ALL_GOOD)
        compose.onNodeWithText(Texts.Home.CONFIRM_YES).assertDoesNotExistOrFail()
    }

    /**
     * WN34 (#165): Aegida is already open in the background with a screen from before the warning. The tap's key arrives first
     * (onNewIntent), the reload after it (onResume): the question page opens once the screen has the warning, not Home.
     */
    @Test fun aBannerTappedWhileAegidaIsOpenOpensTheQuestionOnceTheScreenHasReloaded() {
        val question = Finding(
            FindingKey(DetectorId.PopupSpam, Problem.NewWebsiteNotifications, "https://news.example"),
            mapOf(com.aegida.core.model.Evidence.SITE to "https://news.example"), now, now,
        )
        var home: HomeState = HomeState.NoKnownProblems(now)
        // As on a phone, a reload runs off the main thread and finishes after the screen has been drawn: here, when the test says.
        val work = kotlinx.coroutines.test.StandardTestDispatcher()
        val vm = appViewModel(work) { home }
        var asked by androidx.compose.runtime.mutableStateOf<FindingKey?>(null)
        show { com.aegida.app.ui.AppRoot(vm, asked = asked, onAsked = { asked = null }) }
        work.scheduler.advanceUntilIdle()
        reachable(Texts.Home.ALL_GOOD)
        home = HomeState.NeedsTaps(listOf(question)) // the check that posted the banner stored the warning
        asked = question.key // onNewIntent
        compose.waitForIdle() // drawn with the screen from before the warning
        vm.refresh(runCheck = false) // onResume
        work.scheduler.advanceUntilIdle()
        reachable(Texts.Home.CONFIRM_YES)
        reachable("The website: news.example")
    }

    /** WN34: the warning closed before the reload finished: Home, and the request is used up (a later reload opens no question). */
    @Test fun aBannerTappedWhileAegidaIsOpenForAWarningClosedSinceOpensHome() {
        val gone = Finding(
            FindingKey(DetectorId.PopupSpam, Problem.NewWebsiteNotifications, "https://gone.example"),
            mapOf(com.aegida.core.model.Evidence.SITE to "https://gone.example"), now, now,
        )
        var home: HomeState = HomeState.NoKnownProblems(now)
        val vm = appViewModel { home }
        var asked by androidx.compose.runtime.mutableStateOf<FindingKey?>(null)
        show { com.aegida.app.ui.AppRoot(vm, asked = asked, onAsked = { asked = null }) }
        asked = gone.key
        compose.waitForIdle()
        vm.refresh(runCheck = false)
        reachable(Texts.Home.ALL_GOOD)
        home = HomeState.NeedsTaps(listOf(gone))
        vm.refresh(runCheck = false)
        reachable(Texts.Home.FIX_MY_PHONE)
        compose.onNodeWithText(Texts.Home.CONFIRM_YES).assertDoesNotExistOrFail()
    }

    private fun appViewModel(
        work: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined,
        guardians: Guardians = samWithAPhone,
        home: () -> HomeState,
    ): MainViewModel {
        withADialer()
        val loader = mockk<MainStateLoader>()
        val done = Setup("x", emptySet(), stepsDone = SetupStep.entries.size)
        coEvery { loader.load() } answers { MainState(loaded = true, now = now, setup = done, home = home(), guardians = guardians) }
        return MainViewModel(loader, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
            .also { it.work = work }
    }

    /** A phone with a dialer app: Robolectric has none unless told, and the call button is offered only where a call can be made (T4.10). */
    private fun withADialer() {
        val dial = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", "0", null))
        val info = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply { packageName = "com.example.dialer"; name = "Dialer" }
        }
        org.robolectric.Shadows.shadowOf(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().packageManager)
            .addResolveInfoForIntent(dial, info)
    }

    private fun showApp(home: HomeState, asked: FindingKey?, guardians: Guardians = samWithAPhone) {
        val vm = appViewModel(guardians = guardians) { home }
        show { com.aegida.app.ui.AppRoot(vm, asked = asked) }
    }

    @Test fun homeOffersShowMeNotFixMyPhoneForAWarningThatFixesNothing() { // Kevin, Oct 10
        val link = finding(Problem.LookalikeLink)
        var shown: Finding? = null
        show { HomeScreen(HomeState.NeedsTaps(listOf(link)), picture, ShownAt(now, zone, locale), { shown = it }) }
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).assertDoesNotExistOrFail()
        compose.onNodeWithText(Texts.Home.SHOW_ME).scrollIfInsideScroller().performClick()
        assertEquals(link, shown)
    }

    @Test fun homeOffersShowMeForANoisyAppToo() {
        var shown: Finding? = null
        show { HomeScreen(HomeState.NeedsTaps(listOf(noisy())), picture, ShownAt(now, zone, locale), { shown = it }) }
        compose.onNodeWithText(Texts.Home.SHOW_ME).scrollIfInsideScroller().performClick()
        assertEquals(noisy().key, shown!!.key)
    }

    @Test fun homeWithSeveralProblemsCountsThemAndStartsWithTheFirst() {
        val first = finding(Problem.ListenerOff, minutesOld = 60)
        var fixed: Finding? = null
        show { HomeScreen(HomeState.NeedsTaps(listOf(first, finding(Problem.WebsiteFilterOff))), picture, ShownAt(now, zone, locale), { fixed = it }) }
        reachable(Texts.Home.NEEDS_MANY.format(2))
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).scrollIfInsideScroller().performClick()
        assertEquals(first, fixed)
    }

    @Test fun homeThatCouldNotCheckNeverSaysAllGood() {
        show { HomeScreen(HomeState.CouldntCheck(now.minus(Duration.ofDays(2))), picture, ShownAt(now, zone, locale), {}, withNumber) }
        reachable(Texts.Home.COULDNT_CHECK)
        reachable(Texts.Home.CALL_A_GUARDIAN)
        compose.onNodeWithText(Texts.Home.ALL_GOOD).assertDoesNotExistOrFail()
    }

    @Test fun homeThatNeverCheckedSaysSo() {
        show { HomeScreen(HomeState.CouldntCheck(null), picture, ShownAt(now, zone, locale), {}) }
        reachable(Texts.Home.NEVER_CHECKED)
    }

    @Test fun herPictureIsOnTheScreenAndNamedForAScreenReader() {
        show { HomeScreen(HomeState.NoKnownProblems(now), picture, ShownAt(now, zone, locale), {}) }
        compose.onNodeWithContentDescription(Texts.Common.YOUR_PICTURE.format("Sunflower")).assertExists()
        compose.onNodeWithText(Texts.APP_NAME).assertExists()
    }

    /** Scenario WP17 A13 (findings #149): after her Yes to a website that sent fake warnings, she is told it stays hidden, and can close it. */
    @Test fun aYesToAWebsiteThatSentFakeWarningsTellsHerItStaysHidden() {
        var done = 0
        show { com.aegida.app.ui.KeptHiddenPage(picture) { done++ } }
        reachable("This website sent fake warnings before, so Aegida keeps hiding it.")
        compose.onNodeWithText(Texts.Common.DONE).scrollIfInsideScroller().performClick()
        assertEquals(1, done)
    }

    /** Home, then her Yes to a website question, through the real screens; [fakeOnRecord] is what FindingActions.keptHidden answers. */
    private fun tapYesToAWebsite(fakeOnRecord: Boolean): FindingActions {
        val question = Finding(FindingKey(DetectorId.PopupSpam, Problem.NewWebsiteNotifications, "https://news.example"), emptyMap(), now, now)
        val actions = mockk<FindingActions>()
        coEvery { actions.acceptChange(question.key) } returns true
        coEvery { actions.keptHidden(question.key) } returns fakeOnRecord
        val loader = mockk<MainStateLoader>()
        val done = Setup("x", emptySet(), stepsDone = SetupStep.entries.size)
        coEvery { loader.load() } returns MainState(loaded = true, now = now, setup = done, home = HomeState.NeedsTaps(listOf(question)))
        val vm = MainViewModel(loader, mockk(relaxed = true), mockk(relaxed = true), actions, mockk(relaxed = true)).also { it.work = Dispatchers.Unconfined }
        show { com.aegida.app.ui.AppRoot(vm) }
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).scrollIfInsideScroller().performClick()
        compose.onNodeWithText(Texts.Home.CONFIRM_YES).scrollIfInsideScroller().performClick()
        compose.waitForIdle()
        return actions
    }

    /** Scenario WP17 A13 (findings #151 b): a Yes to a site with a fake warning on record opens the page that says it stays hidden. */
    @Test fun aYesToAWebsiteWithAFakeOnRecordShowsThatItStaysHidden() {
        val actions = tapYesToAWebsite(fakeOnRecord = true)
        reachable(Texts.Home.SITE_KEPT_HIDDEN)
        coVerify { actions.acceptChange(any()) }
    }

    /** Scenario WP17 A14 (findings #151 b): a Yes to a site with no fake on record goes back to Home, with no "stays hidden" page. */
    @Test fun aYesToAWebsiteWithNoFakeOnRecordGoesBackWithoutTheKeptHiddenPage() {
        val actions = tapYesToAWebsite(fakeOnRecord = false)
        compose.onNodeWithText(Texts.Home.SITE_KEPT_HIDDEN).assertDoesNotExistOrFail()
        compose.onNodeWithText(Texts.Home.CONFIRM_YES).assertDoesNotExistOrFail()
        coVerify { actions.acceptChange(any()) }
    }

    // #171, WN39-WN42: the tab bar present. Robolectric measures text with stand-in glyph widths, so it wraps far fewer lines than the
    // emulator's 1080x2400 at 420 dpi (its Home at 200% passed while the real one needed a scroll); these tests therefore use a screen
    // too short for the words whatever they wrap to, and require the main button and its partner on screen above the bar without
    // scrolling. The emulator screenshots (build/emulator-checks/warning-names-2) show the real wrapping.
    private fun assertAboveTheTabBar(vararg texts: String) {
        val barTop = compose.onNodeWithText("History").fetchSemanticsNode().boundsInRoot.top
        texts.forEach {
            val node = compose.onNodeWithText(it)
            node.assertIsDisplayed()
            org.junit.Assert.assertTrue("$it sits under the tab bar", node.fetchSemanticsNode().boundsInRoot.bottom <= barTop)
        }
    }

    private fun listenerWarning(label: String = "Pixel Watch") = Finding(
        FindingKey(DetectorId.Drift, Problem.ListenerAdded, "org.helper"),
        mapOf(
            com.aegida.core.model.Evidence.APP_LABEL to label, com.aegida.core.model.Evidence.APP_SOURCE to "outside",
            com.aegida.core.model.Evidence.APP_ADDED to now.toEpochMilli().toString(),
        ),
        now, now,
    )

    private fun appWarning() = Finding(
        FindingKey(DetectorId.NewApp, Problem.NewAppOutsidePlay, "org.fixture"),
        mapOf(
            com.aegida.core.model.Evidence.APP_LABEL to "Fixture sideloaded", com.aegida.core.model.Evidence.APP_SOURCE to "outside",
            com.aegida.core.model.Evidence.APP_ADDED to now.toEpochMilli().toString(),
        ),
        now, now,
    )

    @Test @org.robolectric.annotation.Config(qualifiers = "w411dp-h400dp-420dpi")
    fun homeForANotificationReaderKeepsTheButtonAndCallAGuardianAboveTheTabBar() {
        showApp(HomeState.NeedsTaps(listOf(listenerWarning())), asked = null)
        assertAboveTheTabBar(Texts.Home.FIX_MY_PHONE, Texts.Home.CALL_A_GUARDIAN)
    }

    @Test @org.robolectric.annotation.Config(qualifiers = "w411dp-h400dp-420dpi")
    fun homeForANewAppKeepsTheButtonAndCallAGuardianAboveTheTabBar() {
        showApp(HomeState.NeedsTaps(listOf(appWarning())), asked = null)
        assertAboveTheTabBar(Texts.Home.FIX_MY_PHONE, Texts.Home.CALL_A_GUARDIAN)
    }

    @Test @org.robolectric.annotation.Config(qualifiers = "w411dp-h400dp-420dpi")
    fun homeForANewWebsiteKeepsTheButtonAndCallAGuardianAboveTheTabBar() {
        val site = Finding(
            FindingKey(DetectorId.PopupSpam, Problem.NewWebsiteNotifications, "https://news.example"),
            mapOf(com.aegida.core.model.Evidence.SITE to "https://news.example"), now, now,
        )
        showApp(HomeState.NeedsTaps(listOf(site)), asked = null)
        assertAboveTheTabBar(Texts.Home.FIX_MY_PHONE, Texts.Home.CALL_A_GUARDIAN)
    }

    @Test @org.robolectric.annotation.Config(qualifiers = "w411dp-h320dp-420dpi")
    fun theQuestionPageKeepsYesAndNoAboveTheTabBarAndItsClosingLineReachable() {
        val app = appWarning()
        showApp(HomeState.NeedsTaps(listOf(app)), asked = app.key)
        assertAboveTheTabBar(Texts.Home.CONFIRM_YES, Texts.Home.CONFIRM_NO)
        reachable(Texts.Home.CONFIRM_NO_HELP)
        val barTop = compose.onNodeWithText("History").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue(compose.onNodeWithText(Texts.Home.CONFIRM_NO_HELP).fetchSemanticsNode().boundsInRoot.bottom <= barTop)
    }

    /** WN43 (#171): a one-line fact ("Added today.") is centred like the lines above it on the question page, not left-aligned. */
    @Test @org.robolectric.annotation.Config(qualifiers = "w411dp-h914dp-420dpi")
    fun aOneLineFactOnTheQuestionPageIsCentredLikeTheLinesAboveIt() {
        val app = appWarning()
        showApp(HomeState.NeedsTaps(listOf(app)), asked = app.key)
        val line = compose.onNodeWithText("Added today.").fetchSemanticsNode().boundsInRoot
        val rootWidth = compose.onAllNodes(androidx.compose.ui.test.isRoot())[0].fetchSemanticsNode().boundsInRoot.width
        org.junit.Assert.assertEquals("centre of the line", rootWidth / 2, (line.left + line.right) / 2, 2f)
    }

    // ---- Release B, T4: nothing is offered that cannot ring ----

    private val samWithAPhone = Guardians.None.copy(firstGuardianName = "Sam", firstGuardianPhone = "555 0100")
    private val withNumber = GuardianReach("Sam", "555 0100", dialerExists = true)
    private val noNumber = GuardianReach("Sam", "", dialerExists = true)

    @Test fun t4_7_noPhoneNoKnownProblemsSaysAskSamWhereTheButtonWas() {
        show { HomeScreen(HomeState.NoKnownProblems(now), picture, ShownAt(now, zone, locale), {}, noNumber) }
        reachable(Texts.Home.NO_KNOWN_PROBLEMS)
        reachable("If you are ever unsure, ask Sam.")
        compose.onNodeWithText(Texts.Home.CALL_A_GUARDIAN).assertDoesNotExistOrFail()
    }

    @Test fun t4_16_noPhoneUnderAWarningOnlyTheBigButtonIsOffered() {
        show { HomeScreen(HomeState.NeedsTaps(listOf(finding(Problem.WebsiteFilterOff))), picture, ShownAt(now, zone, locale), {}, noNumber) }
        reachable(Texts.Home.FIX_MY_PHONE)
        compose.onNodeWithText(Texts.Home.CALL_A_GUARDIAN).assertDoesNotExistOrFail()
        compose.onNodeWithText("If you are ever unsure, ask Sam.").assertDoesNotExistOrFail()
    }

    @Test fun t4_8_noPhoneCouldntCheckSaysAskSamAndOffersNoButton() {
        show { HomeScreen(HomeState.CouldntCheck(now.minus(Duration.ofDays(2))), picture, ShownAt(now, zone, locale), {}, noNumber) }
        reachable("Aegida will try again soon. If this keeps happening, ask Sam.")
        compose.onNodeWithText(Texts.Home.CALL_A_GUARDIAN).assertDoesNotExistOrFail()
        compose.onNodeWithText(Texts.Home.COULDNT_CHECK_WHY).assertDoesNotExistOrFail()
    }

    @Test fun t4_8_noPhoneNeverCheckedSaysAskSamToo() {
        show { HomeScreen(HomeState.CouldntCheck(null), picture, ShownAt(now, zone, locale), {}, noNumber) }
        reachable(Texts.Home.NEVER_CHECKED)
        reachable("If you are ever unsure, ask Sam.")
        compose.onNodeWithText(Texts.Home.CALL_A_GUARDIAN).assertDoesNotExistOrFail()
    }

    @Test fun t4_15_anUnreachableGuardianWithNoPhoneShowsTheTellLineAndNoButtonAtAll() {
        show { HomeScreen(HomeState.NeedsTaps(listOf(finding(Problem.GuardianUnreachable))), picture, ShownAt(now, zone, locale), {}, noNumber) }
        reachable("Aegida couldn't reach Sam for a day. When you can, please tell Sam.")
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).assertDoesNotExistOrFail()
        compose.onNodeWithText(Texts.Home.GOT_IT).assertDoesNotExistOrFail()
        compose.onNodeWithText(Texts.Home.CALL_A_GUARDIAN).assertDoesNotExistOrFail()
    }

    @Test fun t4_15_anUnreachableGuardianWithAPhoneStillOffersFixMyPhoneToCall() {
        show {
            HomeScreen(HomeState.NeedsTaps(listOf(finding(Problem.GuardianUnreachable))), picture, ShownAt(now, zone, locale), {}, withNumber)
        }
        reachable(Problem.GuardianUnreachable.forMom)
        reachable(Texts.Home.FIX_MY_PHONE)
    }

    @Test fun t4_10_aPhoneWithNoDialerShowsTheNumberAsWordsNeverAButton() {
        show { HomeScreen(HomeState.NoKnownProblems(now), picture, ShownAt(now, zone, locale), {}, withNumber.copy(dialerExists = false)) }
        reachable("Your guardian's phone number: 555 0100")
        compose.onNodeWithText(Texts.Home.CALL_A_GUARDIAN).assertDoesNotExistOrFail()
    }

    @Test fun t4_11_aGuardianNameShapedLikeAWebAddressIsNotShownAsOneOnHome() {
        show { HomeScreen(HomeState.NoKnownProblems(now), picture, ShownAt(now, zone, locale), {}, GuardianReach("pay-now.example.com", "", true)) }
        compose.onNodeWithText("pay-now.example.com", substring = true).assertDoesNotExistOrFail()
    }

    @Test fun t4_16_theAppWithNoPhoneSavedShowsNoCallButtonOnHomeOrTheQuestionPage() {
        val none = Guardians.None.copy(firstGuardianName = "Sam")
        showApp(HomeState.NeedsTaps(listOf(appWarning())), asked = null, guardians = none)
        compose.onNodeWithText(Texts.Home.CALL_A_GUARDIAN).assertDoesNotExistOrFail()
    }

    @Test fun t4_17_theQuestionPageWithNoPhoneSaysAskSamNotCall() {
        val none = Guardians.None.copy(firstGuardianName = "Sam")
        val app = appWarning()
        showApp(HomeState.NeedsTaps(listOf(app)), asked = app.key, guardians = none)
        reachable("If you did not do this, change it back, or ask Sam.")
        compose.onNodeWithText(Texts.Home.CONFIRM_NO_HELP).assertDoesNotExistOrFail()
    }
}
