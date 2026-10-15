package com.aegida.app

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.aegida.app.ui.setup.PictureStep
import com.aegida.app.ui.setup.StepPosition
import com.aegida.app.ui.theme.AegidaTheme
import com.aegida.core.model.SafetyPicture
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Layout on an ordinary phone (360 x 800 dp). The screen tests run on Robolectric's small default screen, which cannot say
 * whether a button is on the first screen. Kevin, Oct 9: on the safety-picture step the Continue button fell below the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class SetupLayoutTest {
    private val registerTestActivity = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context.packageName, ComponentActivity::class.java.name))
        }
    }
    private val composeRule = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(registerTestActivity).around(composeRule)

    private fun y(label: String) = composeRule.onNodeWithContentDescription(label).fetchSemanticsNode().positionInRoot.y

    @Test fun theTenPicturesAreThreeAcrossAndTheContinueButtonIsOnTheFirstScreen() {
        composeRule.setContent { AegidaTheme { PictureStep(StepPosition(null, 2, 6), null, {}, {}) } }
        val rows = SafetyPicture.entries.map { y(it.label) }
        assertEquals("the first three pictures share a row", 1, rows.take(3).distinct().size)
        assertEquals("the next three share a row", 1, rows.drop(3).take(3).distinct().size)
        assertTrue("the fourth picture starts a new row", rows[3] > rows[0])
        val screenBottom = composeRule.onRoot().fetchSemanticsNode().size.height
        val button = composeRule.onNodeWithText(Texts.Common.CONTINUE).fetchSemanticsNode()
        val buttonBottom = button.positionInRoot.y + button.size.height
        assertTrue("Continue ends at $buttonBottom px but the screen is $screenBottom px tall", buttonBottom <= screenBottom)
    }
}
