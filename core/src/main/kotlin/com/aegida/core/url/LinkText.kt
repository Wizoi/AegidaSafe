package com.aegida.core.url

/**
 * The one preparation of text before looking for web addresses, used by the scam-link detector and by the screens that hide and
 * mask addresses (findings #101, #102, #104), so the two can never disagree about what an address is.
 */
object LinkText {
    /**
     * Removes invisible and format characters (zero-width ones, soft hyphen, direction marks: an address split by one is still the
     * address to a browser) and turns every dot look-alike into "." (findings #101).
     */
    fun prepare(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            when {
                Character.getType(cp) == Character.FORMAT.toInt() || isIgnoredByBrowsers(cp) -> Unit
                cp in DOT_LOOK_ALIKES -> out.append('.')
                else -> out.appendCodePoint(cp)
            }
        }
        return out.toString()
    }

    /** Default-ignorable code points a browser drops from an address (Unicode's list, beyond the format characters). */
    private fun isIgnoredByBrowsers(cp: Int): Boolean =
        cp == 0x034F || cp in 0x115F..0x1160 || cp in 0x17B4..0x17B5 || cp in 0x180B..0x180F || cp == 0x3164 ||
            cp in 0xFE00..0xFE0F || cp == 0xFFA0 || cp in 0xE0000..0xE0FFF

    private val DOT_LOOK_ALIKES = setOf(
        0x2024, 0xFE52, 0xFE12, 0xFF0E, 0x3002, 0xFF61, 0x00B7, 0x0701, 0x2E3C, 0x2219, 0x22C5, 0x30FB,
        0x2027, 0x0387, 0x16EB, 0x2E31, 0xFF65, 0xA78F, 0x1427, // findings #102
    )
}
