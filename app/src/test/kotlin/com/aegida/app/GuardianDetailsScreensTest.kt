package com.aegida.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.aegida.app.ui.ContactPickerUi
import com.aegida.app.ui.LocalContactPickerUi
import com.aegida.app.ui.SettingsActions
import com.aegida.app.ui.SettingsContent
import com.aegida.app.ui.SettingsScreen
import com.aegida.app.ui.setup.AboutStep
import com.aegida.app.ui.setup.ChangeGuardianScreen
import com.aegida.app.ui.setup.FixNamesScreen
import com.aegida.app.ui.setup.GuardianPhoneScreen
import com.aegida.app.ui.setup.StepPosition
import com.aegida.core.alert.ContactPick
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.PickedContact
import com.aegida.core.model.Setup
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/** Release B part 2: the contact picker (T4.2-T4.23), the phone row and "Fix a spelling" (T4.24, T5.1-T5.4, T5.16). */
@RunWith(ParameterizedRobolectricTestRunner::class)
class GuardianDetailsScreensTest(dark: Boolean, fontScale: Float) : ScreenTestBase(dark, fontScale) {

    private class FakePicker(override val available: Boolean = true, var result: ContactPick = ContactPick.Cancelled) : ContactPickerUi {
        var opened = 0
        override fun open(onResult: (ContactPick) -> Unit) { opened++; onResult(result) }
    }

    private val sam = ContactPick.Picked(PickedContact("+1 555 010 0199", "Sam"))

    private fun withPicker(picker: ContactPickerUi, content: @androidx.compose.runtime.Composable () -> Unit) =
        show { CompositionLocalProvider(LocalContactPickerUi provides picker) { content() } }

    private fun pick() = compose.onNodeWithText(Texts.Setup.PICK_CONTACT).performScrollTo().performClick()

    private fun about(onContinue: (List<String>) -> Unit, initial: Guardians = Guardians.None) =
        @androidx.compose.runtime.Composable { AboutStep(StepPosition(null, 1, 6), initial) { a, b, c, d -> onContinue(listOf(a, b, c, d)) } }

