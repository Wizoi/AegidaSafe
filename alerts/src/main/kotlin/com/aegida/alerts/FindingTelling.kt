package com.aegida.alerts

import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.Outcome
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.Severity
import com.aegida.core.net.NtfyTopic
import java.time.Duration
import java.time.Instant

/**
 * Telling the first guardian about one finding: what is said (the coarse Problem sentence) and how often. Used only by [AlertPipeline].
 */
internal class FindingTelling(
    private val history: HistoryLog,
    private val line: GuardianLine,
) {
    /** Null when the guardian is not to be told about this finding (held back by the repeat back-off, alerts off, or no guardian text). */
    suspend fun tell(finding: Finding, now: Instant): SendResult? =
        if (backedOff(finding, now)) null else messageFor(finding)?.let { line.send(it.first, it.second) }

    /** A message that did not go is kept and retried by [OutboxFlusher]; the owner is not asked to do anything. */
    suspend fun hold(finding: Finding, now: Instant) {
        val (topic, message) = messageFor(finding) ?: return
        line.hold(topic, message, finding.key.problem, now)
    }

    /**
     * Repeats of the same kind of warning must not message the guardian every time. The guardian was told about
     * this problem n times in the last week: wait a day after the last one, then two, then four, then a week.
     * The owner is still warned, and an urgent problem is never held back.
     */
    private suspend fun backedOff(finding: Finding, now: Instant): Boolean {
        if (finding.severity == Severity.Urgent) return false
        // What was sent before the guardian was changed went to someone else; the new guardian has not been told yet (findings #75).
        val pairedAt = line.current().pairedAt
        val told = history.since(now.minus(BACKOFF_LOOKBACK))
            .filter { it.problem == finding.key.problem && (it.outcome == Outcome.GuardianTold || it.outcome == Outcome.GuardianNotReached) }
            .filter { pairedAt == null || !it.at.isBefore(pairedAt) }
            // Stamped well ahead of now (the clock was set ahead, then corrected): not trusted, or it would hold the guardian back for days.
            .filter { !it.at.isAfter(now.plus(AlertPipeline.CLOCK_SKEW)) }
        return told.isNotEmpty() && stillWaitingOut(told, now)
    }

    private fun stillWaitingOut(told: List<HistoryEntry>, now: Instant): Boolean {
        val wait = minOf(BACKOFF_FIRST.multipliedBy(1L shl (told.size - 1).coerceAtMost(MAX_DOUBLINGS)), BACKOFF_LOOKBACK)
        // A stamp a little ahead of now (ordinary skew) counts as "told now".
        val last = minOf(told.maxOf { it.at }, now)
        return Duration.between(last, now) < wait
    }

    private suspend fun messageFor(finding: Finding): Pair<NtfyTopic, GuardianMessage>? {
        val settings = line.current()
        val topic = settings.firstTopic
        if (topic == null || !settings.alertsOn) return null
        val host = finding.evidence[Evidence.HOST] ?: finding.evidence[Evidence.SITE]
        return finding.key.problem.forGuardian(guardianName(settings), finding.evidence[Evidence.APP_LABEL], host)?.let { text ->
            topic to GuardianMessage(title = guardianName(settings), body = text, urgent = finding.severity == Severity.Urgent)
        }
    }

    private companion object {
        val BACKOFF_FIRST: Duration = Duration.ofHours(24)
        val BACKOFF_LOOKBACK: Duration = Duration.ofDays(7)

        /** The wait doubles at most this many times: a day, two, four, eight (then capped at the lookback week). */
        const val MAX_DOUBLINGS = 3
    }
}
