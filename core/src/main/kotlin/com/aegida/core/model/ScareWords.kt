package com.aegida.core.model

/**
 * The words of a fake warning on a website's notification ("virus", "infected", "your phone is ..."). Too broad for an app's own
 * notices, which have a narrower list in NoticeRules (findings #96).
 */
object ScareWords {
    val REGEX = Regex(
        """(?i)\b(virus(es)?|infected|malware|hacked|trojan|spyware)\b|battery (is )?damaged|security (alert|warning)""" +
            """|clean (your|now|it)|your (phone|device) (is|has been)""",
    )

    /**
     * Whether scare words are aimed at her own phone rather than news about someone else (findings #109, #126). One rule: it speaks
     * to her or her phone, or tells her to act. Read one clause at a time: "your/this phone", "your <phone's name> is infected" (a
     * name of one word, or one with a phone word or a model number in it, so "your favourite retailer" stays news), "you are
     * infected", an order starting the clause ("Tap to fix", not "Google to remove 40 apps"), a count of viruses found ("13 viruses
     * found", not "two new viruses found in deer"), or a damaged battery. The table is WP17 in docs/scenarios/website-popups.md.
     */
    fun isAboutHerPhone(title: String, text: String): Boolean =
        listOf(title, text).flatMap { it.lowercase().split(CLAUSE_BREAK) }.map(::wordsOf).any(::speaksToHerOrOrders)

    private fun wordsOf(clause: String): List<String> =
        WORD.findAll(clause.replace('’', '\'')).map { it.value.trim('-', '\'') }.filter { it.isNotEmpty() }.toList()

    private fun speaksToHerOrOrders(w: List<String>): Boolean =
        isOrder(w) || w.indices.any { i -> herPhone(w, i) || herself(w, i) || countedFind(w, i) } || ("battery" in w && "damaged" in w)

    private fun isOrder(w: List<String>): Boolean = w.firstOrNull { it != "please" } in ORDERS

    /** "your phone", "this device", or "your <name> is ... infected". */
    private fun herPhone(w: List<String>, i: Int): Boolean = when (w[i]) {
        "this" -> w.getOrNull(i + 1) in THIS_PHONE
        "your" -> w.getOrNull(i + 1) in PHONE_WORDS || namedPhoneIs(w, i + 1)
        else -> false
    }

    /** "<name> is ... infected" from [start], where the name is one word or has a phone word or a model number in it. */
    private fun namedPhoneIs(w: List<String>, start: Int): Boolean {
        val linkAt = (start + 1..minOf(start + MAX_NAME_WORDS, w.lastIndex)).firstOrNull { w[it] in LINKS }
        val name = if (linkAt == null) emptyList() else w.subList(start, linkAt)
        val state = if (linkAt == null) null else w.drop(linkAt).firstOrNull { it !in LINKS }
        return state in STATES && (name.size == 1 || name.any { it in PHONE_WORDS || it.any(Char::isDigit) })
    }

    /** "you are infected", "you've been hacked", "you have a virus", "you have 3 viruses". */
    private fun herself(w: List<String>, i: Int): Boolean {
        if (w[i] !in YOU) return false
        val after = w.drop(i + 1)
        val state = after.firstOrNull { it !in LINKS }
        val howMany = after.getOrNull(1).orEmpty()
        val hasOne = after.firstOrNull() == "have" && (howMany == "a" || howMany.all(Char::isDigit)) && after.getOrNull(2) in FOUND_THINGS
        return state in STATES || hasOne
    }

    /** "13 viruses found", "(3) viruses were found": a number right before what was found. */
    private fun countedFind(w: List<String>, i: Int): Boolean =
        w[i].all(Char::isDigit) && w.getOrNull(i + 1) in FOUND_THINGS && w.drop(i + 2).firstOrNull { it !in LINKS } in FOUND

    private val CLAUSE_BREAK = Regex("""[.!?:;,|]|\s[-–—]\s|[–—]""")
    private val WORD = Regex("""[\p{L}\p{N}'-]+""")
    private const val MAX_NAME_WORDS = 4
    private val ORDERS = setOf("tap", "click", "press", "clean", "remove", "delete", "download", "install", "scan", "fix", "repair")
    private val PHONE_WORDS = setOf(
        "phone", "smartphone", "cellphone", "device", "mobile", "android", "iphone", "galaxy", "pixel", "tablet", "browser", "chrome",
    )
    private val THIS_PHONE = setOf("phone", "device", "mobile", "smartphone") // "this Android malware" is news
    private val LINKS = setOf("is", "are", "was", "were", "has", "have", "been", "may", "might", "could", "be", "now", "being", "got")
    private val STATES = setOf("infected", "hacked", "compromised", "damaged")
    private val YOU = setOf("you", "you're", "you've")
    private val FOUND_THINGS = setOf("virus", "viruses", "malware", "trojan", "trojans", "spyware")
    private val FOUND = setOf("found", "detected")
}
