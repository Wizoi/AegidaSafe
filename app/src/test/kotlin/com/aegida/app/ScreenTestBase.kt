package com.aegida.app

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.aegida.app.ui.theme.AegidaTheme
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.SafetyPicture
import com.aegida.core.text.Problem
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Shared set-up for the screen tests: every screen in light and dark, at the normal font and at 200% (the owner's
 * reality, CLAUDE.md). Each check scrolls to the main button, so a button pushed off the bottom by large text fails.
 * A screen's test class extends this and carries the runner annotation.
 */
abstract class ScreenTestBase(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @Parameters(name = "dark={0} font={1}")
        fun variants() = listOf(arrayOf(false, 1f), arrayOf(true, 1f), arrayOf(false, 2f), arrayOf(true, 2f))
    }

    // The Compose test activity is declared only in debug builds (and must never ship), so the release unit
    // tests register it here instead. Findings #35.
    protected val registerTestActivity = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context.packageName, ComponentActivity::class.java.name))
        }
    }
    protected val composeRule = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(registerTestActivity).around(composeRule)
    protected val compose get() = composeRule

    protected val now = Instant.now()
    protected val zone = ZoneId.of("America/Los_Angeles")
    protected val locale = Locale.US
    protected val picture = SafetyPicture.Sunflower.id

    protected fun show(content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { AegidaTheme(dark = dark, content = content) }
    }

    protected fun finding(problem: Problem, minutesOld: Long = 5) =
        Finding(FindingKey(DetectorId.Dns, problem, problem.name), emptyMap(), now.minus(Duration.ofMinutes(minutesOld)), now)

    protected fun reachable(text: String) = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()


    protected fun noisy() = Finding(
        FindingKey(DetectorId.NoisyApp, Problem.NoisyApp, "com.example.pdf"),
        mapOf(
            com.aegida.core.model.Evidence.APP_LABEL to "Image to PDF", com.aegida.core.model.Evidence.NOTICES to "8",
            com.aegida.core.model.Evidence.MESSAGE_TITLE to "File converter is ready!", com.aegida.core.model.Evidence.MESSAGE_AT to now.minus(Duration.ofMinutes(5)).toEpochMilli().toString(),
        ),
        now.minus(Duration.ofMinutes(5)), now,
    )

    protected fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistOrFail() {
        try {
            assertExists()
        } catch (e: AssertionError) {
            return
        }
        throw AssertionError("expected no such node")
    }
}
