package com.aegida.app

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.aegida.app.ui.MainContent
import com.aegida.app.ui.Tab
import com.aegida.app.ui.WhatYouSeePage
import com.aegida.app.ui.openPlayPage
import com.aegida.alerts.FindingActions
import com.aegida.core.model.HomeState
import com.aegida.core.model.SafetyPicture
import com.aegida.core.model.Setup
import com.aegida.core.net.PublicPages
import com.aegida.core.text.Texts
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/** T2 ("What you will see", from My settings) and T11's fallbacks (the Play Store app, then the browser, then nothing). */
@RunWith(ParameterizedRobolectricTestRunner::class)
class WhatYouSeeAndRateTest(dark: Boolean, fontScale: Float) : ScreenTestBase(dark, fontScale) {

    private fun context(resolves: (Intent) -> Boolean, opened: MutableList<Intent>): Context =
        object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
            override fun startActivity(intent: Intent) {
                if (!resolves(intent)) throw ActivityNotFoundException()
                opened += intent
            }
        }

    @Test fun ratingOpensThePlayStoreAppWhenThePhoneHasIt() { // T11.1, T11.8
        val opened = mutableListOf<Intent>()
        assertTrue(context({ true }, opened).openPlayPage())
        assertEquals(listOf("market://details?id=com.aegida.app"), opened.map { it.dataString })
    }

    @Test fun ratingFallsBackToTheSamePageInTheBrowserWithoutThePlayStoreApp() { // T11.2
        val opened = mutableListOf<Intent>()
        assertTrue(context({ it.`package` == null }, opened).openPlayPage())
        assertEquals(listOf(PublicPages.PLAY_STORE), opened.map { it.dataString })
    }

    @Test fun ratingWhenNeitherCanOpenDoesNothingAndShowsNoError() { // T11.3
        val opened = mutableListOf<Intent>()
        assertFalse(context({ false }, opened).openPlayPage())
        assertTrue(opened.isEmpty())
    }

    // The "All set." heading on the last setup step is asserted in SetupFlowTest (setupFromFilterToDone); this page is the My settings copy.
    @Test fun theWhatYouWillSeePageShowsHerPictureTheLinesAndDoneAtTheBottomUnderItsOwnHeading() { // T2.1, T2.11
        var done = 0
        show { WhatYouSeePage(picture, noticesOn = true) { done++ } }
        reachable(Texts.Setup.SEE_TITLE)
        compose.onNodeWithText(Texts.Setup.DONE_TITLE).assertDoesNotExist()
        // her picture twice: the header and the big tile
        compose.onAllNodesWithContentDescription(Texts.Common.YOUR_PICTURE.format(SafetyPicture.Sunflower.label)).assertCountEquals(2)
        Texts.Setup.seeLines(true, true).forEach { reachable(it) }
        compose.onNodeWithText(Texts.Common.DONE).performClick()
        assertEquals(1, done)
    }

    @Test fun withNoticesOffThePageNeverPromisesANotice() { // T2.4
        show { WhatYouSeePage(picture, noticesOn = false) {} }
        reachable(Texts.Setup.SEE_NOTICE_OFF)
        compose.onNodeWithText(Texts.Setup.SEE_NOTICE).assertDoesNotExist()
        compose.onNodeWithText(Texts.Setup.SEE_NOTICE_PLAIN).assertDoesNotExist()
    }

    @Test fun withNoPictureThereIsNoTileAndTheLineSaysANoticeFromAegida() { // T2.5
        show { WhatYouSeePage(null, noticesOn = true) {} }
        reachable(Texts.Setup.SEE_NOTICE_PLAIN)
        compose.onNodeWithText(Texts.Setup.SEE_NOTICE).assertDoesNotExist()
        compose.onAllNodesWithContentDescription(Texts.Common.YOUR_PICTURE.format(SafetyPicture.Sunflower.label)).assertCountEquals(0)
    }

    @Test fun doneOnTheMySettingsCopyOnlyGoesBackAndNeverFinishesSetupOrTakesABaseline() { // T2.2, T2.3
        val commands = mockk<SetupCommands>(relaxed = true)
        val loader = mockk<MainStateLoader>()
        coEvery { loader.load() } returns MainState()
        val vm = MainViewModel(loader, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), commands)
            .also { it.work = Dispatchers.Unconfined } // the tap runs its work at once, so a wrong finishStep is seen below, not raced
        show { MainContent(MainState(loaded = true, setup = Setup(null, emptySet(), pictureId = picture).copy(stepsDone = 99)), vm) }
        compose.onNodeWithText(Tab.Settings.label).performClick()
        compose.onNodeWithText(Texts.Setup.SEE_TITLE).performScrollTo().performClick()
        reachable(Texts.Setup.SEE_QUIET)
        compose.onNodeWithText(Texts.Common.DONE).performClick()
        compose.onNodeWithText(Texts.Setup.SEE_QUIET).assertDoesNotExist()
        reachable(Texts.Settings.NAME)
        coVerify(exactly = 0) { commands.finishStep(any(), any()) }
    }

    private fun mySettingsPage(state: MainState, vm: MainViewModel) {
        show { MainContent(state, vm) }
        compose.onNodeWithText(Tab.Settings.label).performClick()
        compose.onNodeWithText(Texts.Setup.SEE_TITLE).performScrollTo().performClick()
    }

    private fun viewModel(actions: FindingActions, commands: SetupCommands): MainViewModel {
        val loader = mockk<MainStateLoader>()
        coEvery { loader.load() } returns MainState()
        return MainViewModel(loader, mockk(relaxed = true), mockk(relaxed = true), actions, commands).also { it.work = Dispatchers.Unconfined }
    }

    @Test fun afterSheChangedHerPictureThePageShowsTheNewOneNotTheOneFromSetup() { // T2.6
        val owl = SafetyPicture.Owl
        val now = Setup(null, emptySet(), pictureId = owl.id).copy(stepsDone = 99)
        mySettingsPage(MainState(loaded = true, setup = now), viewModel(mockk(relaxed = true), mockk(relaxed = true)))
        compose.onAllNodesWithContentDescription(Texts.Common.YOUR_PICTURE.format(owl.label)).assertCountEquals(2)
        compose.onAllNodesWithContentDescription(Texts.Common.YOUR_PICTURE.format(SafetyPicture.Sunflower.label)).assertCountEquals(0)
    }

    @Test fun openingThePageFromMySettingsWhileAWarningIsOpenResolvesNothingAndLeavesHomeAsItWas() { // T2.7
        val actions = mockk<FindingActions>(relaxed = true)
        val commands = mockk<SetupCommands>(relaxed = true)
        val warned = MainState(
            loaded = true, setup = Setup(null, emptySet(), pictureId = picture).copy(stepsDone = 99), home = HomeState.NeedsTaps(listOf(noisy())),
        )
        mySettingsPage(warned, viewModel(actions, commands))
        reachable(Texts.Setup.SEE_QUIET)
        compose.onNodeWithText(Texts.Common.DONE).performClick()
        compose.onNodeWithText(Tab.Home.label).performClick()
        compose.onNodeWithText(Texts.Home.NO_KNOWN_PROBLEMS).assertDoesNotExist()
        confirmVerified(actions, commands)
    }
}
