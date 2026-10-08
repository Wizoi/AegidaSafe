package com.aegida.core.alert

import java.time.Instant

/** One line of History: what happened, what was done about it, and whether it is handled (green) or not (amber). */
data class HistoryRow(val at: Instant, val title: String, val detail: String, val handled: Boolean)

object HistoryRows {
    private val OUTCOME_ORDER = listOf(Outcome.OwnerWarned, Outcome.GuardianTold, Outcome.GuardianNotReached, Outcome.Acknowledged, Outcome.Fixed)

    /**
     * Newest first. Entries the pipeline wrote for the same problem at the same moment (the warning to the owner
     * and the message to a guardian) become one line, so one event reads as one event.
     */
    fun of(entries: List<HistoryEntry>): List<HistoryRow> =
        entries.groupBy { it.at to it.problem }
            .map { (key, group) ->
                val (at, problem) = key
                val outcomes = group.map { it.outcome }.distinct().sortedBy { OUTCOME_ORDER.indexOf(it) }
                HistoryRow(
                    at = at,
                    title = problem.whatHappened,
                    detail = outcomes.joinToString(" ") { it.forMom },
                    handled = Outcome.Fixed in outcomes || Outcome.Acknowledged in outcomes || problem.hidesNotification,
                )
            }
            .sortedByDescending { it.at }
}
