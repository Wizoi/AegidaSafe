package com.aegida.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.aegida.app.ui.HomeScreen
import com.aegida.app.ui.ShownAt
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
        show { HomeScreen(HomeState.NoKnownProblems(now.minus(Duration.ofHours(3))), picture, ShownAt(now, zone, locale), {}, { called++ }) }
        reachable(Texts.Home.ALL_GOOD)
        reachable(Texts.Home.NO_KNOWN_PROBLEMS)
        compose.onNodeWithText(Texts.Home.CALL_A_GUARDIAN).performScrollTo().performClick()
        assertEquals(1, called)
    }

    @Test fun homeWithOneProblemShowsItsSentenceAndABigFixButton() {
        val dns = finding(Problem.WebsiteFilterOff)
        var fixed: Finding? = null
        show { HomeScreen(HomeState.NeedsTaps(listOf(dns)), picture, ShownAt(now, zone, locale), { fixed = it }, {}) }
        reachable(Texts.Home.NEEDS_ONE)
        reachable(Problem.WebsiteFilterOff.forMom)
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).performScrollTo().performClick()
        assertEquals(dns, fixed)
        reachable(Texts.Home.CALL_A_GUARDIAN)
    }

    /** WN1 (#164, Kevin's report): a new website's question names the website on Home, under the sentence. */
    @Test fun homeNamesTheWebsiteANewWebsiteQuestionIsAbout() {
        val site = Finding(
            FindingKey(DetectorId.PopupSpam, Problem.NewWebsiteNotifications, "https://news.example"),
            mapOf(com.aegida.core.model.Evidence.SITE to "https://news.example"), now, now,
        )
        show { HomeScreen(HomeState.NeedsTaps(listOf(site)), picture, ShownAt(now, zone, locale), {}, {}) }
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
        show { HomeScreen(HomeState.NeedsTaps(listOf(listener)), picture, ShownAt(now, zone, locale), {}, {}) }
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).assertIsDisplayed()
        reachable("The app calls itself: “${"W".repeat(40)}…”")
        reachable("It came from outside the Play Store.")
        reachable("Added today.")
    }

    /** WN14: a setting warning has no extra line. */
    @Test fun homeShowsNoExtraLineForASettingWarning() {
        show { HomeScreen(HomeState.NeedsTaps(listOf(finding(Problem.WebsiteFilterOff))), picture, ShownAt(now, zone, locale), {}, {}) }
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

    private fun showApp(home: HomeState, asked: FindingKey) {
        val loader = mockk<MainStateLoader>()
        val done = Setup("x", emptySet(), stepsDone = SetupStep.entries.size)
        coEvery { loader.load() } returns MainState(loaded = true, now = now, setup = done, home = home)
        val vm = MainViewModel(loader, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
            .also { it.work = Dispatchers.Unconfined }
        show { com.aegida.app.ui.AppRoot(vm, asked = asked) }
    }

    @Test fun homeOffersShowMeNotFixMyPhoneForAWarningThatFixesNothing() { // Kevin, Oct 10
        val link = finding(Problem.LookalikeLink)
        var shown: Finding? = null
        show { HomeScreen(HomeState.NeedsTaps(listOf(link)), picture, ShownAt(now, zone, locale), { shown = it }, {}) }
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).assertDoesNotExistOrFail()
        compose.onNodeWithText(Texts.Home.SHOW_ME).performScrollTo().performClick()
        assertEquals(link, shown)
    }

    @Test fun homeOffersShowMeForANoisyAppToo() {
        var shown: Finding? = null
        show { HomeScreen(HomeState.NeedsTaps(listOf(noisy())), picture, ShownAt(now, zone, locale), { shown = it }, {}) }
        compose.onNodeWithText(Texts.Home.SHOW_ME).performScrollTo().performClick()
        assertEquals(noisy().key, shown!!.key)
    }

    @Test fun homeWithSeveralProblemsCountsThemAndStartsWithTheFirst() {
        val first = finding(Problem.ListenerOff, minutesOld = 60)
        var fixed: Finding? = null
        show { HomeScreen(HomeState.NeedsTaps(listOf(first, finding(Problem.WebsiteFilterOff))), picture, ShownAt(now, zone, locale), { fixed = it }, {}) }
        reachable(Texts.Home.NEEDS_MANY.format(2))
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).performScrollTo().performClick()
        assertEquals(first, fixed)
    }

    @Test fun homeThatCouldNotCheckNeverSaysAllGood() {
        show { HomeScreen(HomeState.CouldntCheck(now.minus(Duration.ofDays(2))), picture, ShownAt(now, zone, locale), {}, {}) }
        reachable(Texts.Home.COULDNT_CHECK)
        reachable(Texts.Home.CALL_A_GUARDIAN)
        compose.onNodeWithText(Texts.Home.ALL_GOOD).assertDoesNotExistOrFail()
    }

    @Test fun homeThatNeverCheckedSaysSo() {
        show { HomeScreen(HomeState.CouldntCheck(null), picture, ShownAt(now, zone, locale), {}, {}) }
        reachable(Texts.Home.NEVER_CHECKED)
    }

    @Test fun herPictureIsOnTheScreenAndNamedForAScreenReader() {
        show { HomeScreen(HomeState.NoKnownProblems(now), picture, ShownAt(now, zone, locale), {}, {}) }
        compose.onNodeWithContentDescription(Texts.Common.YOUR_PICTURE.format("Sunflower")).assertExists()
        compose.onNodeWithText(Texts.APP_NAME).assertExists()
    }

    /** Scenario WP17 A13 (findings #149): after her Yes to a website that sent fake warnings, she is told it stays hidden, and can close it. */
    @Test fun aYesToAWebsiteThatSentFakeWarningsTellsHerItStaysHidden() {
        var done = 0
        show { com.aegida.app.ui.KeptHiddenPage(picture) { done++ } }
        reachable("This website sent fake warnings before, so Aegida keeps hiding it.")
        compose.onNodeWithText(Texts.Common.DONE).performScrollTo().performClick()
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
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).performScrollTo().performClick()
        compose.onNodeWithText(Texts.Home.CONFIRM_YES).performScrollTo().performClick()
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
}
