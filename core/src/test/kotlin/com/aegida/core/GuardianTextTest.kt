package com.aegida.core

import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a guardian reads (Kevin, Oct 9): the sentences name the owner and never assume "her", and only the urgent ones say
 * "please call". The gentle ones leave the call to the guardian's judgement. "Please call" stays on: an urgent reminder, a
 * guardian change (a coached change is a scam signal) and an urgent "she said it was hers" (it could be a coached yes).
 */
class GuardianTextTest {
    private val owner = "Ruth"
    private val gendered = Regex("""\b(her|hers|herself|she|he|him|his|himself)\b""", RegexOption.IGNORE_CASE)

    private fun everySentence(): Map<String, String> = buildMap {
        Problem.entries.forEach { p -> p.forGuardian(owner)?.let { put("Problem.${p.name}", it) } }
        put("SET_UP", Texts.Guardian.SET_UP.format(owner))
        put("STILL_OPEN", Texts.Guardian.STILL_OPEN.format(owner))
        put("STILL_OPEN_URGENT", Texts.Guardian.STILL_OPEN_URGENT.format(owner))
        put("CONFIRMED", Texts.Guardian.CONFIRMED.format(owner))
        put("CONFIRMED_URGENT", Texts.Guardian.CONFIRMED_URGENT.format(owner))
        put("CHANGED", Texts.Guardian.CHANGED.format(owner))
        put("NOT_CHECKED_IN", Texts.Guardian.NOT_CHECKED_IN.format(owner))
    }

    @Test fun noSentenceAGuardianReadsAssumesHowTheOwnerIsAddressed() {
        val found = everySentence().filter { gendered.containsMatchIn(it.value) }.map { "${it.key}: ${it.value}" }
        assertEquals("use the owner's name, not a pronoun", emptyList<String>(), found)
    }

    @Test fun everySentenceNamesTheOwner() {
        val missing = everySentence().filterNot { it.value.contains(owner) }.keys
        assertEquals(emptySet<String>(), missing)
    }

    @Test fun onlyTheSeriousMessagesSayPleaseCall() {
        val text = everySentence()
        for (gentle in listOf("STILL_OPEN", "NOT_CHECKED_IN")) {
            assertFalse("$gentle should leave the call to the guardian", text.getValue(gentle).contains("lease call", ignoreCase = true))
        }
        for (firm in listOf("STILL_OPEN_URGENT", "CHANGED", "CONFIRMED_URGENT")) {
            assertTrue("$firm should ask for a call", text.getValue(firm).contains("please call", ignoreCase = true))
        }
    }

    @Test fun theAskedAboutSentencesQuoteWhatTheOwnerWasAsked() {
        // "Aegida asked her if that was her" became a quote of what she saw on screen: no pronoun needed.
        val asked = Problem.entries.filter { it.fix == com.aegida.core.text.Fix.ConfirmChange || it.fix == com.aegida.core.text.Fix.ConfirmEvent }.mapNotNull { it.forGuardian(owner) }
        assertTrue(asked.isNotEmpty())
        asked.forEach { assertTrue(it, it.contains("Was that you?")) }
    }
}
