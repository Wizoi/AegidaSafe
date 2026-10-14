package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.Outcome
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingState
import com.aegida.core.model.Severity
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Texts
import java.time.Duration
import java.time.Instant

/**
 * Warnings still waiting for her after a day are raised once more with the guardian, then kept on Home. One
 * message per run covers every warning that is due, and a guardian reminded in the last day is not reminded
 * again (findings #R8). The message goes (or is queued) first and Escalated is stored last, so a run killed
 * in between sends it again: a duplicate is better than a lost reminder (findings #42, R20). A clock that went
 * backwards never holds a reminder back: a first-seen time in the future is taken as now.
 */
internal class OldWarningReminder(
    private val findings: FindingStore,
    private val history: HistoryLog,
    private val line: GuardianLine,
) {
    /** How many guardian messages could not be delivered (0 or 1). */
    suspend fun remind(now: Instant): Int {
        val settings = line.current()
        val topic = settings.firstTopic?.takeIf { settings.alertsOn } ?: return 0
        val due = dueWarnings(guardianName(settings), now)
        return if (due.isEmpty() || remindedRecently(now)) 0 else raise(topic, due, guardianName(settings), now)
    }

    /** One message for everything due; the count is 1 if it could not be delivered (it is queued). */
    private suspend fun raise(topic: NtfyTopic, due: List<Finding>, owner: String, now: Instant): Int {
        // Firm only when something waiting is urgent; otherwise the guardian is left to judge whether to call (Kevin, Oct 9).
        val text = if (due.any { it.severity == Severity.Urgent }) Texts.Guardian.STILL_OPEN_URGENT else Texts.Guardian.STILL_OPEN
        val message = GuardianMessage(title = owner, body = text.format(owner), urgent = false)
        var notReached = 0
        when (line.send(topic, message)) {
            SendResult.Delivered -> due.forEach { history.add(HistoryEntry(now, it.key.problem, Outcome.GuardianReminded)) }
            is SendResult.NotDelivered -> {
                notReached++
                line.hold(topic, message, due.first().key.problem, now)
                due.forEach { history.add(HistoryEntry(now, it.key.problem, Outcome.GuardianNotReached)) }
            }
        }
        due.forEach { findings.put(it.copy(state = FindingState.Escalated)) }
        return notReached
    }

    private suspend fun dueWarnings(owner: String, now: Instant): List<Finding> {
        val candidates = findings.open().filter { it.state == FindingState.Open && it.severity >= Severity.NeedsTap }
            .filter { it.key.problem.forGuardian(owner) != null }
        return candidates.filter { finding ->
            val putRight = withFirstSeenPutRight(finding, now)
            if (putRight != finding) findings.put(putRight)
            putRight == finding && Duration.between(minOf(finding.firstSeen, now), now) >= REMIND_AFTER
        }
    }

    /** A reminder stamped in the future (clock set ahead) is ignored, so it cannot hold reminders back for days. */
    private suspend fun remindedRecently(now: Instant): Boolean =
        history.since(now.minus(REMIND_AFTER)).any {
            it.outcome == Outcome.GuardianReminded && it.at.isAfter(now.minus(REMIND_AFTER)) && !it.at.isAfter(now)
        }

    private companion object {
        val REMIND_AFTER: Duration = Duration.ofHours(24)
    }
}
