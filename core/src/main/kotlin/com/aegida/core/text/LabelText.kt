package com.aegida.core.text

import com.aegida.core.text.AddressWords.isOrdinaryWord
import com.aegida.core.text.AddressWords.looksLikeAddress
import com.aegida.core.url.HostMask
import com.aegida.core.url.LinkText
import com.aegida.core.url.Links

/** How a label, a sender or a bit of message is cleaned before it is shown or sent. [Texts.Home] is the one place callers use. */
internal object LabelText {
    private val WORD_GAPS = Regex("""[\s\p{Z}]+""")

    /**
     * [text] for the phone's screens. Invisible characters are removed and dot look-alikes become dots first; then a word that looks
     * like an address at all (see [looksLikeAddress]) shows as "(link)" whole. Only an ordinary word with a dot stays (findings #99-#101).
     */
    fun withoutLinks(text: String): String =
        LinkText.prepare(text).split(WORD_GAPS).joinToString(" ") { word ->
            if (looksLikeAddress(word) && !isOrdinaryWord(word)) Texts.Home.LINK_REMOVED else word
        }

    /**
     * An app's label as it may be sent off the phone. After the same preparation, a word that looks like an address is sent as the
     * masks of the addresses found in it, or as `*****` when none can be read; never any part of the word. Then it is cut short.
     */
    fun guardianLabel(raw: String): String =
        cleanLabel(
            cleanLabel(LinkText.prepare(raw), Int.MAX_VALUE).split(WORD_GAPS).joinToString(" ") { word ->
                if (!looksLikeAddress(word)) {
                    word
                } else {
                    Links.findThorough(word).map { HostMask.of(it.host) }.ifEmpty { listOf(HostMask.of("")) }.joinToString(" ")
                }
            },
            Texts.Home.CONFIRM_LABEL_MAX,
        )

    fun cleanLabel(raw: String, max: Int): String {
        val spaced = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            i += Character.charCount(cp)
            val type = Character.getType(cp)
            val blocked = type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt() ||
                type == Character.LINE_SEPARATOR.toInt() || type == Character.PARAGRAPH_SEPARATOR.toInt()
            spaced.append(if (blocked) " " else String(Character.toChars(cp)))
        }
        val text = spaced.toString().trim().replace(Regex(" {2,}"), " ")
        if (text.codePointCount(0, text.length) <= max) return text
        return text.substring(0, text.offsetByCodePoints(0, max)).trimEnd() + "…"
    }
}
