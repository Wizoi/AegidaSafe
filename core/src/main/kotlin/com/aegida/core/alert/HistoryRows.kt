package com.aegida.core.alert

import java.time.Instant

/** One line of History: what happened, what was done about it, and whether it is handled (green) or not (amber). */
data class HistoryRow(val at: Instant, val title: String, val detail: String, val handled: Boolean, val facts: String? = null)

object HistoryRows {
    private val OUTCOME_ORDER = listOf(
        Outcome.OwnerWarned, Outcome.GuardianTold, Outcome.GuardianNotReached, Outcome.GuardianNeverReached, Outcome.GuardianReminded,
        Outcome.Acknowledged, Outcome.Fixed, Outcome.NoLongerAConcern,
    )

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
                    // A hidden fake warning needs nothing, but a hidden "Was that you?" still does (findings #94); an Info line never needs her.
                    handled = Outcome.Fixed in outcomes || Outcome.Acknowledged in outcomes || Outcome.NoLongerAConcern in outcomes ||
                        (problem.hidesNotification && problem.fix != com.aegida.core.text.Fix.ConfirmEvent) ||
                        problem.severity == com.aegida.core.model.Severity.Info,
                    facts = group.firstNotNullOfOrNull { it.detail },
                )
            }
            .sortedByDescending { it.at }
}
