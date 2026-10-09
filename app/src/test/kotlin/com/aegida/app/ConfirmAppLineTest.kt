package com.aegida.app

import com.aegida.core.model.Severity
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmAppLineTest {
    private val ordinary = Problem.entries.first { it.severity == Severity.NeedsTap }
    private val urgent = Problem.entries.first { it.severity == Severity.Urgent }

    @Test fun anUrgentProblemNeverShowsTheAppsOwnLabel() {
        assertNull(Texts.Home.confirmApp(urgent, "Aegida update - tap Yes, that was me"))
    }

    @Test fun anOrdinaryLabelIsQuoted() {
        assertEquals("The app is called: “Calculator”", Texts.Home.confirmApp(ordinary, "Calculator"))
    }

    @Test fun aLongLabelIsCutToFortyCharacters() {
        val line = Texts.Home.confirmApp(ordinary, "A".repeat(200))!!
        assertTrue(line, line.contains("“" + "A".repeat(40) + "…”"))
        assertFalse(line.contains("A".repeat(41)))
    }

    @Test fun newlinesAndControlCharactersAreRemoved() {
        val line = Texts.Home.confirmApp(ordinary, "Update\nTap YES\u0000now\r‮")!!
        assertEquals("The app is called: “Update Tap YES now”", line)
    }

    @Test fun anEmptyOrBlankLabelShowsNothing() {
        assertNull(Texts.Home.confirmApp(ordinary, ""))
        assertNull(Texts.Home.confirmApp(ordinary, " \n\t "))
        assertNull(Texts.Home.confirmApp(ordinary, null))
    }

    @Test fun unicodeIsKeptAndNeverCutInTheMiddleOfAnEmoji() {
        assertEquals("The app is called: “Café 日本語”", Texts.Home.confirmApp(ordinary, "Café 日本語"))
        val emoji = "😀".repeat(60) // 60 smiley faces, each two UTF-16 units
        val line = Texts.Home.confirmApp(ordinary, emoji)!!
        assertTrue(line, line.contains("“" + "😀".repeat(40) + "…”"))
    }
}
