package com.aegida.core

import com.aegida.core.text.DayLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DayLabelTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val now = ZonedDateTime.of(2026, 10, 8, 0, 30, 0, 0, zone).toInstant() // just after local midnight

    @Test fun justBeforeMidnightIsYesterdayNotToday() {
        assertEquals(DayLabel.Yesterday, DayLabel.of(now.minus(Duration.ofMinutes(45)), now, zone))
    }

    @Test fun earlierTheSameLocalDayIsToday() {
        assertEquals(DayLabel.Today, DayLabel.of(now.minus(Duration.ofMinutes(20)), now, zone))
    }

    @Test fun olderDaysAreDated() {
        assertEquals(DayLabel.On(LocalDate.of(2026, 10, 5)), DayLabel.of(now.minus(Duration.ofDays(3)), now, zone))
    }

    @Test fun theZoneDecidesTheDayNotUtc() {
        val utcNextDay: Instant = ZonedDateTime.of(2026, 10, 8, 3, 0, 0, 0, ZoneId.of("UTC")).toInstant() // 8 pm on Oct 7 in LA
        assertEquals(DayLabel.Yesterday, DayLabel.of(utcNextDay, now, zone))
    }
}
