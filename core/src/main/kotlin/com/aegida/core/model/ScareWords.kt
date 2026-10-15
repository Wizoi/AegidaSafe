package com.aegida.core.model

import com.aegida.core.text.LookAlikes
import com.aegida.core.text.Texts

/**
 * The words of an alarming notice from a website ("virus", "infected", "your phone is ..."). Too broad for an app's own notices,
 * which have a narrower list in NoticeRules (findings #96). Whether such a notice is news or a fake is not guessed (findings #148).
 */
object ScareWords {
    /** Phone words and phone names: "your phone", "your Motorola". The makers' names live in Texts (OemSingleOwner). */
    private val PHONES =
        (setOf("phone", "smartphone", "cellphone", "device", "mobile", "tablet", "browser", "chrome") + Texts.PHONE_NAMES).joinToString("|")

    /** The gate, read on [ScareText.plain] words: without one of these a website's notice is not alarming at all. */
    val REGEX = Regex(
        """\b(virus(es)?|infected|malware|hacked|trojan|spyware)\b|battery\b[^.!?]{0,40}\bdamaged|security (alert|warning)""" +
            """|clean (your|now|it)|your ($PHONES) (is|has been|needs cleaning)|\b($PHONES) (will|may|might|could) be damaged""" +
            """|\b($PHONES)\b[^.!?]{0,40}\?+\W*it (will|may|might|could) be damaged""",
    )

    /** Whether a website's title and text carry a scare word, look-alike letters undone ("Vіrus", "V1rus", maths-bold letters). */
    fun anyIn(title: String, text: String): Boolean = REGEX.containsMatchIn(ScareText.plain("$title $text"))
}

/** A website's words made plain for [ScareWords]: look-alike letters undone. */
internal object ScareText {
    /** NFKC, lower case, look-alike letters as Latin, and a word with digits for letters read as the scare word it spells ("v1rus"). */
    fun plain(text: String): String = TOKEN.replace(LookAlikes.plain(text).replace('’', '\'')) { m -> spelled(m.value) }

    private fun spelled(token: String): String {
        if (token.none(Char::isDigit) || token.none(Char::isLetter)) return token
        return listOf('i', 'l').map { one -> token.map { DIGIT_LETTERS[it] ?: if (it == '1') one else it }.joinToString("") }
            .firstOrNull { it in SPELLED } ?: token
    }

    private val TOKEN = Regex("""[\p{L}\p{N}@]+""")
    private val DIGIT_LETTERS = mapOf('0' to 'o', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a')

    /** The scare words a word with digits for letters may spell ("v1rus", "1nfected", "h4cked"). */
    private val SPELLED = setOf(
        "virus", "viruses", "malware", "trojan", "trojans", "spyware", "infected", "hacked", "compromised", "damaged", "cleaning",
        "found", "detected", "cleaner",
    )
}
