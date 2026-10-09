package com.aegida.core

import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryRows
import com.aegida.core.alert.Outcome
import com.aegida.core.text.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class GuardianNeverReachedRowTest {
    @Test fun aGivenUpMessageIsOneAmberLineThatSaysSo() {
        val row = HistoryRows.of(listOf(HistoryEntry(Instant.now(), Problem.ScamLink, Outcome.GuardianNeverReached))).single()
        assertFalse(row.handled)
        assertEquals("Aegida could not reach your guardian, and gave up.", row.detail)
    }
}
