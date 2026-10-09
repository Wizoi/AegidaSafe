package com.aegida.core.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/** Review finding R11: a long dotted run must not overflow the stack and abort the whole check. */
class LinksLongTextTest {
    /** Runs on a small stack, like the reviewer's repro, so a recursive matcher fails here. */
    private fun <T> onSmallStack(block: () -> T): T {
        var result: Result<T>? = null
        val thread = Thread(null, { result = runCatching(block) }, "small-stack", 256 * 1024)
        thread.start()
        thread.join()
        return result!!.getOrThrow()
    }

    @Test fun aTwentyThousandCharacterDottedRunDoesNotThrowAndIsFast() {
        val text = "a.".repeat(10_000)
        var links = emptyList<Link>()
        val ms = measureTimeMillis { links = onSmallStack { Links.extract(text) } }
        assertTrue("took $ms ms", ms < 2_000)
        assertTrue(links.size <= 1)
    }

    @Test fun aScamLinkAfterThreeThousandCharactersOfPaddingWithinTheCapIsStillFound() {
        val text = "a.".repeat(1_500) + " verify at http://examp1e-bank.test/login now"
        assertEquals(listOf("examp1e-bank.test"), onSmallStack { Links.extract(text) }.map { it.host })
    }

    @Test fun aBareLinkAfterPaddingIsFoundToo() {
        val text = "a.".repeat(1_500) + " usps-redelivery.example/track"
        assertEquals(listOf("usps-redelivery.example"), onSmallStack { Links.extract(text) }.map { it.host })
    }

    @Test fun textBeyondTheCapOfOnePartIsNotExamined() {
        val text = "x ".repeat(Links.MAX_PART_CHARS) + " http://beyond-the-cap.example/"
        assertEquals(emptyList<Link>(), onSmallStack { Links.extract(text) })
    }

    @Test fun eachPartHasItsOwnCapSoALongTitleCannotHideALinkInTheText() {
        val title = "x ".repeat(Links.MAX_PART_CHARS)
        val text = "pay at http://examp1e-bank.test/login"
        assertEquals(listOf("examp1e-bank.test"), Links.extractParts(title, text).map { it.host })
    }
}
