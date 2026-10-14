package com.aegida.core.text

import com.aegida.core.url.Links
import com.aegida.core.url.TopLevelDomains

/** Which words of a message look like a web address, for the screens that hide and mask them ([Texts.Home.withoutLinks], guardianLabel). */
internal object AddressWords {
    private const val LAST_ASCII_CODE = 127
    private const val PATH_SIGNS = "/?@:"

    /** Fails closed: a scheme, a dot followed by two letters, an IPv4 address, or anything the link finder sees. */
    fun looksLikeAddress(word: String): Boolean =
        word.contains("://") || DOT_LETTERS.containsMatchIn(word) || IPV4.containsMatchIn(word) || Links.findThorough(word).isNotEmpty()

    /**
     * "Mr.Smith", "St.Louis", "held.Please" (no space after a full stop): plain ASCII, two parts, no scheme, path or @, the first a
     * title or a lowercase word, and the ending a capitalised word in no IANA top-level-domain list. Only for what Mom sees.
     */
    fun isOrdinaryWord(word: String): Boolean {
        val w = word.trim { !it.isLetterOrDigit() }
        val parts = w.split('.')
        val plainAscii = !(w.any { it.code > LAST_ASCII_CODE } || w.any { it in PATH_SIGNS })
        return plainAscii && parts.size == 2 && isCapitalisedWord(parts[1]) && !TopLevelDomains.isDomain(parts[1]) && isTitleOrLowercase(parts[0])
    }

    private fun isCapitalisedWord(ending: String) = ending.length >= 2 && ending.first().isUpperCase() && ending.drop(1).all { it.isLowerCase() }

    private fun isTitleOrLowercase(first: String) = first in TITLES || (first.isNotEmpty() && first.all { it.isLowerCase() })

    /** A dot and two letters, with any marks between them ("evil.ćom" too, findings #102). */
    private val DOT_LETTERS = Regex("""\.\p{M}*\p{L}(\p{M}*\p{L})+""")
    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val TITLES = setOf("Mr", "Mrs", "Ms", "Dr", "St", "Mt", "Jr", "Sr", "Ft")
}
