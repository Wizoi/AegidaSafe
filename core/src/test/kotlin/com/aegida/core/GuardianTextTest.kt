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
        put("ALLOWED", Texts.Guardian.ALLOWED.format(owner, "Image to PDF"))
    }

    @Test fun noSentenceAGuardianReadsAssumesHowTheOwnerIsAddressed() {
        val found = everySentence().filter { gendered.containsMatchIn(it.value) }.map { "${it.key}: ${it.value}" }
        assertEquals("use the owner's name, not a pronoun", emptyList<String>(), found)
    }

    /** Kevin, Oct 10: the note names the app and a masked web address, and nothing else about the message. */
    @Test fun aScamLinkNoteNamesTheAppAndAMaskedAddress() {
        assertEquals("A message with a scam link arrived on Ruth's phone, from the “Messages” app, with a link to pa*****.example. Aegida warned Ruth.", Problem.ScamLink.forGuardian(owner, "Messages", "paypa1-help.example"))
        assertEquals("A message pretending to be a well-known company arrived on Ruth's phone, from the “Messages” app. Aegida warned Ruth.", Problem.LookalikeLink.forGuardian(owner, "Messages"))
        assertEquals(Problem.ScamLink.forGuardian(owner), Problem.ScamLink.forGuardian(owner, null, null))
        assertEquals(Problem.ScamLink.forGuardian(owner), Problem.ScamLink.forGuardian(owner, "   ", ""))
        assertEquals("other problems do not mention an app", Problem.ListenerOff.forGuardian(owner), Problem.ListenerOff.forGuardian(owner, "Messages", "x.example"))
        assertEquals("an app picks its own label, so it is cut short and cleaned", false, Problem.ScamLink.forGuardian(owner, "Call me\nnow " + "x".repeat(100))!!.contains("\n"))
    }

    @Test fun aWebsiteNoteNamesTheSiteMasked() {
        assertEquals("A website (ne*****.example) was sending fake warnings to Ruth's phone. Aegida hid them.", Problem.SiteNotifications.forGuardian(owner, null, "https://news.example"))
        assertEquals("A website (re*****.example) that was not there before started sending notifications on Ruth's phone. Aegida asked Ruth: “Was that you?”", Problem.NewWebsiteNotifications.forGuardian(owner, null, "https://recipes.example"))
        assertEquals(false, Problem.SiteNotifications.forGuardian(owner, null, "https://news.example")!!.contains("news"))
    }

    /** Kevin, Oct 11 (finding #92): a note about an app that keeps sending ads as notices, and one when the owner says an app is fine. */
    @Test fun theNoisyAppNoteNamesTheAppAndSaysWhatAegidaDid() {
        assertEquals("An app keeps showing Ruth ads as notices. Aegida told Ruth how to turn them off.", Problem.NoisyApp.forGuardian(owner))
        assertEquals("An app (“Image to PDF”) keeps showing Ruth ads as notices. Aegida told Ruth how to turn them off.", Problem.NoisyApp.forGuardian(owner, "Image to PDF"))
    }

    @Test fun theAllowedNoteNamesTheAppAndLeavesTheCallToTheGuardian() {
        assertEquals(
            "Ruth told Aegida that an app (“Image to PDF”) is fine, so Aegida will stop warning about it. If that surprises you, it may be a good time to ask Ruth.",
            Texts.Guardian.ALLOWED.format(owner, "Image to PDF"),
        )
    }

    @Test fun everySentenceNamesTheOwner() {
        val missing = everySentence().filterNot { it.value.contains(owner) }.keys
        assertEquals(emptySet<String>(), missing)
    }

    @Test fun onlyTheSeriousMessagesSayPleaseCall() {
        val text = everySentence()
        for (gentle in listOf("STILL_OPEN", "NOT_CHECKED_IN", "ALLOWED")) {
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
