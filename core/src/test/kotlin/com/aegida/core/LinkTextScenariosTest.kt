package com.aegida.core

import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scenario table of findings #99, one test per row: a scam address never leaves the phone unmasked and never shows as a link in
 * message words, while ordinary words with a dot stay words on the phone.
 */
class LinkTextScenariosTest {
    private val hostile = listOf(
        "Paypal-Secure.COM" to "Paypal-Secure",
        "EVIL-SITE.COM" to "EVIL-SITE",
        "evil.Top" to "evil.Top",
        "Verify at Paypa1.Com" to "Paypa1",
        "go evil.top/Win.Now" to "evil.top",
        "evil-site.com,Hello.There" to "evil-site",
        "x evil.com.Now" to "evil.com",
        "evil-site.com Mr.Smith" to "evil-site",
    )

    @Test fun offThePhoneEveryAddressTheLinkFinderSeesIsMaskedWithNoExceptions() {
        for ((text, leak) in hostile) {
            val sent = Texts.Home.guardianLabel(text)
            assertFalse("$text went out as $sent", sent.contains(leak, ignoreCase = true))
        }
    }

    @Test fun aNoisyAppNoteNeverCarriesAnAddressFromTheAppsName() {
        for ((text, leak) in hostile) {
            val note = Problem.NoisyApp.forGuardian("Ruth", text)!!
            assertFalse("$text: $note", note.contains(leak, ignoreCase = true))
        }
    }

    @Test fun aLabelThatEndsInAnAddressAfterTheCutIsStillMasked() {
        val sent = Texts.Home.guardianLabel("A very long app name that goes on and on and on before Paypa1.Com")
        assertFalse(sent, sent.contains("Paypa1", ignoreCase = true))
    }

    @Test fun onThePhoneThoseSameAddressesBecomeTheWordLink() {
        for ((text, leak) in hostile) {
            val shown = Texts.Home.withoutLinks(text)
            assertFalse("$text showed as $shown", shown.contains(leak, ignoreCase = true))
            assertTrue("$text: $shown", shown.contains("(link)"))
        }
    }

    @Test fun onThePhoneOrdinaryWordsWithADotStayWords() {
        assertEquals("Mr.Smith says your parcel is held.Please call St.Louis", Texts.Home.withoutLinks("Mr.Smith says your parcel is held.Please call St.Louis"))
        assertEquals("Dr.Lee e.g. v2.1 3.14", Texts.Home.withoutLinks("Dr.Lee e.g. v2.1 3.14"))
    }

    @Test fun aLinkWithAPathSchemeOrAtSignIsAlwaysALink() {
        for (text in listOf("https://Example.Com", "user@Evil.Site", "Mr.Smith/pay", "a.b.Com")) {
            assertTrue("$text: ${Texts.Home.withoutLinks(text)}", Texts.Home.withoutLinks(text).contains("(link)"))
        }
    }
}
