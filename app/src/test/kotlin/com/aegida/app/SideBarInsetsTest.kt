package com.aegida.app

import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aegida.app.ui.MainContent
import com.aegida.app.ui.Tab
import com.aegida.core.model.Setup
import com.aegida.core.text.Texts
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * #199: a phone turned sideways with three-button navigation puts the navigation bar on the right. Home, History and My settings must
 * keep their words and buttons out from under it (seen on the emulator, Nov 24: My settings ran under the bar). The bar is given to
 * Compose the way Android gives it, through the window insets of the screen's own view.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class SideBarInsetsTest(dark: Boolean, fontScale: Float) : ScreenTestBase(dark, fontScale) {

    @Test fun turnedSidewaysMySettingsStaysClearOfTheNavigationBarOnTheRight() {
        val loader = mockk<MainStateLoader>()
        coEvery { loader.load() } returns MainState()
        val vm = MainViewModel(loader, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        var view: View? = null
        show {
            view = LocalView.current
            MainContent(MainState(loaded = true, setup = Setup(null, emptySet(), pictureId = picture).copy(stepsDone = 99)), vm)
        }
        val barPx = 120
        compose.runOnIdle {
            val bar = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, barPx, 0)).build()
            ViewCompat.dispatchApplyWindowInsets(view!!, bar)
        }
        compose.onNodeWithText(Tab.Settings.label).performClick()
        compose.waitForIdle()
        val screenRight = compose.onRoot().getBoundsInRoot().right
        val buttonRight = compose.onNodeWithText(Texts.Setup.SEE_TITLE).getBoundsInRoot().right
        val barDp = with(compose.density) { barPx.toDp() }
        assertTrue("the button ends at $buttonRight, under the bar that starts at ${screenRight - barDp}", buttonRight <= screenRight - barDp + 0.5.dp)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() = onNode(androidx.compose.ui.test.isRoot())
}
