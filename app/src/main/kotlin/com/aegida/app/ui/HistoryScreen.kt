package com.aegida.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aegida.core.alert.HistoryRow
import com.aegida.core.text.DayLabel
import com.aegida.core.text.Texts
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** A plain list of what Aegida did, newest first, with a day heading. Green = handled, amber = still needs her. */
@Composable
fun HistoryScreen(rows: List<HistoryRow>, pictureId: String?, now: Instant, zone: ZoneId, locale: Locale) {
    AegidaScreen(pictureId) {
        Text(Texts.History.TITLE, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        if (rows.isEmpty()) {
            Body(Texts.History.EMPTY, centered = false)
        } else {
            rows.groupBy { DayLabel.of(it.at, now, zone) }.forEach { (day, dayRows) ->
                Text(dayHeading(day, locale), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                dayRows.forEach { HistoryLine(it, zone, locale) }
            }
            Text(Texts.History.KEPT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun dayHeading(day: DayLabel, locale: Locale): String = when (day) {
    DayLabel.Today -> Texts.History.TODAY
    DayLabel.Yesterday -> Texts.History.YESTERDAY
    is DayLabel.On -> TimeText.date(day.date, locale).uppercase(locale)
}

@Composable
private fun HistoryLine(row: HistoryRow, zone: ZoneId, locale: Locale) {
    val state = if (row.handled) Texts.History.HANDLED else Texts.History.NEEDS_YOU
    Surface(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${row.title}. ${row.facts.orEmpty()} ${row.detail} $state." },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            HandledDot(row.handled, Modifier.padding(top = 6.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium)
                row.facts?.let { Text(it, style = MaterialTheme.typography.bodyLarge) } // what it was (Kevin, Oct 10)
                Text(row.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(TimeText.time(row.at, zone, locale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
