package com.aegida.app

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.aegida.app.ui.AdvicePage
import com.aegida.app.ui.ShownAt
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/** The page Show me opens for a link warning (findings #155, scenario rows ST27 and ST28), in light and dark at 100% and 200% font. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class AdvicePageTest(dark: Boolean, fontScale: Float) : ScreenTestBase(dark, fontScale) {
    private fun link(problem: Problem) = Finding(
        FindingKey(DetectorId.Link, problem, "paypal-help.com"),
        mapOf(Evidence.HOST to "paypal-help.com", Evidence.APP_LABEL to "Messages", Evidence.MESSAGE_TEXT to "Refund: paypal-help.com/claim"),
        now, now,
    )

    private fun showPage(finding: Finding, taps: MutableList<String>) = show {
        AdvicePage(picture, finding, ShownAt(now, zone, locale), onGotIt = { taps += "gotit" }, onFine = { taps += "fine" })
    }

    @Test fun aLookalikeLinkWarningOffersThisLinkIsFine() {
        val taps = mutableListOf<String>()
        showPage(link(Problem.LookalikeLink), taps)
        reachable(Problem.LookalikeLink.forMom)
        reachable("It says: “Refund: (link)”")
        compose.onNodeWithText(Texts.Home.LINK_FINE).performScrollTo().performClick()
        compose.onNodeWithText(Texts.Home.GOT_IT).performScrollTo().performClick()
        assertEquals(listOf("fine", "gotit"), taps)
    }

    @Test fun aLinkOnTheScamListNeverOffersThisLinkIsFine() {
        showPage(link(Problem.ScamLink), mutableListOf())
        reachable(Problem.ScamLink.forMom)
        compose.onNodeWithText(Texts.Home.LINK_FINE).assertDoesNotExistOrFail()
    }
}
