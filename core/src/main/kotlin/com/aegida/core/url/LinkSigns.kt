package com.aegida.core.url

import java.lang.Character.UnicodeScript
import java.net.IDN

/** Things about a link that can be judged from its text alone, without fetching it. */
enum class LinkSign {
    /** A link shortener: where it goes cannot be checked without opening it, which Aegida never does. */
    Shortener,

    /** One part of the name mixes alphabets (for example Latin and Cyrillic letters): a classic look-alike. */
    MixedScript,

    /** The name imitates a well-known brand (for example "paypa1-login.example") but is not the brand's site. */
    BrandLookalike,
}

object LinkSigns {
    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "ow.ly", "buff.ly", "rebrand.ly", "cutt.ly",
        "shorturl.at", "tiny.cc", "rb.gy", "t.ly", "s.id", "v.gd", "short.gy", "lnkd.in",
    )

    /** Brand word -> the domains that really belong to it. A look-alike uses the word on any other domain. */
    private val BRANDS = mapOf(
        "paypal" to setOf("paypal.com"),
        "amazon" to setOf("amazon.com", "amazon.co.uk", "amazon.ca"),
        "apple" to setOf("apple.com", "icloud.com"),
        "icloud" to setOf("icloud.com", "apple.com"),
        "microsoft" to setOf("microsoft.com", "live.com", "office.com"),
        "google" to setOf("google.com"),
        "netflix" to setOf("netflix.com"),
        "wellsfargo" to setOf("wellsfargo.com"),
        "chase" to setOf("chase.com"),
        "bankofamerica" to setOf("bankofamerica.com"),
        "usps" to setOf("usps.com"),
        "fedex" to setOf("fedex.com"),
        "ups" to setOf("ups.com"),
        "irs" to setOf("irs.gov"),
        "medicare" to setOf("medicare.gov"),
        "ssa" to setOf("ssa.gov"),
        "venmo" to setOf("venmo.com"),
        "zelle" to setOf("zellepay.com"),
        "facebook" to setOf("facebook.com"),
        "walmart" to setOf("walmart.com"),
        "costco" to setOf("costco.com"),
    )

    // Common swaps scam domains use to look like a brand: Cyrillic/Greek letters that look Latin, then digits.
    private val HOMOGLYPHS = mapOf(
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x', 'і' to 'i',
        'ј' to 'j', 'ѕ' to 's', 'ԁ' to 'd', 'ɡ' to 'g', 'ο' to 'o', 'α' to 'a', 'ν' to 'v', 'ρ' to 'p', 'κ' to 'k',
    )
    private val DELEET = listOf("rn" to "m", "vv" to "w", "0" to "o", "1" to "l", "3" to "e", "5" to "s", "4" to "a", "@" to "a")

    fun of(link: Link): Set<LinkSign> = buildSet {
        if (link.hostCandidates.any { it in SHORTENERS }) add(LinkSign.Shortener)
        if (link.host.split('.').any(::mixesScripts)) add(LinkSign.MixedScript)
        if (imitatesBrand(link.host)) add(LinkSign.BrandLookalike)
    }

    private fun mixesScripts(asciiLabel: String): Boolean {
        val label = IDN.toUnicode(asciiLabel, IDN.ALLOW_UNASSIGNED)
        val scripts = label.codePoints().toArray()
            .map { UnicodeScript.of(it) }
            .filter { it != UnicodeScript.COMMON && it != UnicodeScript.INHERITED }
            // Japanese writes Kanji, Hiragana and Katakana together; browsers count them as one script (findings #105).
            .map { if (it == UnicodeScript.HIRAGANA || it == UnicodeScript.KATAKANA) UnicodeScript.HAN else it }
            .toSet()
        return scripts.size > 1
    }

    private fun imitatesBrand(host: String): Boolean {
        val unicode = IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED).lowercase()
        val tokens = unicode.split('.', '-').map(::deleet)
        return BRANDS.any { (brand, domains) ->
            val owned = domains.any { host == it || host.endsWith(".$it") }
            !owned && tokens.any { it == brand }
        }
    }

    private fun deleet(token: String): String =
        DELEET.fold(token.map { HOMOGLYPHS[it] ?: it }.joinToString("")) { acc, (from, to) -> acc.replace(from, to) }
}
