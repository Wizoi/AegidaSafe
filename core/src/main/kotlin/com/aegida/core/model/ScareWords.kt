package com.aegida.core.model

import com.aegida.core.text.LookAlikes
import com.aegida.core.text.Texts

/** What a scary notice from a website she had or said Yes to is (table WP17 in docs/scenarios/website-popups.md). */
enum class ScareKind {
    /** A report about someone else ("Retailer hacked"): left where it is, nothing sent. */
    News,

    /**
     * Real news that speaks to the reader ("Check your phone for this spyware", "How to tell if your phone is hacked"): hidden so it
     * cannot frighten her, but not called fake, and the guardian is not told (Kevin, Oct 14; findings #133).
     */
    NewsToTheReader,

    /** A fake warning about her phone (a count of viruses, "your phone is infected", "tap to clean"): hidden, the guardian is told. */
    FakeWarning,
}

/**
 * The words of a fake warning on a website's notification ("virus", "infected", "your phone is ..."). Too broad for an app's own
 * notices, which have a narrower list in NoticeRules (findings #96).
 */
object ScareWords {
    /** Phone words and phone names: "your phone", "your Motorola". The makers' names live in Texts (OemSingleOwner). */
    private val PHONE_WORDS =
        setOf("phone", "smartphone", "cellphone", "device", "mobile", "tablet", "browser", "chrome") + Texts.PHONE_NAMES
    private val PHONES = PHONE_WORDS.joinToString("|")

    /** The gate, read on [ScareText.plain] words: without one of these a website's notice is not scary at all. */
    val REGEX = Regex(
        """\b(virus(es)?|infected|malware|hacked|trojan|spyware)\b|battery\b[^.!?]{0,40}\bdamaged|security (alert|warning)""" +
            """|clean (your|now|it)|your ($PHONES) (is|has been|needs cleaning)|\b($PHONES) (will|may|might|could) be damaged""",
    )

    /** Whether a website's title and text carry a scare word, look-alike letters undone ("Vіrus", "V1rus", maths-bold letters). */
    fun anyIn(title: String, text: String): Boolean = REGEX.containsMatchIn(ScareText.plain("$title $text"))

    /**
     * News, news to the reader, or a fake warning (findings #109, #126, #133). Read one clause at a time, look-alike letters undone.
     * A fake warning says something about her phone or tells her to act: a count of viruses ("13 viruses found", "has 3 viruses"),
     * her phone or she in a bad state ("your phone is infected", "Trojan detected on this phone", "you may have a virus"), a damaged
     * battery, or an order to act ("Tap to fix", "Clean it now"; an order word counts only when a command word follows, so "Tap water"
     * and "Tap to read the full story" are not). Speaking to the reader without any of that ("Check your phone for this spyware", a
     * question "Is your phone infected?", "Delete these apps now") is news to the reader. Anything else is news.
     */
    fun kindOf(title: String, text: String): ScareKind {
        val clauses = listOf(title, text).flatMap { ScareText.clausesOf(ScareText.plain(it)) }
        return when {
            clauses.any(::isFake) -> ScareKind.FakeWarning
            clauses.any(::speaksToTheReader) -> ScareKind.NewsToTheReader
            else -> ScareKind.News
        }
    }

    private fun isFake(w: List<String>): Boolean {
        // A question or a condition is not a claim: "Is your phone infected?", "How to tell if your phone is hacked".
        val asked = { i: Int -> w.first() in QUESTION_STARTS || w.take(i).any { it in CONDITIONS } }
        // An order to act on her phone. "Delete these apps now" is news to the reader unless the clause names a virus or says "it".
        val verb = orderWord(w)
        val ordersHer = verb != null && (verb != "delete" || w.any { it in FOUND_THINGS || it == "it" || it == "them" })
        return ordersHer || ("battery" in w && "damaged" in w) ||
            w.indices.any { i -> countedFind(w, i) || (!asked(i) && (phoneInTrouble(w, i) || herselfInTrouble(w, i))) }
    }

    private fun speaksToTheReader(w: List<String>): Boolean =
        orderWord(w) != null || w.indices.any { i -> herPhoneAt(w, i) || herselfInTrouble(w, i) }

    /** The order word starting the clause (after "please") when a command word follows it: "Tap to fix", not "Tap water". */
    private fun orderWord(w: List<String>): String? {
        val start = w.indexOfFirst { it != "please" }
        val next = w.getOrNull(start + 1)
        val commanded = if (next == "to") w.getOrNull(start + 2) in ORDERS else next in COMMAND_NEXT || next in FOUND_THINGS
        return w.getOrNull(start)?.takeIf { it in ORDERS && commanded }
    }


    /** "your phone", "this device", "your Motorola", or "your <name> is ... infected" with a phone word or model number in the name. */
    private fun herPhoneAt(w: List<String>, i: Int): Boolean = when (w[i]) {
        "this" -> w.getOrNull(i + 1) in THIS_PHONE
        "your" -> w.getOrNull(i + 1) in PHONE_WORDS || namedPhoneIs(w, i + 1)
        else -> false
    }

