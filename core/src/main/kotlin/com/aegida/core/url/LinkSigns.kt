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

    /**
     * Brand word -> the domains that really belong to it, short-link and country sites included by name (findings #108). A look-alike
     * uses the word on any other domain, so a country ending not listed here is still warned about.
     */
    private val BRANDS = mapOf(
        "paypal" to setOf("paypal.com", "paypal.me"),
        "amazon" to setOf(
            "amazon.com", "amazon.co.uk", "amazon.ca", "amazon.de", "amazon.fr", "amazon.it", "amazon.es", "amazon.nl", "amazon.com.au",
            "amazon.co.jp", "amazon.in", "amazon.com.mx", "amazon.com.br",
        ),
        "apple" to setOf("apple.com", "icloud.com", "apple.co"),
        "icloud" to setOf("icloud.com", "apple.com"),
        "microsoft" to setOf("microsoft.com", "live.com", "office.com"),
        "google" to setOf(
            "google.com", "google.co.uk", "google.ca", "google.de", "google.fr", "google.it", "google.es", "google.nl", "google.com.au",
            "google.co.jp", "google.co.in", "google.com.mx", "google.com.br",
        ),
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
        'ӏ' to 'l', // the palochka: "аррӏе" is all Cyrillic, so not mixed, yet reads as "apple" (findings #108)
    )

    /** English words ending in "-ups" ("pop-ups"): the "ups" in them is not the parcel company (findings #108). */
    private val UPS_WORDS = setOf(
        "pop", "sign", "check", "start", "back", "set", "follow", "mix", "pick", "line", "mock", "warm", "round", "close", "make",
        "grown", "push", "pull", "sit", "top", "write", "clean", "tune", "roll", "hook", "lock", "pin",
    )
    /** Words of a parcel scam's name ("track-pick-ups", "ups-redelivery"). */
    private val PARCEL_WORDS = setOf(
        "track", "tracking", "delivery", "redelivery", "deliver", "redeliver", "parcel", "parcels", "package", "packages", "shipping",
        "shipment", "courier",
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
        val labels = unicode.split('.').map { label -> label.split('-').map(::deleet).flatMap(::splitUps) }
        val aboutParcels = labels.any { words -> words.any { it in PARCEL_WORDS } }
        val tokens = labels.flatMap { brandWords(it, aboutParcels) }
        return BRANDS.any { (brand, domains) ->
            val owned = domains.any { host == it || host.endsWith(".$it") }
            !owned && tokens.any { it == brand }
        }
    }

    /**
     * The words of one name between dots, without the "ups" of an English "-ups" word such as "pop-ups" or "check-ups", unless the
     * host talks about parcels: "pick-ups-redelivery" and "track-pick-ups" are the parcel company, "pop-ups-blocker" is not
     * (findings #108, #120, #127; table ST19 in docs/scenarios/scam-texts.md).
     */
    private fun brandWords(words: List<String>, aboutParcels: Boolean): List<String> =
        if (aboutParcels) words else words.filterIndexed { i, w -> !(w == "ups" && i > 0 && words[i - 1] in UPS_WORDS) }

    /** "pickups" and "setups" read as "pick ups" and "set ups"; "upsdelivery" as "ups delivery". "upstairs" and "cups" stay one word. */
    private fun splitUps(word: String): List<String> {
        val root = word.removeSuffix("ups")
        val rest = word.removePrefix("ups")
        return when {
            word == "ups" -> listOf(word)
            root != word && root in UPS_WORDS -> listOf(root, "ups")
            rest != word && rest in PARCEL_WORDS -> listOf("ups", rest)
            else -> listOf(word)
        }
    }

    private fun deleet(token: String): String =
        DELEET.fold(token.map { HOMOGLYPHS[it] ?: it }.joinToString("")) { acc, (from, to) -> acc.replace(from, to) }
}
