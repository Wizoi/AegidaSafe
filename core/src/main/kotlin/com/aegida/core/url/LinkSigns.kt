package com.aegida.core.url

import com.aegida.core.text.LookAlikes
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
        "apple" to setOf("apple.com", "icloud.com", "apple.co", "apple.news"),
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
        "ups" to setOf("ups.com", "ups-scs.com", "theupsstore.com"), // UPS's own supply-chain and store sites (Kevin, Oct 14)
        "irs" to setOf("irs.gov"),
        "medicare" to setOf("medicare.gov"),
        "ssa" to setOf("ssa.gov"),
        "venmo" to setOf("venmo.com"),
        "zelle" to setOf("zellepay.com"),
        "facebook" to setOf("facebook.com"),
        "walmart" to setOf("walmart.com"),
        "costco" to setOf("costco.com"),
    )

    /** English words ending in "-ups" ("pop-ups", "touch-ups"): the "ups" in them is not the parcel company (findings #108, #133). */
    private val UPS_WORDS = setOf(
        "pop", "sign", "check", "start", "back", "set", "follow", "mix", "pick", "line", "mock", "warm", "round", "close", "make",
        "grown", "push", "pull", "sit", "top", "write", "clean", "tune", "roll", "hook", "lock", "pin", "touch", "mash",
    )

    /** Whole English words ending in "ups" that are not "<word> ups": never the parcel company, even next to "delivery" (findings #133). */
    private val ENDS_IN_UPS = setOf(
        "cups", "groups", "soups", "coups", "hiccups", "stirrups", "teacups", "buttercups", "subgroups", "regroups", "recoups",
    )

    /** English words starting with "ups" ("upstate", "upset"): never the parcel company (findings #133). */
    private val STARTS_WITH_UPS = listOf(
        "upset", "upstair", "upstate", "upstream", "upside", "upshot", "upscale", "upsell", "upsilon", "upstage", "upstart", "upsurge",
        "upswing", "upsize", "upskill",
    )

    /** Words of a parcel scam's name ("track-pick-ups", "ups-redelivery", "upsmychoice"). */
    private val PARCEL_WORDS = setOf(
        "track", "tracking", "delivery", "deliveries", "redelivery", "deliver", "redeliver", "parcel", "parcels", "package", "packages",
        "shipping", "shipment", "courier", "mychoice",
    )

    /** Also parcel words when joined to "ups" ("upsexpress"), but not alone: "pop-ups-express" is a pop-up shop (findings #138). */
    private val JOINED_PARCEL_WORDS = PARCEL_WORDS + "express"
    private const val UPS = "ups"
    private val UPS_NUMBER = Regex("""ups\d+""")
    /** How a name with letters outside a-z is written in a link (IDNA): "xn--80ak6aa92e" is the all-Cyrillic "apple". */
    private const val ACE_PREFIX = "xn--"
    private val DELEET = listOf("rn" to "m", "vv" to "w", "0" to "o", "1" to "l", "3" to "e", "5" to "s", "4" to "a", "@" to "a")

    /**
     * The signs of [link]. With [popularSite] (a parent domain of the host on the popular list), only the part in front of it is read
     * for brand words and alphabets, while whose site it is still comes from the whole host: icloud.apple.com is Apple's, and
     * paypal-login.sites.google.com is not PayPal's (findings #155, #159).
     */
    fun of(link: Link, popularSite: String? = null): Set<LinkSign> = buildSet {
        val read = popularSite?.takeIf { link.host.endsWith(".$it") }?.let { link.host.removeSuffix(".$it") } ?: link.host
        if (link.hostCandidates.any { it in SHORTENERS }) add(LinkSign.Shortener)
        if (read.split('.').any(::mixesScripts)) add(LinkSign.MixedScript)
        if (imitatesBrand(link.host, read)) add(LinkSign.BrandLookalike)
    }

    /**
     * Whether a look-alike warning about [link] may be a false alarm she can wave off with "This link is fine" (findings #156, rows
     * ST37-ST39): a brand's name in plain letters on a site Aegida does not know (google-reviews.example). Never a name with a letter
     * outside a-z (the all-Cyrillic "apple" is not mixed, yet reads "apple"), and never a brand spelled only with swapped letters
     * (paypa1, rnicrosoft): no ordinary site spells a brand that way, so those are the ones a scammer would coach her to allow.
     */
    fun namesBrandPlainly(link: Link): Boolean =
        link.host.split('.').none { it.startsWith(ACE_PREFIX) } &&
            brandsImitated(link.host, link.host, undoLookAlikes = false).let { plain ->
                // Every brand the warning found must be written plainly: paypal-rnicrosoft.com hides a swapped brand (findings #161).
                plain.isNotEmpty() && plain.containsAll(brandsImitated(link.host, link.host, undoLookAlikes = true))
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

    /** A brand word in [read] (the host, or the part of it in front of a popular site) on a [host] the brand does not own. */
    private fun imitatesBrand(host: String, read: String): Boolean = brandsImitated(host, read, undoLookAlikes = true).isNotEmpty()

    /** The brands whose word is in [read] on a [host] they do not own. */
    private fun brandsImitated(host: String, read: String, undoLookAlikes: Boolean): Set<String> {
        val unicode = IDN.toUnicode(read, IDN.ALLOW_UNASSIGNED).lowercase()
        val labels = unicode.split('.').map { label -> label.split('-').flatMap { wordsOf(it, undoLookAlikes) } }
        // Only a parcel word in the same name between dots counts: track.check-ups-clinic.com is a clinic (Kevin, Oct 14).
        val tokens = labels.flatMap { words -> brandWords(words, aboutParcels = words.any { it in PARCEL_WORDS }) }
        val ending = host.lowercase().trimEnd('.').substringAfterLast('.')
        return BRANDS.filter { (brand, domains) ->
            val owned = domains.any { host == it || host.endsWith(".$it") } || ownsItsEnding(brand, ending)
            !owned && tokens.any { it == brand }
        }.keys
    }

    /**
     * A brand that runs its own top-level domain owns every name under it: share.google and store.apple are the brand's (findings #155).
     * Only the ending as written counts, so google.evil.com and share.goog1e are still look-alikes.
     */
    private fun ownsItsEnding(brand: String, ending: String): Boolean = ending == brand && TopLevelDomains.isListed(ending)

    /**
     * One hyphen-separated word, look-alike letters undone: "ups24" is the brand with a number, and "upsdelivery" reads as "ups
     * delivery" so its parcel word counts. "pickups" stays one word: in a name about parcels [joinedToUps] makes it the brand.
     */
    private fun wordsOf(token: String, undoLookAlikes: Boolean): List<String> {
        val word = if (undoLookAlikes) deleet(token) else token
        val rest = word.removePrefix(UPS)
        return when {
            UPS_NUMBER.matches(token) -> listOf(UPS)
            rest != word && rest in JOINED_PARCEL_WORDS -> listOf(UPS, rest)
            else -> listOf(word)
        }
    }

    /**
     * The words of one name between dots, with "ups" left in only where it is the parcel company (table ST19 in
     * docs/scenarios/scam-texts.md; findings #108, #120, #127, #133). The "ups" ending an English "-ups" word ("pop-ups") is excused
     * unless the name is about parcels ("track-pick-ups"). In a name about parcels, "ups" joined to either end of a word is the brand
     * too ("myups-redelivery", "upsglobal-track"), unless the word is English ("cups-delivery", "upstate-shipping"). "ups-and-downs"
     * is never the brand.
     */
    private fun brandWords(words: List<String>, aboutParcels: Boolean): List<String> = words.mapIndexedNotNull { i, w ->
        when {
            w == UPS && words.getOrNull(i + 1) == "and" && words.getOrNull(i + 2) == "downs" -> null
            w == UPS && !aboutParcels && i > 0 && words[i - 1] in UPS_WORDS -> null
            aboutParcels && joinedToUps(w) -> UPS
            else -> w
        }
    }

    private fun joinedToUps(word: String): Boolean = word.length > UPS.length && word !in ENDS_IN_UPS &&
        (word.endsWith(UPS) || (word.startsWith(UPS) && STARTS_WITH_UPS.none { word.startsWith(it) }))

    private fun deleet(token: String): String =
        DELEET.fold(LookAlikes.latin(token)) { acc, (from, to) -> acc.replace(from, to) }
}
