package com.aegida.core.text

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which day a moment falls on, from Mom's point of view: today, yesterday, or a date. Pure, so it is tested. */
sealed interface DayLabel {
    data object Today : DayLabel
    data object Yesterday : DayLabel
    data class On(val date: LocalDate) : DayLabel

    companion object {
        fun of(at: Instant, now: Instant, zone: ZoneId): DayLabel {
            val day = at.atZone(zone).toLocalDate()
            val today = now.atZone(zone).toLocalDate()
            return when (day) {
                today -> Today
                today.minusDays(1) -> Yesterday
                else -> On(day)
            }
        }
    }
}
