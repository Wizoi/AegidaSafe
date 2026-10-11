package com.aegida.core.notice

import com.aegida.core.model.Aegida
import com.aegida.core.model.InstalledApp
import com.aegida.core.model.NotificationEvent
import java.time.Duration
import java.time.Instant

/** One notice an app posted that Aegida counted: when, and how many of the ad signals it had. Never its words. */
data class NoticeRow(val at: Instant, val signals: Int)

/**
 * Tells an app that sends ads as notifications from a real one (design: docs/notification-ad-spam-design.md, finding #92).
 * Pure rules; the numbers are the first guesses and stay here, in one place, so they can be changed from real data.
 */
object NoticeRules {
    /** What the sending app says the notice is, when it is something real. Never counted. */
    private val REAL_KINDS = setOf("msg", "call", "email", "alarm", "navigation", "progress", "service", "status", "transport", "reminder", "event", "social")

    private val AD_CHANNEL_WORDS = Regex("""\b(promo\w*|offers?|deals?|ads?|advert\w*|sale|marketing|news|recommend\w*|discover\w*)\b""", RegexOption.IGNORE_CASE)

    /** Words that push an action or scare, the way the fake-ad notices do. */
    private val PUSH_WORDS = Regex(
        """(\bready!|\bconvert\b|\bclean( now)?\b|\bboost\b|\bscan( now)?\b|\bfree\b|\binstall\b|\bwin\b|\blimited\b|\bvirus\b|\binfected\b|\bspeed up\b|\boptimi[sz]e\b|\bstorage (is )?full\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** ApplicationInfo.category values where a steady flow of notices is unusual: game, image, productivity. */
    private val TOOL_CATEGORIES = setOf(0, 3, 7)

    const val HEADS_UP = 4

    val DAY: Duration = Duration.ofHours(24)
    val WEEK: Duration = Duration.ofDays(7)
    const val DAY_COUNT = 3
    const val DAY_WITH_SIGNS = 2
    const val WEEK_COUNT = 6

    /** Whether this notice is one Aegida counts at all. [roleApps] are the default messages, phone and browser apps. */
    fun counted(event: NotificationEvent, apps: List<InstalledApp>, roleApps: Set<String>): Boolean {
        if (event.packageName == Aegida.PACKAGE || event.packageName in roleApps) return false
        if (event.channelId?.startsWith("web:") == true) return false
        if (event.category in REAL_KINDS || event.ongoing || event.conversation) return false
        if (apps.firstOrNull { it.packageName == event.packageName }?.isSystem == true) return false
        return true
    }

    /** How many of the six ad signals this notice shows (0 to 6). [appCategory] is the app's own category, or null. */
    fun signals(event: NotificationEvent, appCategory: Int?): Int {
        var n = 0
        if (event.category == "promo" || event.category == "recommendation") n++
        if (AD_CHANNEL_WORDS.containsMatchIn(event.channelName.orEmpty() + " " + event.channelDescription.orEmpty())) n++
        if (event.importance >= HEADS_UP) n++
        if (PUSH_WORDS.containsMatchIn(event.title + " " + event.text)) n++
        if (appCategory in TOOL_CATEGORIES) n++
        if (event.sentimentNegative) n++
        return n
    }

    /**
     * Flag the app when, in the last day, it posted 3 or more counted notices and 2 or more of them had at least 2 signals;
     * or when, in the last week, it posted 6 or more and most had at least 1. A single notice never flags anything.
     */
    fun flagged(rows: List<NoticeRow>, now: Instant): Boolean {
        val day = rows.filter { !it.at.isBefore(now.minus(DAY)) }
        if (day.size >= DAY_COUNT && day.count { it.signals >= 2 } >= DAY_WITH_SIGNS) return true
        val week = rows.filter { !it.at.isBefore(now.minus(WEEK)) }
        return week.size >= WEEK_COUNT && week.count { it.signals >= 1 } * 2 > week.size
    }
}
