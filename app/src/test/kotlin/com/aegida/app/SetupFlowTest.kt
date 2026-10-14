package com.aegida.app

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.aegida.app.ui.SetupFlow
import com.aegida.core.model.SafetyPicture
import com.aegida.core.model.SetupStep
import com.aegida.core.text.Texts
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * First-run setup driven through the real SetupFlow with the ViewModel's work dispatcher queued, as on a phone where the save runs
 * on a background thread after the screen has already moved on (finding #123, docs/scenarios/first-run.md FR7).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
class SetupFlowTest(dark: Boolean, fontScale: Float) : ScreenTestBase(dark, fontScale) {

    private val scheduler = TestCoroutineScheduler()
    private val finished = mutableListOf<SetupStep>()

    private fun viewModel(): MainViewModel {
        val commands = mockk<SetupCommands>(relaxed = true)
        coEvery { commands.finishStep(any(), any()) } coAnswers { finished += firstArg<SetupStep>() }
        val loader = mockk<MainStateLoader>()
        coEvery { loader.load() } returns MainState()
        return MainViewModel(loader, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), commands)
            .also { it.work = StandardTestDispatcher(scheduler) }
    }

    @Test fun eachContinueSavesTheStepSheJustFinishedNotTheOneAfterIt() {
        val vm = viewModel()
        show { SetupFlow(MainState(), vm) }
        compose.onNodeWithText(Texts.Setup.WELCOME_START).performScrollTo().performClick()
        compose.onNodeWithText(Texts.Setup.YOUR_NAME).performScrollTo().performTextInput("Ruth")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_NAME).performScrollTo().performTextInput("Kevin")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).performScrollTo().performTextInput("kevin@example.org")
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().performClick()
        compose.waitForIdle()
        scheduler.advanceUntilIdle() // the save runs only now, after the screen moved on to Picture
        assertEquals(listOf(SetupStep.About), finished)
        compose.onNodeWithContentDescription(SafetyPicture.Owl.label).performScrollTo().performClick()
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().performClick()
        compose.waitForIdle()
        scheduler.advanceUntilIdle()
        assertEquals(listOf(SetupStep.About, SetupStep.Picture), finished)
    }
}
