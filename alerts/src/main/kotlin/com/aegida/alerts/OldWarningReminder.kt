package com.aegida.alerts

import com.aegida.core.alert.Covered
import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.HistoryDetail
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.Outcome
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingState
import com.aegida.core.model.Severity
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
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
    suspend fun remind(now: Instant, allowedSites: Set<String> = emptySet()): Int {
        val settings = line.current()
        val topic = settings.firstTopic?.takeIf { settings.alertsOn } ?: return 0
        val due = dueWarnings(guardianName(settings), now, allowedSites)
        return if (due.isEmpty() || remindedRecently(now)) 0 else raise(topic, due, guardianName(settings), now)
    }

    /** One message for everything due; the count is 1 if it could not be delivered (it is queued). */
    private suspend fun raise(topic: NtfyTopic, due: List<Finding>, owner: String, now: Instant): Int {
        // Firm only when something waiting is urgent; otherwise the guardian is left to judge whether to call (Kevin, Oct 9).
        val text = if (due.any { it.severity == Severity.Urgent }) Texts.Guardian.STILL_OPEN_URGENT else Texts.Guardian.STILL_OPEN
        val message = GuardianMessage(title = owner, body = text.format(owner), urgent = false)
        var notReached = 0
        when (line.send(topic, message)) {
            // Each line says which app or site it was about, so her "Yes, that was me" finds it for that one only (findings #117).
            SendResult.Delivered -> due.forEach { history.add(HistoryEntry(now, it.key.problem, Outcome.GuardianReminded, detailOf(it))) }
            is SendResult.NotDelivered -> {
                notReached++
                // One message, held once; every warning it covers is listed so each gets its own History line on delivery (findings #131).
                line.hold(topic, message, due.map { Covered(it.key.problem, detailOf(it)) }, now, reminder = true)
                due.forEach { history.add(HistoryEntry(now, it.key.problem, Outcome.GuardianNotReached, detailOf(it))) }
            }
        }
        due.forEach { findings.put(it.copy(state = FindingState.Escalated)) }
        return notReached
    }

    private fun detailOf(finding: Finding): String? = HistoryDetail.of(finding)

    /**
     * A fake warning about a website she now allows (one raised before findings #148, or before her Yes) is not raised again: since
     * #148 such a site's alarming notice tells only her (findings #149, WP17 A11). It stays on Home until she taps it.
     */
    private suspend fun dueWarnings(owner: String, now: Instant, allowedSites: Set<String>): List<Finding> {
        val candidates = findings.open().filter { it.state == FindingState.Open && it.severity >= Severity.NeedsTap }
            .filter { it.key.problem.forGuardian(owner) != null || it.key.problem.remindsGuardianOnly }
            .filterNot { it.key.problem == Problem.SiteNotifications && it.key.subject in allowedSites }
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
