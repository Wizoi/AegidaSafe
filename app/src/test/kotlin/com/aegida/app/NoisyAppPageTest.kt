package com.aegida.app

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.aegida.core.model.Finding
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/** The page Show me opens for an app that keeps sending ads as notices. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class NoisyAppPageTest(dark: Boolean, fontScale: Float) : ScreenTestBase(dark, fontScale) {

    /** Finding #92: an app that keeps sending ads as notices. Show me opens a page that says which app and gives her four ways out. */
    @Test fun theNoisyAppPageSaysWhichAppAndOffersToTurnItOffRemoveItLeaveItAloneOrJustCloseTheWarning() {
        val taps = mutableListOf<String>()
        show {
            com.aegida.app.ui.NoisyAppPage(
                picture, noisy(), com.aegida.app.ui.ShownAt(now, zone, locale),
                com.aegida.app.ui.NoisyAppActions(
                    onTurnOff = { taps += "off" }, onRemove = { taps += "remove" }, onFine = { taps += "fine" }, onGotIt = { taps += "gotit" },
                ),
            )
        }
        reachable(Problem.NoisyApp.forMom)
        reachable("It came from the “Image to PDF” app.")
        reachable("It said: “File converter is ready!”")
        reachable("It sent 8 notices that look like ads lately.")
        for (button in listOf(Texts.Home.NOISY_TURN_OFF, Texts.Home.NOISY_REMOVE, Texts.Home.NOISY_FINE, Texts.Home.GOT_IT)) {
            compose.onNodeWithText(button).performScrollTo().performClick()
        }
        assertEquals(listOf("off", "remove", "fine", "gotit"), taps)
    }
}
