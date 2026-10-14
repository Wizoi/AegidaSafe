package com.aegida.detectors

import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.inputs
import com.aegida.detectors.Fixtures.message
import org.junit.Assert.assertEquals
import org.junit.Test

/** The scenario table of findings #104, one test per row: odd forms of a scam link are warned about like the plain link. */
class LinkDetectorScenariosTest {
    private val detector = LinkDetector()
    private fun problems(text: String) = detector.evaluate(inputs(event = message(text))).map { it.key.problem to it.key.subject }

    private val plain = listOf(Problem.LookalikeLink to "paypa1-help.example")

    @Test fun anIdeographicOrFullwidthDotIsStillTheLink() {
        assertEquals(plain, problems("Refund: paypa1-help。example/claim"))
        assertEquals(plain, problems("Refund: paypa1-help．example/claim"))
    }

    @Test fun aDotLookAlikeIsStillTheLink() {
        assertEquals(plain, problems("Refund: paypa1-help․example/claim"))
    }

    @Test fun anInvisibleCharacterInsideIsStillTheLink() {
        assertEquals(plain, problems("Refund: paypa1-help​.example/claim"))
        assertEquals(plain, problems("Refund: paypa1-help.ex͏ample/claim"))
    }

    @Test fun aLinkInsideAFragmentThatDoesNotParseIsStillFound() {
        assertEquals(plain, problems("Refund: xא.paypa1-help.example/claim"))
        assertEquals(plain, problems("Refund: " + "a".repeat(70) + ".paypa1-help.example/claim"))
    }

    @Test fun ordinaryWordsWithADotAndPlainLinksAreNeverWarnedAbout() {
        assertEquals(emptyList<Any>(), problems("Mr.Smith says your parcel is held.Please read file.txt, e.g. now"))
        assertEquals(emptyList<Any>(), problems("Lunch menu at https://www.example.org/menu"))
    }
}
