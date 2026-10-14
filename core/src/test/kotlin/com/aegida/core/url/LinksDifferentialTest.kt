package com.aegida.core.url

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** The hand scanner finds exactly the candidates the regex it replaced found, on short random text. */
class LinksDifferentialTest {
    private val old = Regex(
        """(?i)\b(?:https?://[^\s<>"'`]+|(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]*[\p{L}\p{N}])?\.)+\p{L}{2,}(?::\d+)?(?:/[^\s<>"'`]*)?)""",
    )
    private val trailing = ".,;:!?)]}'\"*"

    @Test fun sameLinksAsTheOldPatternOnRandomShortText() {
        val alphabet = listOf(
            "a", "b", "c", "1", "9", ".", ".", "-", "/", ":", "@", " ", "h", "t", "p", "s", "://", "http://", "HTTPS://", "_", "'", "\n", "?", "com", "x",
        )
        val random = Random(7)
        repeat(60_000) {
            val text = (0 until random.nextInt(1, 14)).joinToString("") { alphabet[random.nextInt(alphabet.size)] }
            val expected = old.findAll(text).mapNotNull { Links.parse(it.value.trimEnd { c -> c in trailing }) }.distinct().toList()
            assertEquals("text=[$text]", expected, Links.extract(text))
        }
    }
}
