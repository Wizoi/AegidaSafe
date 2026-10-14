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
