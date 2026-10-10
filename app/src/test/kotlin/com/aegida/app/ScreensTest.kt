package com.aegida.app

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.aegida.app.ui.HistoryScreen
import com.aegida.app.ui.HomeScreen
import com.aegida.app.ui.SettingsScreen
import com.aegida.app.ui.setup.AboutStep
import com.aegida.app.ui.setup.AccessStep
import com.aegida.app.ui.setup.AlertsStep
import com.aegida.app.ui.setup.ChangeGuardianScreen
import com.aegida.app.ui.setup.FilterStep
import com.aegida.app.ui.setup.InviteStep
import com.aegida.app.ui.setup.LocalSetupBack
import com.aegida.app.ui.setup.PictureStep
import com.aegida.app.ui.setup.WelcomeScreen
import com.aegida.app.ui.theme.AegidaTheme
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryRow
import com.aegida.core.health.CheckRun
import com.aegida.core.health.Trigger
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.HomeState
import com.aegida.core.model.SafetyPicture
import com.aegida.core.model.Setup
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Every screen in light and dark, at the normal font and at 200% (the owner's reality, CLAUDE.md). Each check
 * scrolls to the main button, so a button pushed off the bottom by large text fails here.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class ScreensTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @Parameters(name = "dark={0} font={1}")
        fun variants() = listOf(arrayOf(false, 1f), arrayOf(true, 1f), arrayOf(false, 2f), arrayOf(true, 2f))
    }

    // The Compose test activity is declared only in debug builds (and must never ship), so the release unit
    // tests register it here instead. Findings #35.
    private val registerTestActivity = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context.packageName, ComponentActivity::class.java.name))
        }
    }
    private val composeRule = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(registerTestActivity).around(composeRule)
    private val compose get() = composeRule

    private val now = Instant.now()
    private val zone = ZoneId.of("America/Los_Angeles")
    private val locale = Locale.US
    private val picture = SafetyPicture.Sunflower.id

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { AegidaTheme(dark = dark, content = content) }
    }

    private fun finding(problem: Problem, minutesOld: Long = 5) =
        Finding(FindingKey(DetectorId.Dns, problem, problem.name), emptyMap(), now.minus(Duration.ofMinutes(minutesOld)), now)

    private fun reachable(text: String) = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()

    // Home

    @Test fun homeWithNoKnownProblemsSaysSoAndOffersACall() {
        var called = 0
        show { HomeScreen(HomeState.NoKnownProblems(now.minus(Duration.ofHours(3))), picture, now, zone, locale, {}, { called++ }) }
        reachable(Texts.Home.ALL_GOOD)
        reachable(Texts.Home.NO_KNOWN_PROBLEMS)
        compose.onNodeWithText(Texts.Home.CALL_A_GUARDIAN).performScrollTo().performClick()
        assertEquals(1, called)
    }

    @Test fun homeWithOneProblemShowsItsSentenceAndABigFixButton() {
        val dns = finding(Problem.WebsiteFilterOff)
        var fixed: Finding? = null
        show { HomeScreen(HomeState.NeedsTaps(listOf(dns)), picture, now, zone, locale, { fixed = it }, {}) }
        reachable(Texts.Home.NEEDS_ONE)
        reachable(Problem.WebsiteFilterOff.forMom)
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).performScrollTo().performClick()
        assertEquals(dns, fixed)
        reachable(Texts.Home.CALL_A_GUARDIAN)
    }

    @Test fun homeOffersShowMeNotFixMyPhoneForAWarningThatFixesNothing() { // Kevin, Oct 10
        val link = finding(Problem.LookalikeLink)
        var shown: Finding? = null
        show { HomeScreen(HomeState.NeedsTaps(listOf(link)), picture, now, zone, locale, { shown = it }, {}) }
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).assertDoesNotExistOrFail()
        compose.onNodeWithText(Texts.Home.SHOW_ME).performScrollTo().performClick()
        assertEquals(link, shown)
    }

    @Test fun homeWithSeveralProblemsCountsThemAndStartsWithTheFirst() {
        val first = finding(Problem.ListenerOff, minutesOld = 60)
        var fixed: Finding? = null
        show { HomeScreen(HomeState.NeedsTaps(listOf(first, finding(Problem.WebsiteFilterOff))), picture, now, zone, locale, { fixed = it }, {}) }
        reachable(Texts.Home.NEEDS_MANY.format(2))
        compose.onNodeWithText(Texts.Home.FIX_MY_PHONE).performScrollTo().performClick()
        assertEquals(first, fixed)
    }

    @Test fun homeThatCouldNotCheckNeverSaysAllGood() {
        show { HomeScreen(HomeState.CouldntCheck(now.minus(Duration.ofDays(2))), picture, now, zone, locale, {}, {}) }
        reachable(Texts.Home.COULDNT_CHECK)
        reachable(Texts.Home.CALL_A_GUARDIAN)
        compose.onNodeWithText(Texts.Home.ALL_GOOD).assertDoesNotExistOrFail()
    }

    @Test fun homeThatNeverCheckedSaysSo() {
        show { HomeScreen(HomeState.CouldntCheck(null), picture, now, zone, locale, {}, {}) }
        reachable(Texts.Home.NEVER_CHECKED)
    }

    @Test fun herPictureIsOnTheScreenAndNamedForAScreenReader() {
        show { HomeScreen(HomeState.NoKnownProblems(now), picture, now, zone, locale, {}, {}) }
        compose.onNodeWithContentDescription(Texts.Common.YOUR_PICTURE.format("Sunflower")).assertExists()
        compose.onNodeWithText(Texts.APP_NAME).assertExists()
    }

    // History

    @Test fun historyListsEventsUnderADayHeadingAndSaysHowLongItIsKept() {
        val rows = listOf(
            HistoryRow(now.minus(Duration.ofMinutes(10)), "A website sent fake warnings", "You were warned.", handled = true, facts = "The website was news.example."),
            HistoryRow(now.minus(Duration.ofDays(1)), "Website protection was off", "You were warned. Fixed.", handled = true),
        )
        show { HistoryScreen(rows, picture, now, zone, locale) }
        reachable(Texts.History.TODAY)
        reachable("A website sent fake warnings")
        reachable("The website was news.example.") // what it was (Kevin, Oct 10)
        reachable(Texts.History.YESTERDAY)
        reachable(Texts.History.KEPT)
    }

    @Test fun emptyHistorySaysNothingHasHappened() {
        show { HistoryScreen(emptyList(), picture, now, zone, locale) }
        reachable(Texts.History.EMPTY)
    }

    // My settings

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
        reachable(Texts.Setup.FILTER_TITLE)
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

    // First-run steps

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
        show { AboutStep(null, 1, 6, Guardians.None) { _, _, _, _ -> } }
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
        show { AboutStep(null, 1, 6, Guardians.None) { _, _, _, _ -> } }
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
        show { AboutStep(null, 1, 6, Guardians.None) { a, b, c, d -> saved = listOf(a, b, c, d) } }
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
        show { PictureStep(null, 2, 6, null, { chosen = it }, onContinue = {}) }
        SafetyPicture.entries.forEach { compose.onNodeWithContentDescription(it.label).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Owl").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Owl").assertIsSelected()
        assertEquals(SafetyPicture.Owl, chosen)
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().assertIsEnabled()
    }

    @Test fun inviteStepOnlyLetsHerSayItWasSentAfterTheEmailWasWritten() {
        var written = 0
        show { InviteStep(picture, 3, 6, "Kevin", noEmailApp = false, onWrite = { written++ }, onSent = {}) }
        compose.onNodeWithText(Texts.Setup.INVITE_SENT).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(Texts.Setup.INVITE_WRITE).performScrollTo().performClick()
        assertEquals(1, written)
        compose.onNodeWithText(Texts.Setup.INVITE_SENT).performScrollTo().assertIsEnabled()
    }

    @Test fun inviteStepSaysSoWhenThereIsNoEmailApp() {
        show { InviteStep(picture, 3, 6, "Kevin", noEmailApp = true, onWrite = {}, onSent = {}) }
        reachable(Texts.Setup.INVITE_NO_EMAIL_APP)
    }

    @Test fun alertsStepOffersYesAndNoWithAnExampleOfWhatIsSent() {
        val answers = mutableListOf<Boolean>()
        show { AlertsStep(picture, 4, 6) { answers += it } }
        reachable(Texts.Setup.ALERTS_BODY)
        compose.onNodeWithText(Texts.Setup.ALERTS_YES).performScrollTo().performClick()
        compose.onNodeWithText(Texts.Setup.ALERTS_NO).performScrollTo().performClick()
        assertEquals(listOf(true, false), answers)
    }

    @Test fun accessStepShowsWhatIsDoneAndOnlyContinuesFullyWhenBothAreOn() {
        show { AccessStep(picture, 5, 6, listenerOn = true, bannersOn = false, onOpenAccess = {}, onAllowBanners = {}, onContinue = {}) }
        reachable(Texts.Setup.ACCESS_DONE)
        reachable(Texts.Setup.ACCESS_ALLOW_BANNERS)
        reachable(Texts.Common.NOT_NOW)
    }

    /** Findings #65: skipping the step that lets Aegida see notifications turns off its main job, so she is asked first. */
    @Test fun skippingTheNotificationStepAsksAreYouSureFirst() {
        var continued = 0
        show { AccessStep(picture, 5, 6, listenerOn = false, bannersOn = false, onOpenAccess = {}, onAllowBanners = {}, onContinue = { continued++ }) }
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
        show { AccessStep(picture, 5, 6, listenerOn = false, bannersOn = false, onOpenAccess = { access++ }, onAllowBanners = { banners++ }, onContinue = {}) }
        reachable(Texts.Setup.ACCESS_PART_1)
        reachable(Texts.Setup.ACCESS_PART_2)
        reachable(Texts.Setup.ACCESS_FIRST)
        compose.onNodeWithText(Texts.Setup.ACCESS_ALLOW_BANNERS).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(Texts.Setup.ACCESS_OPEN).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, access)
    }

    @Test fun eachFinishedPartShowsAGreenTickRowSoSheKnowsItWorked() {
        show { AccessStep(picture, 5, 6, listenerOn = true, bannersOn = true, onOpenAccess = {}, onAllowBanners = {}, onContinue = {}) }
        compose.onNode(androidx.compose.ui.test.hasContentDescription(Texts.Setup.ACCESS_DONE)).performScrollTo().assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasContentDescription(Texts.Setup.BANNERS_DONE)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().assertIsEnabled()
    }

    @Test fun filterStepReadsAsNotOnYetAndThenOnOnlyWhenAegidaHasReadItBack() {
        show { FilterStep(picture, 6, 6, filterOn = false, onOpenSettings = {}, onCheck = {}, onContinue = {}) }
        reachable(Texts.Setup.FILTER_NOT_YET)
        reachable(Texts.Setup.FILTER_COPY_AND_OPEN)
        reachable(Texts.Setup.FILTER_CHECK)
    }

    @Test fun theOneBigButtonOnTheFilterStepIsTheCopyAndOpenButton() {
        var opened = 0
        show { FilterStep(picture, 6, 6, filterOn = false, onOpenSettings = { opened++ }, onCheck = {}, onContinue = {}) }
        compose.onNodeWithText(Texts.Setup.FILTER_COPY_AND_OPEN).performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test fun whenWebsiteProtectionIsAlreadyOnTheStepIsJustAGreenTickAndContinue() { // findings #74: a wall of text for something already done
        var continued = 0
        show { FilterStep(picture, 6, 6, filterOn = true, onOpenSettings = {}, onCheck = {}, onContinue = { continued++ }) }
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
                InviteStep(picture, 3, 6, "Sam", noEmailApp = false, onWrite = {}, onSent = {})
            }
        }
        compose.onNodeWithText(Texts.Common.BACK).performScrollTo().performClick()
        assertEquals(1, backs)
    }

    @Test fun aStepWithNowhereToGoBackToHasNoBackButton() {
        show { InviteStep(picture, 3, 6, "Sam", noEmailApp = false, onWrite = {}, onSent = {}) }
        compose.onNodeWithText(Texts.Common.BACK).assertDoesNotExistOrFail()
    }

    @Test fun filterStepOnShowsItAndContinues() {
        var continued = 0
        show { FilterStep(picture, 6, 6, filterOn = true, onOpenSettings = {}, onCheck = {}, onContinue = { continued++ }) }
        reachable(Texts.Setup.FILTER_ON)
        compose.onNodeWithText(Texts.Common.CONTINUE).performScrollTo().performClick()
        assertEquals(1, continued)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistOrFail() {
        try {
            assertExists()
        } catch (e: AssertionError) {
            return
        }
        throw AssertionError("expected no such node")
    }
}
