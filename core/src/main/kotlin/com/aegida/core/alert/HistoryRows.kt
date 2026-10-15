package com.aegida.core.alert

import com.aegida.core.model.Severity
import com.aegida.core.text.Problem
import java.time.Instant

/** One line of History: what happened, what was done about it, and whether it is handled (green) or not (amber). */
data class HistoryRow(val at: Instant, val title: String, val detail: String, val handled: Boolean, val facts: String? = null)

object HistoryRows {
    private val OUTCOME_ORDER = listOf(
        Outcome.OwnerWarned, Outcome.GuardianTold, Outcome.GuardianNotReached, Outcome.GuardianNeverReached, Outcome.GuardianReminded,
        Outcome.Acknowledged, Outcome.Fixed, Outcome.NoLongerAConcern, Outcome.SeeNewerWarning,
    )

    /** Lines that close a warning or a question: it was fixed, she saw it or answered it, or a newer warning took it over. */
    private val CLOSING = setOf(Outcome.Fixed, Outcome.Acknowledged, Outcome.NoLongerAConcern, Outcome.SeeNewerWarning)

    /**
     * Kinds there is only one of at a time, so a closing line with nothing more to say (no detail) can only be about that one
     * (findings #129). Every other kind can be about several apps or sites at once, so a line without a detail never closes it:
     * the urgent kinds never name the app (R17), and their rows stay amber rather than risk another app's row turning green.
     */
    private val ONE_AT_A_TIME = setOf(
        Problem.WebsiteFilterOff, Problem.ListenerOff, Problem.ListenerNotConnected, Problem.NotificationsBlocked,
        Problem.GuardianUnreachable, Problem.ListsOld,
    )

    /**
     * Newest first. Entries the pipeline wrote for the same problem at the same moment (the warning to the owner
     * and the message to a guardian) become one line, so one event reads as one event. Two events of one kind at one moment
     * about different things (a reminder about two new apps) stay two lines, each with its own detail (finding #130).
     * A line still waiting for her turns green once a later line closes it: the same kind, about the same app or site (the same
     * detail), written after it (findings #129, Kevin, Oct 14). Nothing written after it, and it stays amber.
     */
    fun of(entries: List<HistoryEntry>): List<HistoryRow> {
        val closings = entries.filter { it.outcome in CLOSING }
        return entries.groupBy { Triple(it.at, it.problem, it.detail) }
            .map { (key, group) ->
                val (at, problem, detail) = key
                val outcomes = group.map { it.outcome }.distinct().sortedBy { OUTCOME_ORDER.indexOf(it) }
                HistoryRow(
                    at = at,
                    title = problem.whatHappened,
                    detail = outcomes.joinToString(" ") { it.forMom },
                    handled = outcomes.any { it in CLOSING } || closedLater(at, problem, detail, closings) ||
                        // A hidden fake warning needs nothing, but a hidden "Was that you?" still does (findings #94); an Info line never needs her.
                        (problem.hidesNotification && problem.fix != com.aegida.core.text.Fix.ConfirmEvent) ||
                        problem.severity == Severity.Info,
                    facts = detail,
                )
            }
            .sortedByDescending { it.at }
    }

    /** True when a closing line of the same kind, about the same app or site, was written after [at]. */
    private fun closedLater(at: Instant, problem: Problem, detail: String?, closings: List<HistoryEntry>): Boolean =
        (detail != null || problem in ONE_AT_A_TIME) &&
            closings.any { it.problem == problem && it.detail == detail && it.at.isAfter(at) }
}
