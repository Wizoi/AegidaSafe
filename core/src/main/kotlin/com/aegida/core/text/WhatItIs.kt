package com.aegida.core.text

import com.aegida.core.model.AppSource
import com.aegida.core.model.Evidence
import com.aegida.core.model.Severity
import com.aegida.core.url.LinkSign
import com.aegida.core.url.LinkSigns
import com.aegida.core.url.LinkText
import com.aegida.core.url.Links
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The one wording rule for what set a warning off (finding #164): the app (with facts it cannot fake), the website, or the app a
 * message came in. Home, the "Was that you?" page, the advice pages and History all build from it, through [Texts.Home.whatItIs].
 * The guardian's note never uses it (it is [Problem.forGuardian], unchanged).
 */
internal object WhatItIs {
    private val APP_KINDS = setOf(
        Problem.BrowserChanged, Problem.SmsAppChanged, Problem.DialerChanged, Problem.AssistantChanged, Problem.ListenerAdded,
        Problem.AccessibilityAdded, Problem.AdminAdded, Problem.NewAppOutsidePlay, Problem.RemoteControlApp, Problem.StalkerwareApp,
    )
    private val SITE_KINDS = setOf(Problem.SiteNotifications, Problem.SiteScaryNews, Problem.NewWebsiteNotifications)
    private val LINK_KINDS = setOf(Problem.ScamLink, Problem.LookalikeLink)
    private const val SITE_MAX = 40
    private const val ACE = "xn--"
    private const val ISOLATE_START = '⁨'
    private const val ISOLATE_END = '⁩'
    private val WORD_GAPS = Regex("""[\s\p{Z}]+""")

    fun lines(problem: Problem, evidence: Map<String, String>, now: Instant?, zone: ZoneId, locale: Locale): List<String> = when (problem) {
        in SITE_KINDS -> listOfNotNull(siteLine(evidence[Evidence.SITE]))
        in LINK_KINDS -> listOfNotNull(cameFromApp(evidence[Evidence.APP_LABEL]) ?: Texts.Home.senderLine(evidence[Evidence.MESSAGE_TITLE]))
        Problem.NoisyApp -> listOfNotNull(cameFromApp(evidence[Evidence.APP_LABEL])) + facts(evidence, now, zone, locale)
        in APP_KINDS -> listOf(nameLine(problem, evidence[Evidence.APP_LABEL])) + facts(evidence, now, zone, locale)
        else -> emptyList()
    }

    /** "The app is called: “X”", for an urgent kind "The app calls itself: “X”", or that Aegida can't read its name (never the package). */
    fun nameLine(problem: Problem, label: String?): String {
        val shown = appWords(label) ?: return Texts.Home.APP_NAME_UNREADABLE
        val line = if (problem.severity == Severity.Urgent) Texts.Home.APP_CALLS_ITSELF else Texts.Home.CONFIRM_APP
        return line.format("“$shown”")
    }

    /** "It came from the “Messages” app.", or null when the app's name is not known. */
    fun cameFromApp(label: String?): String? = appWords(label)?.let { Texts.Home.ADVICE_FROM.format("“$it”") }

    /**
     * An app's own label as Mom may read it: cleaned and cut short; a word shows as "(link)" only when it really is a web address (a
     * scheme, "www.", a path or "@", or a name the link checks flag as mixed or look-alike letters), so "Booking.com" stays a name
     * while "chase-secure.com" does not; right-to-left words are drawn isolated so they cannot turn the sentence around.
     */
    fun appWords(label: String?): String? {
        // Line breaks, direction marks and invisible characters become spaces first (WN18); then dot look-alikes become dots.
        val prepared = LinkText.prepare(Texts.Home.cleanLabel(label.orEmpty(), Int.MAX_VALUE))
        val words = prepared.split(WORD_GAPS).joinToString(" ") { if (isWebAddress(it)) Texts.Home.LINK_REMOVED else it }
        val clean = Texts.Home.cleanLabel(words)
        if (clean.isEmpty()) return null
        return if (clean.any { Character.getDirectionality(it) in RIGHT_TO_LEFT }) "$ISOLATE_START$clean$ISOLATE_END" else clean
    }

    private fun isWebAddress(word: String): Boolean = when {
        !AddressWords.looksLikeAddress(word) || AddressWords.isOrdinaryWord(word) -> false
        "://" in word || word.lowercase().startsWith("www.") || word.any { it in PATH_SIGNS } -> true
        else -> foundAndFlagged(word)
    }

    private fun foundAndFlagged(word: String): Boolean {
        return Links.findThorough(word).any { link -> isUnusualHost(link.host) || LinkSigns.of(link).isNotEmpty() }
    }

    /** "The website: news.example" (the host as written, a port kept), or that the name is unusual (another alphabet, look-alike letters). */
    fun siteLine(site: String?): String? {
        val hostPort = site.orEmpty().trim().lowercase().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@')
        val host = hostPort.substringBefore(':')
        return when {
            host.isEmpty() -> null
            looksUnusual(host) -> Texts.Home.WEBSITE_UNUSUAL
            else -> Texts.Home.WEBSITE_IS.format(Texts.Home.cleanLabel(hostPort, SITE_MAX))
        }
    }

    private fun looksUnusual(host: String): Boolean {
        val link = Links.parse(host)
        val flagged = link != null && LinkSigns.of(link).any { it == LinkSign.MixedScript || it == LinkSign.BrandLookalike }
        return isUnusualHost(host) || flagged
    }

    private fun isUnusualHost(host: String): Boolean =
        host.any { it.code > LAST_ASCII } || host.split('.').any { it.startsWith(ACE) }

    /** Where the app came from and when it was added: "It came from the Play Store." "Added today." (no day for one that came with the phone). */
    fun facts(evidence: Map<String, String>, now: Instant?, zone: ZoneId, locale: Locale): List<String> {
        val source = AppSource.entries.firstOrNull { it.evidence == evidence[Evidence.APP_SOURCE] }
        val from = when (source) {
            AppSource.PlayStore -> Texts.Home.FROM_PLAY
            AppSource.MakerStore -> Texts.Home.FROM_MAKER_STORE
            AppSource.Outside -> Texts.Home.FROM_OUTSIDE
            AppSource.CameWithPhone -> Texts.Home.CAME_WITH_PHONE
            null -> null
        }
        // No day without [now] (History: written once and compared later, so it must not depend on the clock, the zone or the language).
        val millis = evidence[Evidence.APP_ADDED]?.toLongOrNull()
        val added = if (now == null || millis == null || source == AppSource.CameWithPhone) null else {
            when (val day = DayLabel.of(Instant.ofEpochMilli(millis), now, zone)) {
                DayLabel.Today -> Texts.Home.ADDED_TODAY
                DayLabel.Yesterday -> Texts.Home.ADDED_YESTERDAY
                is DayLabel.On -> Texts.Home.ADDED_ON.format(DateTimeFormatter.ofPattern(DAY_PATTERN, locale).format(day.date))
            }
        }
        return listOfNotNull(from, added)
    }

    private const val LAST_ASCII = 127
    private const val PATH_SIGNS = "/@"
    private val RIGHT_TO_LEFT = setOf(Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC)
    private const val DAY_PATTERN = "MMMM d"
}
