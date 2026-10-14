package com.aegida.core.notice

import com.aegida.core.model.Aegida
import com.aegida.core.model.InstalledApp
import com.aegida.core.model.NotificationEvent
import java.time.Duration
import java.time.Instant

/** One notice an app posted that Aegida counted: when, and how many of the ad signals it had. Never its words. */
data class NoticeRow(val at: Instant, val signals: Int)

/**
 * Tells an app that sends ads as notifications from a real one (design: docs/notification-ad-spam-design.md, findings #92, #93).
 * Pure rules; every number is here, in one place, so it can be changed from real data.
 *
 * Signs come in two kinds. **Content** signs are in the notice itself: the app calls it a promotion, its channel has an ad word in
 * its name, or its words push or scare. **Context** signs are the same on every notice an app sends: it pops up, the app is a game
 * or a tool, the phone has learned the owner dismisses it. Context alone is never an ad (a bank's pop-up channel is not), so a
 * notice scores only when it has a content sign, and then the context adds to it.
 */
object NoticeRules {
    /** What the sending app says the notice is, when it is something real. Never counted. */
    private val REAL_KINDS = setOf("msg", "call", "email", "alarm", "navigation", "progress", "service", "status", "transport", "reminder", "event", "social")

    /** Ad words in a channel's name or description ("discount": Uber's promo channel, findings #107). Not "news" or "discover": breaking news and a bank called Discover are real. */
    private val AD_CHANNEL_WORDS = Regex("""\b(promo\w*|offers?|deals?|discounts?|ads?|advert\w*|sales?|marketing|recommend\w*)\b""", RegexOption.IGNORE_CASE)

    /** Words that push an action, the way the fake-ad notices do. Not "free", "win", "install" or "ready!": banks, scores, updates and pharmacies say those. */
    private val PUSH_WORDS = Regex(
        """(\bconvert\b|\bclean now\b|\bboost\b|\bscan now\b|\blimited time\b|\bspeed up\b|\boptimi[sz]e\b|\bstorage (is )?full\b)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Fake-virus words in an app's notice. Not the website check's whole list: "security alert", "your phone is", "hacked" and
     * "clean your" are what banks, watches and news say (findings #96).
     */
    private val SCARE_WORDS = Regex("""\b(virus(es)?|infected|malware|trojan|spyware)\b|battery (is )?damaged""", RegexOption.IGNORE_CASE)

    /** ApplicationInfo.category values where a steady flow of notices is unusual: game, image, productivity. */
    private val TOOL_CATEGORIES = setOf(0, 3, 7)

    const val HEADS_UP = 4

    /** The short window: pop-ups that keep coming arrive several an hour (Kevin, Oct 11). */
    val BURST: Duration = Duration.ofHours(1)
    val WEEK: Duration = Duration.ofDays(7)
    const val BURST_COUNT = 3
    const val BURST_WITH_SIGNS = 2
    const val WEEK_COUNT = 6

    /** The same notice updated within this time (a download bar, an edited text) is one notice. */
    val SAME_NOTICE: Duration = Duration.ofMinutes(10)

    /** A row stamped later than now by more than this came from a clock that was ahead; it is ignored and pruned. */
    val CLOCK_SKEW: Duration = Duration.ofMinutes(5)

    /** Whether this notice is one Aegida counts at all. [roleApps] are the default messages, phone and browser apps. */
    fun counted(event: NotificationEvent, apps: List<InstalledApp>, roleApps: Set<String>): Boolean {
        if (event.packageName == Aegida.PACKAGE || event.packageName in roleApps || event.channelId?.startsWith("web:") == true) return false
        return !isNotAnAppNotice(event) && apps.firstOrNull { it.packageName == event.packageName }?.isSystem != true
    }

    /** A notice the app labels as something real, a running service, a chat or a group summary. */
    private fun isNotAnAppNotice(event: NotificationEvent) =
        event.category in REAL_KINDS || event.ongoing || event.conversation || event.groupSummary

    /** The notice's score: 0 without a content sign; otherwise its content signs plus its context signs (1 to 6). [appCategory] may be null. */
    fun signals(event: NotificationEvent, appCategory: Int?): Int {
        val words = event.title + " " + event.text
        var content = 0
        if (event.category == "promo" || event.category == "recommendation") content++
        if (AD_CHANNEL_WORDS.containsMatchIn(event.channelName.orEmpty() + " " + event.channelDescription.orEmpty())) content++
        if (PUSH_WORDS.containsMatchIn(words) || SCARE_WORDS.containsMatchIn(words)) content++
        if (content == 0) return 0
        var context = 0
        if (event.importance >= HEADS_UP) context++
        if (appCategory in TOOL_CATEGORIES) context++
        if (event.sentimentNegative) context++
        return content + context
    }

    /**
     * Flag the app when, in the last hour, it posted 3 or more counted notices and 2 or more of them scored at least 2; or when, in the
     * last week, it posted 6 or more and most scored at least 1. A single notice never flags anything; rows from the future are ignored.
     */
    fun flagged(rows: List<NoticeRow>, now: Instant): Boolean {
        val real = rows.filter { !it.at.isAfter(now.plus(CLOCK_SKEW)) }
        val burst = real.filter { !it.at.isBefore(now.minus(BURST)) }
        if (burst.size >= BURST_COUNT && burst.count { it.signals >= 2 } >= BURST_WITH_SIGNS) return true
        val week = real.filter { !it.at.isBefore(now.minus(WEEK)) }
        return week.size >= WEEK_COUNT && week.count { it.signals >= 1 } * 2 > week.size
    }

    /** How many of [rows] in the last week looked like ads: the number the owner and History are told. */
    fun adLike(rows: List<NoticeRow>, now: Instant): Int =
        rows.count { it.signals >= 1 && !it.at.isBefore(now.minus(WEEK)) && !it.at.isAfter(now.plus(CLOCK_SKEW)) }
}
