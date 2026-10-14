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
        // findings #100
        "go.io,paypal-secure.com/go.io" to "paypal-secure",
        "pal.com,paypal.com" to "paypal",
        "evil.top,secure-login.evil.top" to "secure-login",
        "xא.evil.com" to "evil",
        "a".repeat(70) + ".usps-redeliver.com" to "usps-redeliver",
        "paypal。com" to "paypal",
        "paypal．com" to "paypal",
        // findings #101
        "evil․com" to "evil",
        "evil﹒com" to "evil",
        "evil·com" to "evil",
        "evil​.com" to "evil",
        "paypal­.com" to "paypal",
        "evil.c​om" to "evil",
        "evil⁠.com" to "evil",
        "http://evil%2Ecom/login" to "evil",
        "http://3232235777/x" to "3232235777",
        "https://evil/x" to "evil",
        "192.168.1.5/login" to "192.168",
        "pay­pal.com" to "pay",
        "secure_paypal.com" to "paypal", // the link finder does not start after "_"; the dot-and-letters rule still hides it
        "_evil.com" to "evil",
        // findings #102: characters a browser ignores, a visible mark, more dot look-alikes
        "evil.c͏om" to "evil",
        "paypal.͏com/login" to "paypal",
        "evil.️com" to "evil",
        "evil.c︀om" to "evil",
        "evil.᠋com" to "evil",
        "evil.ćom" to "evil",
        "evil‧com" to "evil",
        "evil·com" to "evil",
        "evil⸱com" to "evil",
        "evil･com" to "evil",
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

    @Test fun aCharacterBrowsersIgnoreIsRemovedSoTheGuardianStillGetsAUsefulMask() { // findings #102
        assertEquals("ev*****.com", Texts.Home.guardianLabel("evil.c͏om"))
        assertEquals("ev*****.com", Texts.Home.guardianLabel("evil.️com"))
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

    @Test fun onThePhoneARealDomainEndingIsALinkEvenAfterATitle() { // findings #100
        for (text in listOf("Dr.Bank", "usps.Sbs", "irs.Gov", "amazon.Lol", "chase.Bond", "St.Shop", "paypal.Ltd")) {
            assertEquals(text, "(link)", Texts.Home.withoutLinks(text))
        }
    }

    @Test fun onThePhoneOnlyPlainWordsArePassedNotOtherAlphabets() { // findings #101
        assertEquals("(link)", Texts.Home.withoutLinks("пример.Рф"))
    }

    @Test fun aLinkWithAPathSchemeOrAtSignIsAlwaysALink() {
        for (text in listOf("https://Example.Com", "user@Evil.Site", "Mr.Smith/pay", "a.b.Com")) {
            assertTrue("$text: ${Texts.Home.withoutLinks(text)}", Texts.Home.withoutLinks(text).contains("(link)"))
        }
    }

    @Test fun aLinkIsTakenOutOfTheWordsByTheSameCodeThatFindsLinksNotByTheStoredHost() { // finding #94
        val lookalike = Texts.Home.saysLine("Refund at \u0440\u0430ypal.com/claim now", "xn--ypal-43d9g.com")!!
        assertFalse(lookalike, lookalike.contains("ypal"))
        val two = Texts.Home.saysLine("Pay at evil.example/x or other-site.example/y today", "evil.example")!!
        assertFalse(two, two.contains("other-site") || two.contains("evil"))
        assertEquals("From: “Visit (link) now”", Texts.Home.senderLine("Visit paypa1-help.example now"))
    }

    @Test fun aWebsitePopUpTitleNeverShowsALinkAsText() { // finding #98
        val said = Texts.Home.saidLine("Infected! Go to cleaner-app.top now")!!
        assertFalse(said, said.contains("cleaner-app"))
    }

    @Test fun ordinaryWordsWithADotAreNotTakenForLinks() { // finding #98
        assertEquals("Mr.Smith says your parcel is held.Please call St.Louis", Texts.Home.withoutLinks("Mr.Smith says your parcel is held.Please call St.Louis"))
    }

    @Test fun aLongLabelEndingInAnAddressIsMaskedBeforeItIsCut() { // finding #98
        val label = Texts.Home.guardianLabel("A very long app name that goes on and on before evil-site.com")
        assertFalse(label, label.contains("evil-site") || label.contains("evil-si"))
    }

    @Test fun anAppNamedLikeAWebAddressReachesTheGuardianMasked() { // findings #94, #96
        val note = Problem.NoisyApp.forGuardian("Ruth", "evil-site.com")!!
        assertFalse(note, note.contains("evil-site.com"))
        assertTrue(note, note.contains("ev*****.com"))
        assertFalse(Texts.Guardian.ALLOWED.format("Ruth", Texts.Home.guardianLabel("Get evil-site.com")).contains("evil-site.com"))
    }
}
