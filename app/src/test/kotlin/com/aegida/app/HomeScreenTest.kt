package com.aegida.app

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.aegida.app.ui.HomeScreen
import com.aegida.app.ui.ShownAt
import com.aegida.core.model.Finding
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
}
