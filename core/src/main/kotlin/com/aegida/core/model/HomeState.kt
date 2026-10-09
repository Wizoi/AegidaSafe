package com.aegida.core.model

import com.aegida.core.health.CheckRun
import com.aegida.core.text.Problem
import java.time.Instant

/**
 * What the Home screen shows, decided by one pure function so Compose only draws it (design section 1).
 * There is no "safe": the best state is "no known problems found".
 */
sealed interface HomeState {
    /** Nothing needs her and the last daily check ran. */
    data class NoKnownProblems(val checkedAt: Instant) : HomeState

    /** [items] are in the order "Fix my phone" walks through them. */
    data class NeedsTaps(val items: List<Finding>) : HomeState

    /** Nothing is wrong that Aegida knows of, but it cannot say so: never checked, the lists are old, or a daily check was missed. */
    data class CouldntCheck(val lastCheckedAt: Instant?) : HomeState

    companion object {
        /** Problems that mean "I could not check", whatever else is true. */
        private val UNCHECKED = setOf(Problem.ListsOld, Problem.MissedCheck)

        /** [lastCheck] is the last time Aegida looked at the phone by any trigger (every check runs every detector); a missed daily check or old lists still read as "couldn't check". */
        fun of(open: List<Finding>, lastCheck: CheckRun?): HomeState {
            val needsTap = open
                .filter { it.severity >= Severity.NeedsTap }
                .sortedWith(compareByDescending<Finding> { it.severity }.thenBy { it.firstSeen }.thenBy { it.key.toString() })
            if (needsTap.isNotEmpty()) return NeedsTaps(needsTap)
            val unchecked = lastCheck == null || open.any { it.key.problem in UNCHECKED }
            return if (unchecked) CouldntCheck(lastCheck?.startedAt) else NoKnownProblems(lastCheck.startedAt)
        }
    }
}
