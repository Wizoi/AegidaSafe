package com.aegida.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.aegida.app.ui.HomeScreen
import com.aegida.app.ui.ShownAt
import com.aegida.core.model.HomeState
import com.aegida.app.ui.SettingsActions
import com.aegida.app.ui.SettingsContent
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
                SettingsContent(
                    Setup(null, emptySet(), pictureId = picture),
                    Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                    filterOn = true, listsUpdatedAt = null, zone = zone, locale = locale, allowed = listOf(item),
                ),
                SettingsActions(
                    onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = {}, onHowItWorks = {},
                    onStopAllowing = { removed = it },
                ),
            )
        }
        reachable(Texts.Settings.ALLOWED_TITLE)
        reachable("Image to PDF")
        compose.onNodeWithText(Texts.Settings.ALLOWED_REMOVE).performScrollTo().performClick()
        assertEquals(item, removed)
    }

    /** ST29 (findings #155): a website she said is fine is listed by its name, as plain words, with the same way to take it back. */
    @Test fun aWebsiteSheSaidIsFineIsListedByNameAndTakingItBackIsOneTap() {
        var removed: com.aegida.core.notice.AllowedItem? = null
        val item = com.aegida.core.notice.AllowedItem(Problem.LookalikeLink, "paypal-help.com", "paypal-help.com", now)
        show {
            SettingsScreen(
                SettingsContent(
                    Setup(null, emptySet(), pictureId = picture),
                    Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                    filterOn = true, listsUpdatedAt = null, zone = zone, locale = locale, allowed = listOf(item),
                ),
                SettingsActions(
                    onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = {}, onHowItWorks = {},
                    onStopAllowing = { removed = it },
                ),
            )
        }
        reachable("Links to paypal-help.com")
        compose.onNodeWithText(Texts.Settings.ALLOWED_REMOVE).performScrollTo().performClick()
        assertEquals(item, removed)
    }

    private fun noShareOrRate() {
        for (text in listOf(Texts.Settings.SHARE_BUTTON, Texts.Settings.RATE_BUTTON, Texts.Settings.RATE_NOTE)) {
            compose.onNodeWithText(text).assertDoesNotExist()
        }
    }

    private fun showSettings(listedOnPlay: Boolean? = null, actions: SettingsActions = SettingsActions({}, {}, {}, {}, {}, {})) = show {
        val content = SettingsContent(
            Setup(null, emptySet(), pictureId = picture),
            Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
            filterOn = true, listsUpdatedAt = null, zone = zone, locale = locale,
        )
        SettingsScreen(if (listedOnPlay == null) content else content.copy(listedOnPlay = listedOnPlay), actions)
    }

    /** T10.0 and T11: while Aegida is in closed testing (the switch as shipped) there is no share button, no rate button and no line about reviews. */
    @Test fun whileInClosedTestingThereIsNoTellAFriendAndNoRateButton() {
        showSettings()
        reachable(Texts.Settings.HOW_IT_WORKS)
        noShareOrRate()
    }

    /** T10.1, T10.5, T11.1, T11.7: once public, both buttons sit under "How does this work?", each tap just opens, twice means twice. */
    @Test fun oncePublicTellAFriendAndRateAreQuietButtonsUnderHowDoesThisWorkWithTheGoogleNameLine() {
        var shared = 0
        var rated = 0
        showSettings(true, SettingsActions({}, {}, {}, {}, {}, {}, onShare = { shared++ }, onRate = { rated++ }))
        reachable(Texts.Settings.RATE_NOTE)
        compose.onNodeWithText(Texts.Settings.SHARE_BUTTON).performScrollTo().performClick().performClick()
        compose.onNodeWithText(Texts.Settings.RATE_BUTTON).performScrollTo().performClick()
        assertEquals(2, shared)
        assertEquals(1, rated)
        val how = compose.onNodeWithText(Texts.Settings.HOW_IT_WORKS).fetchSemanticsNode().positionInRoot.y
        assertTrue("under How does this work?", compose.onNodeWithText(Texts.Settings.SHARE_BUTTON).fetchSemanticsNode().positionInRoot.y > how)
        assertTrue("the Google-name line is above the Rate button",
            compose.onNodeWithText(Texts.Settings.RATE_NOTE).fetchSemanticsNode().positionInRoot.y <
                compose.onNodeWithText(Texts.Settings.RATE_BUTTON).fetchSemanticsNode().positionInRoot.y)
    }

    /** T10.7 and T11.4: Home never offers either one. */
    @Test fun homeHasNoTellAFriendAndNoRateButton() {
        show { HomeScreen(HomeState.NoKnownProblems(now.minus(Duration.ofHours(3))), picture, ShownAt(now, zone, locale), {}, {}) }
        reachable(Texts.Home.NO_KNOWN_PROBLEMS)
        noShareOrRate()
    }

    /** T2: the way into "What you will see" is the first thing in My settings. */
    @Test fun myFirstSettingIsTheWayIntoWhatYouWillSee() {
        var opened = 0
        showSettings(actions = SettingsActions({}, {}, {}, {}, {}, {}, onWhatYouSee = { opened++ }))
        compose.onNodeWithText(Texts.Setup.SEE_TITLE).performScrollTo().performClick()
        assertEquals(1, opened)
        assertTrue(compose.onNodeWithText(Texts.Setup.SEE_TITLE).fetchSemanticsNode().positionInRoot.y <
            compose.onNodeWithText(Texts.Settings.NAME).fetchSemanticsNode().positionInRoot.y)
    }

    @Test fun settingsHasNoAllowedListWhenNothingWasAllowed() {
        show {
            SettingsScreen(
                SettingsContent(
                    Setup(null, emptySet(), pictureId = picture),
                    Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                    filterOn = true, listsUpdatedAt = null, zone = zone, locale = locale,
                ),
                SettingsActions(
                    onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = {}, onHowItWorks = {},
                ),
            )
        }
        compose.onNodeWithText(Texts.Settings.ALLOWED_TITLE).assertDoesNotExistOrFail()
    }

    @Test fun anAllowedAppsOwnLabelIsCleanedBeforeItIsShown() { // an app picks its label (R17, finding #93)
        val item = com.aegida.core.notice.AllowedItem(Problem.NoisyApp, "com.example.pdf", "Image\u2028to PDF", now)
        show {
            SettingsScreen(
                SettingsContent(
                    Setup(null, emptySet(), pictureId = picture),
                    Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                    filterOn = true, listsUpdatedAt = null, zone = zone, locale = locale, allowed = listOf(item),
                ),
                SettingsActions(
                    onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = {}, onHowItWorks = {},
                ),
            )
        }
        reachable("Image to PDF")
    }

    @Test fun settingsShowsHerChoicesAndTheAlertsSwitchWorks() {
        var alerts: Boolean? = null
        var opened = 0
        show {
            SettingsScreen(
                SettingsContent(
                    Setup(null, emptySet(), pictureId = picture),
                    Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                    filterOn = false, listsUpdatedAt = now.minus(Duration.ofDays(2)), zone = zone, locale = locale,
                ),
                SettingsActions(
                    onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = { alerts = it }, onFilterHelp = {},
                    onHowItWorks = { opened++ },
                ),
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
                SettingsContent(
                    Setup(null, emptySet(), pictureId = picture),
                    Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                    filterOn = false, listsUpdatedAt = null, zone = zone, locale = locale,
                ),
                SettingsActions(
                    onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = { tried++ }, onHowItWorks = {},
                ),
            )
        }
        compose.onNodeWithText(Texts.Settings.TURN_ON_WEBSITE_PROTECTION).performScrollTo().performClick()
        assertEquals(1, tried)
    }

    @Test fun websiteProtectionThatIsOnOffersNothingToRedo() {
        show {
            SettingsScreen(
                SettingsContent(
                    Setup(null, emptySet(), pictureId = picture),
                    Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                    filterOn = true, listsUpdatedAt = null, zone = zone, locale = locale,
                ),
                SettingsActions(
                    onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = {}, onHowItWorks = {},
                ),
            )
        }
        reachable(Texts.Settings.ON)
        compose.onNodeWithText(Texts.Settings.TURN_ON_WEBSITE_PROTECTION).assertDoesNotExistOrFail()
    }

    /** Kevin, Oct 9: the "How does this work?" button is the last thing on My settings, below her choices and the credits. */
    @Test fun theHowDoesThisWorkButtonIsAtTheBottomOfMySettings() {
        show {
            SettingsScreen(
                SettingsContent(
                    Setup(null, emptySet(), pictureId = picture),
                    Guardians(alertsOn = false, ownerName = "Ruth", firstTopic = null, firstGuardianName = "Kevin"),
                    filterOn = false, listsUpdatedAt = now.minus(Duration.ofDays(2)), zone = zone, locale = locale,
                ),
                SettingsActions(
                    onChangePicture = {}, onChangeGuardian = {}, onResendInvite = {}, onAlerts = {}, onFilterHelp = {}, onHowItWorks = {},
                ),
            )
        }
        fun y(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y
        val button = y(Texts.Settings.HOW_IT_WORKS)
        for (above in listOf(Texts.Settings.NAME, Texts.Settings.GUARDIAN, Texts.Settings.PICTURE, Texts.Settings.TELL_GUARDIANS,
            Texts.Settings.CREDITS_TITLE)) {
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
