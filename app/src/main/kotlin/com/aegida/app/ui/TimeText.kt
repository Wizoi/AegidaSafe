package com.aegida.app.ui

import com.aegida.core.text.DayLabel
import com.aegida.core.text.Texts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Times and dates in the phone's own language and clock style. Which day it is comes from the pure DayLabel. */
object TimeText {
    fun time(at: Instant, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(at.atZone(zone))

    fun date(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

    /** How fresh the scam lists are: the day they were built, or that none have been downloaded. */
    fun listsLine(builtAt: Instant?, zone: ZoneId, locale: Locale): String =
        if (builtAt == null) Texts.Settings.LISTS_NOT_YET else Texts.Settings.LISTS_UPDATED.format(date(builtAt.atZone(zone).toLocalDate(), locale))

    fun checkedLine(at: Instant, now: Instant, zone: ZoneId, locale: Locale): String = when (val day = DayLabel.of(at, now, zone)) {
        DayLabel.Today -> Texts.Home.CHECKED_TODAY.format(time(at, zone, locale))
        DayLabel.Yesterday -> Texts.Home.CHECKED_YESTERDAY.format(time(at, zone, locale))
        is DayLabel.On -> Texts.Home.CHECKED_ON.format(date(day.date, locale))
    }

    /** "Last checked yesterday at 9:02 AM." for the "I couldn't check today" state. */
    fun lastCheckedLine(at: Instant, now: Instant, zone: ZoneId, locale: Locale): String =
        Texts.Home.LAST_CHECKED.format(checkedLine(at, now, zone, locale).removePrefix("Checked ").removeSuffix("."))
}
