package com.aegida.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.aegida.app.ui.setup.AboutStep
import com.aegida.app.ui.setup.AccessStatus
import com.aegida.app.ui.setup.AccessStep
import com.aegida.app.ui.setup.AlertsStep
import com.aegida.app.ui.setup.FilterStep
import com.aegida.app.ui.setup.InviteStep
import com.aegida.app.ui.setup.LocalSetupBack
import com.aegida.app.ui.setup.PictureStep
import com.aegida.app.ui.setup.StepPosition
import com.aegida.app.ui.setup.WelcomeScreen
import com.aegida.core.alert.Guardians
import com.aegida.core.model.SafetyPicture
import com.aegida.core.model.Setup
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/** The first-run steps, in light and dark at 100% and 200% font. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class SetupScreensTest(dark: Boolean, fontScale: Float) : ScreenTestBase(dark, fontScale) {

    @Test fun theWelcomeScreenSaysWhatAegidaIsBeforeAnyQuestionAndOnlyThenStarts() {
        var started = 0
        show { WelcomeScreen(onStart = { started++ }) }
        reachable(Texts.Setup.WELCOME_TITLE)
        reachable(Texts.Setup.WELCOME_BODY)
        assertEquals(0, started)
        compose.onNodeWithText(Texts.Setup.WELCOME_START).performScrollTo().performClick()
        assertEquals(1, started)
    }

    /**
     * Kevin, Oct 9: the second screen "looks messy still": a long intro, her name, a second heading, a sentence, three boxes.
     * Now: the title, one question for her name, one sentence about the guardian, and their name, email and phone, in that order.
     */
    @Test fun aboutStepIsHerNameThenOneSentenceThenTheGuardiansThreeDetailsAndNothingElse() {
        show { AboutStep(StepPosition(null, 1, 6), Guardians.None) { _, _, _, _ -> } }
        compose.onNodeWithText(Texts.Setup.ABOUT_TITLE).assertIsDisplayed()
        compose.onNodeWithText("How shall I address you?").assertIsDisplayed()
        compose.onNodeWithText("Aegida looks out for scams", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Who should we tell?").assertDoesNotExist()
        val tops = listOf(Texts.Setup.YOUR_NAME, Texts.Setup.GUARDIAN_BODY, Texts.Setup.GUARDIAN_NAME, Texts.Setup.GUARDIAN_EMAIL, Texts.Setup.GUARDIAN_PHONE)
            .map { compose.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y }
        assertEquals("her name, the sentence, then name, email and phone", tops.sorted(), tops)
    }

    /** Kevin, Oct 9: with the keyboard up she had to hunt for the next box. Next moves down the boxes; Done on the last closes the keyboard. */
    @Test fun theKeyboardsNextButtonMovesDownTheBoxesAndDoneOnTheLastOneLetsGo() {
        show { AboutStep(StepPosition(null, 1, 6), Guardians.None) { _, _, _, _ -> } }
        compose.onNodeWithText(Texts.Setup.YOUR_NAME).performScrollTo().performClick()
        compose.onNodeWithText(Texts.Setup.YOUR_NAME).performImeAction()
        compose.onNodeWithText(Texts.Setup.GUARDIAN_NAME).assertIsFocused()
        compose.onNodeWithText(Texts.Setup.GUARDIAN_NAME).performImeAction()
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).assertIsFocused()
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).performImeAction()
        compose.onNodeWithText(Texts.Setup.GUARDIAN_PHONE).assertIsFocused()
        compose.onNodeWithText(Texts.Setup.GUARDIAN_PHONE).performImeAction()
        compose.onNodeWithText(Texts.Setup.GUARDIAN_PHONE).assertIsNotFocused()
    }

    @Test fun aboutStepNeedsAValidNameGuardianAndEmailBeforeContinuing() {
        var saved: List<String>? = null
        show { AboutStep(StepPosition(null, 1, 6), Guardians.None) { a, b, c, d -> saved = listOf(a, b, c, d) } }
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(Texts.Setup.YOUR_NAME).performScrollTo().performTextInput("Ruth")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_NAME).performScrollTo().performTextInput("Kevin")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).performScrollTo().performTextInput("not an email")
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).performScrollTo().performTextInput("kevin@example.org")
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().assertIsEnabled().performClick()
        assertTrue(saved!![2].endsWith("kevin@example.org"))
    }

    @Test fun pictureStepShowsAllTenAndContinuesOnlyAfterOneIsPicked() {
        var chosen: SafetyPicture? = null
        show { PictureStep(StepPosition(null, 2, 6), null, { chosen = it }, onContinue = {}) }
        SafetyPicture.entries.forEach { compose.onNodeWithContentDescription(it.label).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Owl").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Owl").assertIsSelected()
        assertEquals(SafetyPicture.Owl, chosen)
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().assertIsEnabled()
    }

    @Test fun inviteStepOnlyLetsHerSayItWasSentAfterTheEmailWasWritten() {
        var written = 0
        show { InviteStep(StepPosition(picture, 3, 6), "Kevin", noEmailApp = false, onWrite = { written++ }, onSent = {}) }
        compose.onNodeWithText(Texts.Setup.INVITE_SENT).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(Texts.Setup.INVITE_WRITE).performScrollTo().performClick()
        assertEquals(1, written)
        compose.onNodeWithText(Texts.Setup.INVITE_SENT).performScrollTo().assertIsEnabled()
    }

    @Test fun inviteStepSaysSoWhenThereIsNoEmailApp() {
        show { InviteStep(StepPosition(picture, 3, 6), "Kevin", noEmailApp = true, onWrite = {}, onSent = {}) }
        reachable(Texts.Setup.INVITE_NO_EMAIL_APP)
    }

    @Test fun alertsStepOffersYesAndNoWithAnExampleOfWhatIsSent() {
        val answers = mutableListOf<Boolean>()
        show { AlertsStep(StepPosition(picture, 4, 6)) { answers += it } }
        reachable(Texts.Setup.ALERTS_BODY)
        compose.onNodeWithText(Texts.Setup.ALERTS_YES).performScrollTo().performClick()
        compose.onNodeWithText(Texts.Setup.ALERTS_NO).performScrollTo().performClick()
        assertEquals(listOf(true, false), answers)
    }

    @Test fun accessStepShowsWhatIsDoneAndOnlyContinuesFullyWhenBothAreOn() {
        show { AccessStep(StepPosition(picture, 5, 6), status = AccessStatus(listenerOn = true, bannersOn = false), onOpenAccess = {},
            onAllowBanners = {}, onContinue = {}) }
        reachable(Texts.Setup.ACCESS_DONE)
        reachable(Texts.Setup.ACCESS_ALLOW_BANNERS)
        reachable(Texts.Common.NOT_NOW)
    }

    /** Findings #65: skipping the step that lets Aegida see notifications turns off its main job, so she is asked first. */
    @Test fun skippingTheNotificationStepAsksAreYouSureFirst() {
        var continued = 0
        show { AccessStep(StepPosition(picture, 5, 6), status = AccessStatus(listenerOn = false, bannersOn = false), onOpenAccess = {},
            onAllowBanners = {}, onContinue = { continued++ }) }
        compose.onNodeWithText(Texts.Common.NOT_NOW).performScrollTo().performClick()
        compose.onNodeWithText(Texts.Setup.ACCESS_SKIP_TITLE).assertIsDisplayed()
        compose.onNodeWithText(Texts.Setup.ACCESS_SKIP_BODY).assertIsDisplayed()
        assertEquals("nothing happens until she answers", 0, continued)
        compose.onNodeWithText(Texts.Setup.ACCESS_SKIP_BACK).performClick()
        compose.onNodeWithText(Texts.Setup.ACCESS_SKIP_TITLE).assertDoesNotExist()
        assertEquals(0, continued)
        compose.onNodeWithText(Texts.Common.NOT_NOW).performScrollTo().performClick()
        compose.onNodeWithText(Texts.Setup.ACCESS_SKIP_ANYWAY).performClick()
        assertEquals(1, continued)
    }

    @Test fun accessStepIsTwoNumberedPartsAndTheSecondWaitsForTheFirst() { // findings #74: two blue buttons, which first?
        var banners = 0
        var access = 0
        show { AccessStep(StepPosition(picture, 5, 6), status = AccessStatus(listenerOn = false, bannersOn = false), onOpenAccess = { access++ },
            onAllowBanners = { banners++ }, onContinue = {}) }
        reachable(Texts.Setup.ACCESS_PART_1)
        reachable(Texts.Setup.ACCESS_PART_2)
        reachable(Texts.Setup.ACCESS_FIRST)
        compose.onNodeWithText(Texts.Setup.ACCESS_ALLOW_BANNERS).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(Texts.Setup.ACCESS_OPEN).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, access)
    }

    @Test fun eachFinishedPartShowsAGreenTickRowSoSheKnowsItWorked() {
        show { AccessStep(StepPosition(picture, 5, 6), status = AccessStatus(listenerOn = true, bannersOn = true), onOpenAccess = {},
            onAllowBanners = {}, onContinue = {}) }
        compose.onNode(androidx.compose.ui.test.hasContentDescription(Texts.Setup.ACCESS_DONE)).performScrollTo().assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasContentDescription(Texts.Setup.BANNERS_DONE)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().assertIsEnabled()
    }

    @Test fun filterStepReadsAsNotOnYetAndThenOnOnlyWhenAegidaHasReadItBack() {
        show { FilterStep(StepPosition(picture, 6, 6), filterOn = false, onOpenSettings = {}, onCheck = {}, onContinue = {}) }
        reachable(Texts.Setup.FILTER_NOT_YET)
        reachable(Texts.Setup.FILTER_COPY_AND_OPEN)
        reachable(Texts.Setup.FILTER_CHECK)
    }

    @Test fun theOneBigButtonOnTheFilterStepIsTheCopyAndOpenButton() {
        var opened = 0
        show { FilterStep(StepPosition(picture, 6, 6), filterOn = false, onOpenSettings = { opened++ }, onCheck = {}, onContinue = {}) }
        compose.onNodeWithText(Texts.Setup.FILTER_COPY_AND_OPEN).performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test fun whenWebsiteProtectionIsAlreadyOnTheStepIsJustAGreenTickAndContinue() { // findings #74: a wall of text for something already done
        var continued = 0
        show { FilterStep(StepPosition(picture, 6, 6), filterOn = true, onOpenSettings = {}, onCheck = {}, onContinue = { continued++ }) }
        compose.onNode(androidx.compose.ui.test.hasContentDescription(Texts.Setup.FILTER_ON)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(Texts.Setup.FILTER_BODY).assertDoesNotExistOrFail()
        compose.onNodeWithText(Texts.Setup.FILTER_COPY_AND_OPEN).assertDoesNotExistOrFail()
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().performClick()
        assertEquals(1, continued)
    }

    @Test fun everyFirstRunStepAfterTheFirstHasABackButtonThatGoesBack() {
        var backs = 0
        show {
            CompositionLocalProvider(LocalSetupBack provides { backs++ }) {
                InviteStep(StepPosition(picture, 3, 6), "Sam", noEmailApp = false, onWrite = {}, onSent = {})
            }
        }
        compose.onNodeWithText(Texts.Common.BACK).performScrollTo().performClick()
        assertEquals(1, backs)
    }

    @Test fun aStepWithNowhereToGoBackToHasNoBackButton() {
        show { InviteStep(StepPosition(picture, 3, 6), "Sam", noEmailApp = false, onWrite = {}, onSent = {}) }
        compose.onNodeWithText(Texts.Common.BACK).assertDoesNotExistOrFail()
    }

    @Test fun filterStepOnShowsItAndContinues() {
        var continued = 0
        show { FilterStep(StepPosition(picture, 6, 6), filterOn = true, onOpenSettings = {}, onCheck = {}, onContinue = { continued++ }) }
        reachable(Texts.Setup.FILTER_ON)
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().performClick()
        assertEquals(1, continued)
    }
}
