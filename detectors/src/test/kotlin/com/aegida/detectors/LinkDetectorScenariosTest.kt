package com.aegida.detectors

import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.message
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The scenario tables of findings #104 and #105, one test per row: odd forms of a scam link are warned about like the plain link,
 * and ordinary text written with Japanese full stops or middle dots never is.
 */
class LinkDetectorScenariosTest {
    private val detector = LinkDetector()
    private fun problems(text: String) = detector.evaluate(inputs(event = message(text))).map { it.key.problem to it.key.subject }

    private val plain = listOf(Problem.LookalikeLink to "paypa1-help.com")

    private fun problemsAllowing(text: String, sites: Set<String>) =
        detector.evaluate(inputs(event = message(text)).copy(allowedSites = sites)).map { it.key.problem to it.key.subject }

    /** ST24 (findings #155): only the very name she said is fine is quiet; another name on the same domain, or a look-alike of it, is not. */
    @Test fun onlyTheWebsiteSheSaidIsFineIsQuiet() {
        val allowed = setOf("paypa1-help.com")
        assertEquals(emptyList<Any>(), problemsAllowing("Refund: paypa1-help.com/claim", allowed))
        assertEquals(listOf(Problem.LookalikeLink to "login.paypa1-help.com"), problemsAllowing("Refund: login.paypa1-help.com/claim", allowed))
        assertEquals(listOf(Problem.LookalikeLink to "paypa1-help.net"), problemsAllowing("Refund: paypa1-help.net/claim", allowed))
        assertEquals(listOf(Problem.LookalikeLink to "paypa1-he1p.com"), problemsAllowing("Refund: paypa1-he1p.com/claim", allowed))
        assertEquals(listOf(Problem.LookalikeLink to "www.paypa1-help.com"), problemsAllowing("Refund: www.paypa1-help.com/claim", allowed))
        // Written in capitals or with a trailing dot it is still the same name (the link's host is made lower case without the dot).
        assertEquals(emptyList<Any>(), problemsAllowing("Refund: PAYPA1-HELP.COM./claim", allowed))
    }

    /** ST26, must never: a site on the scam list is warned about even when she said it is fine. */
    @Test fun aSiteSheSaidIsFineIsStillWarnedWhenItIsOnTheScamList() {
        val listed = inputs(event = message("Refund: evil.example/claim")).copy(allowedSites = setOf("evil.example"))
        assertEquals(listOf(Problem.ScamLink), detector.evaluate(listed).map { it.key.problem })
    }

    @Test fun anIdeographicOrFullwidthDotIsStillTheLink() {
        assertEquals(plain, problems("Refund: paypa1-help。com/claim"))
        assertEquals(plain, problems("Refund: paypa1-help．com/claim"))
    }

    @Test fun aDotLookAlikeIsStillTheLink() {
        assertEquals(plain, problems("Refund: paypa1-help․com/claim"))
    }

    @Test fun anInvisibleCharacterInsideIsStillTheLink() {
        assertEquals(plain, problems("Refund: paypa1-help​.com/claim"))
        assertEquals(plain, problems("Refund: paypa1-help.c͏om/claim"))
    }

    @Test fun aLinkInsideAFragmentThatDoesNotParseIsStillFound() {
        assertEquals(plain, problems("Refund: xא.paypa1-help.com/claim"))
        assertEquals(plain, problems("Refund: " + "a".repeat(70) + ".paypa1-help.com/claim"))
    }

    @Test fun ordinaryWordsWithADotAndPlainLinksAreNeverWarnedAbout() {
        assertEquals(emptyList<Any>(), problems("Mr.Smith says your parcel is held.Please read file.txt, e.g. now"))
        assertEquals(emptyList<Any>(), problems("Lunch menu at https://www.example.org/menu"))
    }

    @Test fun aShortenerAloneIsNeverWarnedAbout() { // findings #105: a row without a test
        assertEquals(emptyList<Any>(), problems("See bit.ly/abc123 for the photos"))
    }

    /** Findings #105: a real Japanese address writes Kanji and Kana together; that is one script, not a look-alike. */
    @Test fun aJapaneseAddressMixingKanjiAndKanaIsNotALookAlike() {
        assertEquals(emptyList<Any>(), problems("Visit 日本のお店.jp today"))
    }

    /** Findings #105: text that only looks like a link once its dots are prepared, with no real ending, is never a warning. */
    @Test fun japaneseSentencesAndMiddleDotsBetweenWordsAreNeverWarnedAbout() {
        for (text in listOf(
            "ご注文ありがとうございます。お届けは明日です。",
            "Deliver to 東京都渋谷区。ヤマト運輸",
            "Amazon・プライムの会員登録",
            "PayPal·Venmo transfer done",
            "Price·paypal",
            "Meet near Costco·Main St",
            "Your order from Amazon。Track it",
        )) {
            assertEquals(text, emptyList<Any>(), problems(text))
        }
    }
}
