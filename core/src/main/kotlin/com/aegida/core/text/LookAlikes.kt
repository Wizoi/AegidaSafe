package com.aegida.core.text

import java.text.Normalizer

/**
 * Letters that look like Latin ones, undone the same way for a link's name (LinkSigns) and a website's words (ScareWords), so the
 * two cannot drift apart (findings #108, #133).
 */
object LookAlikes {
    /** Cyrillic and Greek letters scams use in place of the Latin letter they look like. */
    val LETTERS: Map<Char, Char> = mapOf(
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x', 'і' to 'i',
        'ј' to 'j', 'ѕ' to 's', 'ԁ' to 'd', 'ɡ' to 'g', 'ο' to 'o', 'α' to 'a', 'ν' to 'v', 'ρ' to 'p', 'κ' to 'k',
        'ӏ' to 'l', // the palochka: "аррӏе" is all Cyrillic, so not mixed, yet reads as "apple" (findings #108)
    )

    /** Each look-alike letter as the Latin letter it imitates. */
    fun latin(text: String): String = text.map { LETTERS[it] ?: it }.joinToString("")

    /** Maths-bold and full-width letters as plain ones (NFKC), lower case, then [latin]: "𝐕𝐢𝐫𝐮𝐬" and "Vіrus" read "virus". */
    fun plain(text: String): String = latin(Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase())
}
