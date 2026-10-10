package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.HistoryDetail
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.OwnerNotifier
import com.aegida.core.alert.Outbox
import com.aegida.core.alert.QueuedMessage
import com.aegida.core.alert.Outcome
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.Severity
import com.aegida.core.text.Problem
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Texts
import com.aegida.core.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * The only path from findings to people (design section 5). A finding alerts once: a repeat only updates
 * lastSeen. Findings from settings-based detectors that stop appearing are resolved. Guardians hear only
 * when the owner turned alerts on, and only the coarse Problem sentence. Escalation is build step 2.
 */
class AlertPipeline @Inject constructor(
    private val findings: FindingStore,
    private val owner: OwnerNotifier,
    private val history: HistoryLog,
    private val guardians: GuardianSettings,
    private val ntfy: NtfySender,
    private val outbox: Outbox,
    private val clock: Clock,
) {
    suspend fun handle(current: List<Finding>, ran: Set<DetectorId>): PipelineResult {
        val now = clock.now()
        var alerted = 0
        var notReached = 0
        current.forEach { finding ->
            val stored = findings.get(finding.key)
            if (stored != null && stored.state.needsHer) {
                findings.put(stored.copy(lastSeen = finding.lastSeen))
                return@forEach
            }
            alerted++
            val detail = HistoryDetail.of(finding.key.problem, finding.evidence) // what it was, kept in History
            if (finding.severity >= Severity.NeedsTap) {
                owner.show(finding)
                history.add(HistoryEntry(now, finding.key.problem, Outcome.OwnerWarned, detail))
            }
            val result = if (backedOff(finding, now)) null else tellFirstGuardian(finding)
            when (result) {
                SendResult.Delivered -> history.add(HistoryEntry(now, finding.key.problem, Outcome.GuardianTold, detail))
                is SendResult.NotDelivered -> {
                    notReached++
                    holdForRetry(finding, now)
                    history.add(HistoryEntry(now, finding.key.problem, Outcome.GuardianNotReached, detail))
                }
                null -> Unit
            }
            // Last, so a run killed before this point is repeated whole: a duplicate is better than a lost warning.
            findings.put(finding.copy(state = FindingState.Open))
        }

        val stillThere = current.map { it.key }.toSet()
        val resolved = findings.open().filter { it.key.detectorId in ran && it.key.detectorId in SETTING_BASED && it.key !in stillThere }
        resolved.forEach { finding ->
            findings.put(finding.copy(state = FindingState.Resolved, lastSeen = now))
            owner.clear(finding.key)
            history.add(HistoryEntry(now, finding.key.problem, Outcome.Fixed))
        }
        notReached += remindAboutOldWarnings(now)
        return PipelineResult(alerted = alerted, resolved = resolved.size, guardiansNotReached = notReached)
    }

    /**
     * She said a change was hers. A guardian who was told about *this* warning hears that, so an alarm about a
     * family-approved install does not need a phone call to settle. Nothing is sent to a guardian who was never told
     * (alerts were off, the repeat back-off held it back, or the kind has no guardian text), nor while the original alarm is still waiting in the
     * outbox (the all-clear must not arrive first). For urgent kinds the words never say everything is fine: it may
     * be a scammer coaching her (findings #50).
     */
    suspend fun tellConfirmed(key: FindingKey) {
        val problem = key.problem
        val settings = guardians.current()
        val topic = settings.firstTopic?.takeIf { settings.alertsOn } ?: return
        val finding = findings.get(key) ?: return
        val told = history.since(finding.firstSeen).any {
            it.problem == problem && (it.outcome == Outcome.GuardianTold || it.outcome == Outcome.GuardianReminded)
        }
        if (!told || outbox.all().any { it.problem == problem }) return
        val now = clock.now()
        val text = if (problem.severity == Severity.Urgent) Texts.Guardian.CONFIRMED_URGENT else Texts.Guardian.CONFIRMED
        val message = GuardianMessage(title = guardianName(settings), body = text.format(guardianName(settings)), urgent = false)
        when (ntfy.send(topic, message)) {
            SendResult.Delivered -> history.add(HistoryEntry(now, problem, Outcome.GuardianTold))
            is SendResult.NotDelivered -> {
                outbox.add(QueuedMessage(0, topic.value, message.title, message.body, message.urgent, problem, now, 1, now))
                history.add(HistoryEntry(now, problem, Outcome.GuardianNotReached))
            }
        }
    }

    /**
     * Repeats of the same kind of warning must not message the guardian every time. The guardian was told about
     * this problem n times in the last week: wait a day after the last one, then two, then four, then a week.
     * The owner is still warned, and an urgent problem is never held back.
     */
    private suspend fun backedOff(finding: Finding, now: Instant): Boolean {
        if (finding.severity == Severity.Urgent) return false
        // What was sent before the guardian was changed went to someone else; the new guardian has not been told yet (findings #75).
        val pairedAt = guardians.current().pairedAt
        val told = history.since(now.minus(BACKOFF_LOOKBACK))
            .filter { it.problem == finding.key.problem && (it.outcome == Outcome.GuardianTold || it.outcome == Outcome.GuardianNotReached) }
            .filter { pairedAt == null || !it.at.isBefore(pairedAt) }
            // Stamped well ahead of now (the clock was set ahead, then corrected): not trusted, or it would hold the guardian back for days.
            .filter { !it.at.isAfter(now.plus(CLOCK_SKEW)) }
        if (told.isEmpty()) return false
        val wait = minOf(BACKOFF_FIRST.multipliedBy(1L shl (told.size - 1).coerceAtMost(3)), BACKOFF_LOOKBACK)
        // A stamp a little ahead of now (ordinary skew) counts as "told now".
        val last = minOf(told.maxOf { it.at }, now)
        return Duration.between(last, now) < wait
    }

    /**
     * Warnings still waiting for her after a day are raised once more with the guardian, then kept on Home. One
     * message per run covers every warning that is due, and a guardian reminded in the last day is not reminded
     * again (findings #R8). The message goes (or is queued) first and Escalated is stored last, so a run killed
     * in between sends it again: a duplicate is better than a lost reminder (findings #42, R20). A clock that went
     * backwards never holds a reminder back: a first-seen time in the future is taken as now.
     */
    private suspend fun remindAboutOldWarnings(now: Instant): Int {
        val settings = guardians.current()
        val topic = settings.firstTopic?.takeIf { settings.alertsOn } ?: return 0
        val candidates = findings.open().filter { it.state == FindingState.Open && it.severity >= Severity.NeedsTap }
            .filter { it.key.problem.forGuardian(guardianName(settings)) != null }
        val due = candidates.filter { finding ->
            if (finding.firstSeen.isAfter(now.plus(CLOCK_SKEW))) {
                findings.put(finding.copy(firstSeen = now))
                return@filter false
            }
            Duration.between(minOf(finding.firstSeen, now), now) >= REMIND_AFTER
        }
        if (due.isEmpty()) return 0
        // A reminder stamped in the future (clock set ahead) is ignored, so it cannot hold reminders back for days.
        val recently = history.since(now.minus(REMIND_AFTER)).any { it.outcome == Outcome.GuardianReminded && it.at.isAfter(now.minus(REMIND_AFTER)) && !it.at.isAfter(now) }
        if (recently) return 0
        val owner = guardianName(settings)
        // Firm only when something waiting is urgent; otherwise the guardian is left to judge whether to call (Kevin, Oct 9).
        val text = if (due.any { it.severity == Severity.Urgent }) Texts.Guardian.STILL_OPEN_URGENT else Texts.Guardian.STILL_OPEN
        val message = GuardianMessage(title = owner, body = text.format(owner), urgent = false)
        var notReached = 0
        when (ntfy.send(topic, message)) {
            SendResult.Delivered -> due.forEach { history.add(HistoryEntry(now, it.key.problem, Outcome.GuardianReminded)) }
            is SendResult.NotDelivered -> {
                notReached++
                outbox.add(QueuedMessage(0, topic.value, message.title, message.body, message.urgent, due.first().key.problem, now, 1, now))
                due.forEach { history.add(HistoryEntry(now, it.key.problem, Outcome.GuardianNotReached)) }
            }
        }
        due.forEach { findings.put(it.copy(state = FindingState.Escalated)) }
        return notReached
    }

    /** The name the guardian sees; blank falls back to the app name, as [DeadMansSwitch] does. */
    private fun guardianName(settings: com.aegida.core.alert.Guardians) = settings.ownerName.ifBlank { Texts.APP_NAME }

    /** Null when guardians are not to be told about this finding. */
    private suspend fun tellFirstGuardian(finding: Finding): SendResult? {
        val message = messageFor(finding) ?: return null
        return ntfy.send(message.first, message.second)
    }

    private suspend fun messageFor(finding: Finding): Pair<NtfyTopic, GuardianMessage>? {
        val settings = guardians.current()
        val topic = settings.firstTopic ?: return null
        if (!settings.alertsOn) return null
        val text = finding.key.problem.forGuardian(guardianName(settings), finding.evidence[Evidence.APP_LABEL], finding.evidence[Evidence.HOST] ?: finding.evidence[Evidence.SITE]) ?: return null
        return topic to GuardianMessage(title = guardianName(settings), body = text, urgent = finding.severity == Severity.Urgent)
    }

    /** A message that did not go is kept and retried by [OutboxFlusher]; the owner is not asked to do anything. */
    private suspend fun holdForRetry(finding: Finding, now: java.time.Instant) {
        val (topic, message) = messageFor(finding) ?: return
        outbox.add(QueuedMessage(0, topic.value, message.title, message.body, message.urgent, finding.key.problem, now, 1, now))
    }

    companion object {
        private val BACKOFF_FIRST: Duration = Duration.ofHours(24)
        private val BACKOFF_LOOKBACK: Duration = Duration.ofDays(7)
        private val REMIND_AFTER: Duration = Duration.ofHours(24)
        private val CLOCK_SKEW: Duration = Duration.ofMinutes(5)

        /**
         * Detectors that look at the phone's settings on every run, so a missing finding means "fixed".
         * Event detectors (links, site notifications) only speak when a notification arrives.
         */
        val SETTING_BASED = setOf(DetectorId.Dns, DetectorId.Health, DetectorId.Delivery, DetectorId.Drift, DetectorId.NewApp, DetectorId.RemoteControl, DetectorId.Stalkerware)
    }
}

/** Every run reports what it did (design section 1: a write reports what it changed). */
data class PipelineResult(val alerted: Int, val resolved: Int, val guardiansNotReached: Int)