    /** Her phone, then a bad state after it ("your phone is infected"), or something found "on this phone". */
    private fun phoneInTrouble(w: List<String>, i: Int): Boolean =
        herPhoneAt(w, i) && (w.drop(i + 2).any { it in STATES } || (w.getOrNull(i - 1) in ON && w.any { it in FOUND }))

    private fun namedPhoneIs(w: List<String>, start: Int): Boolean {
        val linkAt = (start + 1..minOf(start + MAX_NAME_WORDS, w.lastIndex)).firstOrNull { w[it] in LINKS }
        val name = if (linkAt == null) emptyList() else w.subList(start, linkAt)
        val state = if (linkAt == null) null else w.drop(linkAt).firstOrNull { it !in LINKS }
        return state in STATES && name.any { it in PHONE_WORDS || it.any(Char::isDigit) }
    }

    /** "you are infected", "you got hacked", "you have a virus", "you may have 3 viruses", "you've got a virus". */
    private fun herselfInTrouble(w: List<String>, i: Int): Boolean {
        if (w[i] !in YOU) return false
        val after = w.drop(i + 1)
        val state = after.firstOrNull { it !in LINKS }
        val have = after.dropWhile { it in MAYBE }
        val owns = have.firstOrNull() in HAVE
        val howMany = have.getOrNull(1).orEmpty()
        return state in STATES || (owns && (howMany in SOME || howMany.all(Char::isDigit)) && have.getOrNull(2) in FOUND_THINGS)
    }

    /** "13 viruses found", "(3) viruses were found", "infected with 13 viruses": a number right before what was found. */
    private fun countedFind(w: List<String>, i: Int): Boolean =
        w[i].all(Char::isDigit) && w.getOrNull(i + 1) in FOUND_THINGS &&
            (i + 1 == w.lastIndex || w.drop(i + 2).firstOrNull { it !in LINKS } in FOUND)

    private const val MAX_NAME_WORDS = 4
    private val ORDERS = setOf("tap", "click", "press", "clean", "remove", "delete", "download", "install", "scan", "fix", "repair")
    private val COMMAND_NEXT = setOf(
        "to", "here", "ok", "now", "it", "them", "the", "this", "these", "your", "up", "all", "immediately", "cleaner",
    )
    private val THIS_PHONE = setOf("phone", "device", "mobile", "smartphone") // "this Android malware" is news
    private val LINKS = setOf("is", "are", "was", "were", "has", "have", "been", "may", "might", "could", "be", "now", "being", "got", "will")
    val STATES = setOf("infected", "hacked", "compromised", "damaged", "cleaning")
    private val YOU = setOf("you", "you're", "you've")
    private val MAYBE = setOf("may", "might", "could")
    private val HAVE = setOf("have", "got")
    private val SOME = setOf("a", "an", "some")
    private val ON = setOf("on", "in")
    private val QUESTION_STARTS = setOf(
        "is", "are", "was", "were", "has", "have", "had", "do", "does", "did", "can", "could", "should", "will", "would", "how", "what",
        "why", "when", "which", "who", "where",
    )
    private val CONDITIONS = setOf("if", "whether", "how", "what", "why", "when")
    val FOUND_THINGS = setOf("virus", "viruses", "malware", "trojan", "trojans", "spyware")
    val FOUND = setOf("found", "detected")
}

/** A website's words made plain for [ScareWords]: look-alike letters undone, then cut into clauses of words. */
internal object ScareText {
    /** NFKC, lower case, look-alike letters as Latin, and a word with digits for letters read as the scare word it spells ("v1rus"). */
    fun plain(text: String): String = TOKEN.replace(LookAlikes.plain(text).replace('’', '\'')) { m -> spelled(m.value) }

    private fun spelled(token: String): String {
        if (token.none(Char::isDigit) || token.none(Char::isLetter)) return token
        return listOf('i', 'l').map { one -> token.map { DIGIT_LETTERS[it] ?: if (it == '1') one else it }.joinToString("") }
            .firstOrNull { it in SPELLED } ?: token
    }

    /** Clauses split at punctuation, brackets and a spaced dash, each as its words; "(3)" is a count, not a clause. */
    fun clausesOf(text: String): List<List<String>> = text.replace(BRACKETED_NUMBER, " $1 ").split(CLAUSE_BREAK)
        .map { clause -> WORD.findAll(clause).map { it.value.trim('-', '\'') }.filter { it.isNotEmpty() }.toList() }
        .filter { it.isNotEmpty() }

    private val TOKEN = Regex("""[\p{L}\p{N}@]+""")
    private val BRACKETED_NUMBER = Regex("""[(\[](\d+)[)\]]""")
    private val CLAUSE_BREAK = Regex("""[.!?:;,|()\[\]]|\s[-–—]\s|[–—]""")
    private val WORD = Regex("""[\p{L}\p{N}'-]+""")
    private val DIGIT_LETTERS = mapOf('0' to 'o', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a')
    private val SPELLED = ScareWords.FOUND_THINGS + ScareWords.STATES + ScareWords.FOUND + "cleaner"
}