    @Test fun t4_2_pickingAContactInSetupFillsTheNumberAndTheNameWhileTheNameIsEmpty() {
        var saved: List<String>? = null
        withPicker(FakePicker(result = sam), about({ saved = it }))
        compose.onNodeWithText(Texts.Setup.YOUR_NAME).performScrollTo().performTextInput("Ruth")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).performScrollTo().performTextInput("sam@example.org")
        pick()
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().performClick()
        assertEquals(listOf("Ruth", "Sam", "sam@example.org", "+1 555 010 0199"), saved)
    }

    @Test fun t4_2_pickingAContactInSetupKeepsAGuardianNameAlreadyTyped() {
        var saved: List<String>? = null
        withPicker(FakePicker(result = sam), about({ saved = it }))
        compose.onNodeWithText(Texts.Setup.YOUR_NAME).performScrollTo().performTextInput("Ruth")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_NAME).performScrollTo().performTextInput("Samuel")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).performScrollTo().performTextInput("sam@example.org")
        pick()
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().performClick()
        assertEquals(listOf("Ruth", "Samuel", "sam@example.org", "+1 555 010 0199"), saved)
    }

    @Test fun t4_3_backingOutOfTheContactListLeavesTheFieldsAsTheyWere() {
        val picker = FakePicker(result = ContactPick.Cancelled)
        withPicker(picker, about({}))
        pick()
        assertEquals(1, picker.opened)
        compose.onNodeWithText(Texts.Setup.NO_CONTACT_NUMBER).assertDoesNotExistOrFail()
    }

    @Test fun t4_18_aContactWithNoUsableNumberSaysSoAndLeavesTheTypedNumber() {
        var saved: List<String>? = null
        withPicker(FakePicker(result = ContactPick.NoNumber), about({ saved = it }))
        compose.onNodeWithText(Texts.Setup.YOUR_NAME).performScrollTo().performTextInput("Ruth")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_NAME).performScrollTo().performTextInput("Sam")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).performScrollTo().performTextInput("sam@example.org")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_PHONE).performScrollTo().performTextInput("555 0100")
        pick()
        reachable("That contact has no phone number. You can type it below.")
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().performClick()
        assertEquals("555 0100", saved!![3])
    }

    @Test fun t4_21_withNoContactListOnThePhoneThereIsNoPickButtonOnlyTheTypedField() {
        withPicker(FakePicker(available = false), about({}))
        compose.onNodeWithText(Texts.Setup.PICK_CONTACT).assertDoesNotExistOrFail()
        reachable(Texts.Setup.GUARDIAN_PHONE)
    }

    // ---- My settings: the phone row and its page ----

    private fun settings(guardians: Guardians, onChangePhone: () -> Unit = {}, onFix: () -> Unit = {}) = show {
        SettingsScreen(
            SettingsContent(Setup(null, emptySet(), pictureId = picture), guardians, filterOn = true, listsUpdatedAt = null, zone = zone, locale = locale),
            SettingsActions(
                onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = {}, onHowItWorks = {},
                onChangePhone = onChangePhone, onFixSpelling = onFix,
            ),
        )
    }

    private val ruth = Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Sam")

    @Test fun t4_24_mySettingsShowsSamsPhoneWithAddWhenThereIsNone() {
        var opened = 0
        settings(ruth, onChangePhone = { opened++ })
        reachable("Sam's phone")
        compose.onNodeWithText(Texts.Settings.ADD).performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test fun t4_24_mySettingsShowsTheSavedPhoneWithChange() {
        settings(ruth.copy(firstGuardianPhone = "555 0100"))
        reachable("Sam's phone")
        reachable("555 0100")
        compose.onNodeWithText(Texts.Settings.ADD).assertDoesNotExistOrFail()
    }

    @Test fun t5_1_mySettingsOffersFixASpellingForTheNames() {
        var opened = 0
        settings(ruth, onFix = { opened++ })
        compose.onNodeWithText(Texts.Settings.FIX_SPELLING).performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test fun t4_23_pickingInMySettingsSavesOnlyTheNumberAndNeverAName() {
        var saved: String? = null
        withPicker(FakePicker(result = sam)) { GuardianPhoneScreen(picture, "Samuel", "", alertsOn = true) { saved = it } }
        pick()
        compose.onNodeWithText(Texts.Common.SAVE).performScrollTo().performClick()
        assertEquals("+1 555 010 0199", saved)
    }

    @Test fun t4_24_theNumberCanBeTypedOrClearedAndSaved() {
        var saved: String? = null
        withPicker(FakePicker()) { GuardianPhoneScreen(picture, "Sam", "555 0100", alertsOn = true) { saved = it } }
        compose.onNodeWithText(Texts.Setup.GUARDIAN_PHONE).performScrollTo().performTextClearance()
        compose.onNodeWithText(Texts.Common.SAVE).performScrollTo().assertIsEnabled().performClick()
        assertEquals("", saved)
    }

    @Test fun k7_theNoteThatSamWillBeToldShowsOnlyWhileAlertsAreOnAndNeverPromisesTheNumber() {
        withPicker(FakePicker()) { GuardianPhoneScreen(picture, "Sam", "", alertsOn = true) {} }
        reachable("Aegida will let Sam know you changed it. It never sends the number.")
    }

    @Test fun k7_withAlertsOffNoOneWillBeToldNowSoThePageDoesNotSayIt() {
        withPicker(FakePicker()) { GuardianPhoneScreen(picture, "Sam", "", alertsOn = false) {} }
        compose.onNodeWithText("Aegida will let Sam know you changed it. It never sends the number.").assertDoesNotExistOrFail()
    }

    // ---- Fix a spelling ----

    @Test fun t5_2_fixASpellingOffersOnlyTheTwoNamesAndSavesBoth() {
        var saved: List<String>? = null
        show { FixNamesScreen(picture, "Ruht", "Sam") { a, b -> saved = listOf(a, b) } }
        reachable(Texts.Setup.YOUR_NAME)
        reachable(Texts.Setup.GUARDIAN_NAME)
        reachable("Aegida will use these names from now on. Nothing is sent.")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).assertDoesNotExistOrFail()
        compose.onNodeWithText(Texts.Setup.GUARDIAN_PHONE).assertDoesNotExistOrFail()
        compose.onNodeWithText(Texts.Setup.PICK_CONTACT).assertDoesNotExistOrFail()
        compose.onNodeWithText("Ruht").performScrollTo().performTextClearance()
        compose.onNodeWithText(Texts.Setup.YOUR_NAME).performScrollTo().performTextInput("Ruth")
        compose.onNodeWithText(Texts.Common.SAVE).performScrollTo().performClick()
        assertEquals(listOf("Ruth", "Sam"), saved)
    }

    @Test fun t5_10_fixASpellingWaitsWhileEitherNameIsBlank() {
        show { FixNamesScreen(picture, "Ruth", "Sam") { _, _ -> } }
        compose.onNodeWithText("Sam").performScrollTo().performTextClearance()
        compose.onNodeWithText(Texts.Common.SAVE).performScrollTo().assertIsNotEnabled()
    }

    // ---- Pick a different guardian, prefilled ----

    private val current = Guardians(
        alertsOn = true, ownerName = "Ruth", firstTopic = null,
        firstGuardianName = "Sam", firstGuardianEmail = "sam@example.org", firstGuardianPhone = "555 0100",
    )

    @Test fun t5_16_theChangeFlowOpensWithTheCurrentDetailsAndSavingThemUnchangedAsksNoOneToBeTold() {
        var saved: List<String>? = null
        show { ChangeGuardianScreen(picture, "Sam", current) { a, b, c -> saved = listOf(a, b, c) } }
        compose.onNodeWithText("sam@example.org").performScrollTo()
        compose.onNodeWithText(Texts.Common.SAVE).performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf("Sam", "sam@example.org", "555 0100"), saved)
        compose.onNodeWithText(Texts.Settings.CHANGE_GUARDIAN_POPUP_OK).assertDoesNotExistOrFail()
    }

    @Test fun t5_16_aNewEmailStillAsksHerToTellTheOldGuardianFirst() {
        var saved: List<String>? = null
        show { ChangeGuardianScreen(picture, "Sam", current) { a, b, c -> saved = listOf(a, b, c) } }
        compose.onNodeWithText("sam@example.org").performScrollTo().performTextClearance()
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).performScrollTo().performTextInput("ann@example.org")
        compose.onNodeWithText(Texts.Common.SAVE).performScrollTo().performClick()
        assertNull(saved)
        reachable(Texts.Settings.CHANGE_GUARDIAN_POPUP_OK)
    }

    @Test fun t5_16_theSameEmailInAnotherLetterCaseIsTheSamePersonAndAsksNoOne() {
        var saved: List<String>? = null
        show { ChangeGuardianScreen(picture, "Sam", current) { a, b, c -> saved = listOf(a, b, c) } }
        compose.onNodeWithText("sam@example.org").performScrollTo().performTextClearance()
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).performScrollTo().performTextInput(" SAM@Example.org ")
        compose.onNodeWithText(Texts.Common.SAVE).performScrollTo().performClick()
        assertEquals(listOf("Sam", " SAM@Example.org ", "555 0100"), saved)
    }
}
