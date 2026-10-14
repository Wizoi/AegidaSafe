package com.aegida.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.aegida.app.ui.SettingsScreen
import com.aegida.app.ui.setup.ChangeGuardianScreen
import com.aegida.core.alert.Guardians
import com.aegida.core.model.Setup
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.time.Duration

/** My settings and Change guardian, in light and dark at 100% and 200% font. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class SettingsScreenTest(dark: Boolean, fontScale: Float) : ScreenTestBase(dark, fontScale) {

    /** Kevin, Oct 11: everything the owner said is fine is listed in My settings, each with a way to take it back. */
    @Test fun settingsListsWhatWasAllowedAndTakingItBackIsOneTap() {
        var removed: com.aegida.core.notice.AllowedItem? = null
        val item = com.aegida.core.notice.AllowedItem(Problem.NoisyApp, "com.example.pdf", "Image to PDF", now)
        show {
            SettingsScreen(
                Setup(null, emptySet(), pictureId = picture),
                Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                filterOn = true, onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = {}, onHowItWorks = {},
                listsUpdatedAt = null, zone = zone, locale = locale, allowed = listOf(item), onStopAllowing = { removed = it },
            )
        }
        reachable(Texts.Settings.ALLOWED_TITLE)
        reachable("Image to PDF")
        compose.onNodeWithText(Texts.Settings.ALLOWED_REMOVE).performScrollTo().performClick()
        assertEquals(item, removed)
    }

    @Test fun settingsHasNoAllowedListWhenNothingWasAllowed() {
        show {
            SettingsScreen(
                Setup(null, emptySet(), pictureId = picture),
                Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                filterOn = true, onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = {}, onHowItWorks = {},
                listsUpdatedAt = null, zone = zone, locale = locale,
            )
        }
        compose.onNodeWithText(Texts.Settings.ALLOWED_TITLE).assertDoesNotExistOrFail()
    }

    @Test fun anAllowedAppsOwnLabelIsCleanedBeforeItIsShown() { // an app picks its label (R17, finding #93)
        val item = com.aegida.core.notice.AllowedItem(Problem.NoisyApp, "com.example.pdf", "Image\u2028to PDF", now)
        show {
            SettingsScreen(
                Setup(null, emptySet(), pictureId = picture),
                Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                filterOn = true, onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = {}, onHowItWorks = {},
                listsUpdatedAt = null, zone = zone, locale = locale, allowed = listOf(item),
            )
        }
        reachable("Image to PDF")
    }

    @Test fun settingsShowsHerChoicesAndTheAlertsSwitchWorks() {
        var alerts: Boolean? = null
        var opened = 0
        show {
            SettingsScreen(
                Setup(null, emptySet(), pictureId = picture),
                Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                filterOn = false, onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = { alerts = it }, onFilterHelp = {}, onHowItWorks = { opened++ },
                listsUpdatedAt = now.minus(Duration.ofDays(2)), zone = zone, locale = locale,
            )
        }
        reachable("Ruth")
        reachable("Kevin")
        // Kevin, Oct 9: a way from the app to the page that says what Aegida does and how.
        compose.onNodeWithText(Texts.Settings.HOW_IT_WORKS).performScrollTo().performClick()
        assertEquals(1, opened)
        reachable(Texts.Settings.CHANGE_GUARDIAN)
        reachable(Texts.Settings.RESEND_INVITE)
        reachable(Texts.Settings.CREDITS) // CC BY 4.0 asks for credit
        reachable(com.aegida.app.ui.TimeText.listsLine(now.minus(Duration.ofDays(2)), zone, locale)) // how fresh the lists are (Kevin, Oct 9)
        compose.onNode(androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.isToggleable()).performScrollTo().performClick()
        assertEquals(true, alerts)
        reachable(Texts.Settings.OFF)
        reachable(Texts.Settings.TURN_ON_WEBSITE_PROTECTION) // Kevin, Oct 10: skipped in setup, so an easy way to try again
    }

    @Test fun websiteProtectionThatIsOffHasAnEasyWayToTryAgainAndOnHasNone() {
        var tried = 0
        show {
            SettingsScreen(
                Setup(null, emptySet(), pictureId = picture),
                Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                filterOn = false, onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = { tried++ }, onHowItWorks = {},
                listsUpdatedAt = null, zone = zone, locale = locale,
            )
        }
        compose.onNodeWithText(Texts.Settings.TURN_ON_WEBSITE_PROTECTION).performScrollTo().performClick()
        assertEquals(1, tried)
    }

    @Test fun websiteProtectionThatIsOnOffersNothingToRedo() {
        show {
            SettingsScreen(
                Setup(null, emptySet(), pictureId = picture),
                Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                filterOn = true, onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = {}, onHowItWorks = {},
                listsUpdatedAt = null, zone = zone, locale = locale,
            )
        }
        reachable(Texts.Settings.ON)
        compose.onNodeWithText(Texts.Settings.TURN_ON_WEBSITE_PROTECTION).assertDoesNotExistOrFail()
    }

    /** Kevin, Oct 9: the "How does this work?" button is the last thing on My settings, below her choices and the credits. */
    @Test fun theHowDoesThisWorkButtonIsAtTheBottomOfMySettings() {
        show {
            SettingsScreen(
                Setup(null, emptySet(), pictureId = picture),
                Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                filterOn = false, onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = {}, onHowItWorks = {},
                listsUpdatedAt = now.minus(Duration.ofDays(2)), zone = zone, locale = locale,
            )
        }
        fun y(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y
        val button = y(Texts.Settings.HOW_IT_WORKS)
        for (above in listOf(Texts.Settings.NAME, Texts.Settings.GUARDIAN, Texts.Settings.PICTURE, Texts.Settings.TELL_GUARDIANS, Texts.Settings.CREDITS_TITLE)) {
            assertTrue("the button should be below \"$above\"", button > y(above))
        }
    }

    @Test fun changingTheGuardianFirstAsksHerToTellTheOldOneAndOnlySavesAfterShesAgreed() {
        var saved: List<String>? = null
        show { ChangeGuardianScreen(picture, "Kevin") { a, b, c -> saved = listOf(a, b, c) } }
        compose.onNodeWithText(Texts.Common.SAVE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(Texts.Setup.GUARDIAN_NAME).performScrollTo().performTextInput("Ann")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).performScrollTo().performTextInput("ann@example.org")
        compose.onNodeWithText(Texts.Common.SAVE).performScrollTo().assertIsEnabled().performClick()
        assertEquals(null, saved)
        compose.onNodeWithText(Texts.Settings.CHANGE_GUARDIAN_POPUP_TITLE.format("Kevin")).assertIsDisplayed()
        compose.onNodeWithText(Texts.Settings.CHANGE_GUARDIAN_POPUP_BACK).performClick()
        assertEquals(null, saved)
        compose.onNodeWithText(Texts.Common.SAVE).performScrollTo().performClick()
        compose.onNodeWithText(Texts.Settings.CHANGE_GUARDIAN_POPUP_OK).performClick()
        assertEquals(listOf("Ann", "ann@example.org", ""), saved)
    }

    @Test fun changingTheGuardianWithNoPreviousOneDoesNotAskAnyone() {
        var saved: List<String>? = null
        show { ChangeGuardianScreen(picture, "") { a, b, c -> saved = listOf(a, b, c) } }
        compose.onNodeWithText(Texts.Setup.GUARDIAN_NAME).performScrollTo().performTextInput("Ann")
        compose.onNodeWithText(Texts.Setup.GUARDIAN_EMAIL).performScrollTo().performTextInput("ann@example.org")
        compose.onNodeWithText(Texts.Common.SAVE).performScrollTo().performClick()
        assertEquals(listOf("Ann", "ann@example.org", ""), saved)
    }
}
