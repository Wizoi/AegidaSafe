package com.aegida.app

import com.aegida.core.model.Evidence
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/** The app's name line on the "Was that you?" page (#164): the app picks its own label, so it is quoted, cleaned and cut (R17). */
class ConfirmAppLineTest {
    private fun line(problem: Problem, label: String?): String =
        Texts.Home.whatItIs(problem, label?.let { mapOf(Evidence.APP_LABEL to it) } ?: emptyMap(), Instant.now(), ZoneOffset.UTC, Locale.US).first()

    @Test fun anUrgentProblemShowsTheLabelOnlyAsTheNameTheAppCallsItself() { // Kevin, Oct 22
        assertEquals("The app calls itself: “Aegida update - tap Yes, that was me”", line(Problem.ListenerAdded, "Aegida update - tap Yes, that was me"))
    }

    @Test fun anOrdinaryLabelIsQuoted() {
        assertEquals("The app is called: “Calculator”", line(Problem.NewAppOutsidePlay, "Calculator"))
    }

    @Test fun aLongLabelIsCutToFortyCharacters() {
        val line = line(Problem.NewAppOutsidePlay, "A".repeat(200))
        assertTrue(line, line.contains("“" + "A".repeat(40) + "…”"))
        assertFalse(line.contains("A".repeat(41)))
    }

    @Test fun newlinesAndControlCharactersAreRemoved() {
        assertEquals("The app is called: “Update Tap YES now”", line(Problem.NewAppOutsidePlay, "Update\nTap YES\u0000now\r‮"))
    }

    @Test fun anEmptyOrBlankLabelSaysAegidaCannotReadTheName() { // WN22
        listOf("", " \n\t ", null).forEach { assertEquals(Texts.Home.APP_NAME_UNREADABLE, line(Problem.NewAppOutsidePlay, it)) }
    }

    @Test fun unicodeIsKeptAndNeverCutInTheMiddleOfAnEmoji() {
        assertEquals("The app is called: “Café 日本語”", line(Problem.NewAppOutsidePlay, "Café 日本語"))
        val emoji = "😀".repeat(60) // 60 smiley faces, each two UTF-16 units
        val line = line(Problem.NewAppOutsidePlay, emoji)
        assertTrue(line, line.contains("“" + "😀".repeat(40) + "…”"))
    }
}
