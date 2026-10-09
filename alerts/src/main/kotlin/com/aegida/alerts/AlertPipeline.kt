package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.OwnerNotifier
import com.aegida.core.alert.Outbox
import com.aegida.core.alert.QueuedMessage
import com.aegida.core.alert.Outcome
import com.aegida.core.model.DetectorId
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
            if (finding.severity >= Severity.NeedsTap) {
                owner.show(finding)
                history.add(HistoryEntry(now, finding.key.problem, Outcome.OwnerWarned))
            }
            val result = if (backedOff(finding, now)) null else tellFirstGuardian(finding)
            when (result) {
                SendResult.Delivered -> history.add(HistoryEntry(now, finding.key.problem, Outcome.GuardianTold))
                is SendResult.NotDelivered -> {
                    notReached++
                    holdForRetry(finding, now)
                    history.add(HistoryEntry(now, finding.key.problem, Outcome.GuardianNotReached))
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
        val message = GuardianMessage(title = settings.ownerName, body = text.format(settings.ownerName), urgent = false)
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
        val told = history.since(now.minus(BACKOFF_LOOKBACK))
            .filter { it.problem == finding.key.problem && (it.outcome == Outcome.GuardianTold || it.outcome == Outcome.GuardianNotReached) }
        if (told.isEmpty()) return false
        val wait = minOf(BACKOFF_FIRST.multipliedBy(1L shl (told.size - 1).coerceAtMost(3)), BACKOFF_LOOKBACK)
        return Duration.between(told.maxOf { it.at }, now) < wait
    }

    /** A warning still waiting for her after a day is raised once more with the guardian, then kept on Home. */
    private suspend fun remindAboutOldWarnings(now: Instant): Int {
        val settings = guardians.current()
        val topic = settings.firstTopic?.takeIf { settings.alertsOn } ?: return 0
        var notReached = 0
        findings.open().filter { it.state == FindingState.Open && it.severity >= Severity.NeedsTap }.forEach { finding ->
            if (finding.key.problem.forGuardian(settings.ownerName) == null) return@forEach
            if (Duration.between(finding.firstSeen, now) < REMIND_AFTER) return@forEach
            val owner = settings.ownerName
            val message = GuardianMessage(title = owner, body = Texts.Guardian.STILL_OPEN.format(owner), urgent = false)
            findings.put(finding.copy(state = FindingState.Escalated))
            when (ntfy.send(topic, message)) {
                SendResult.Delivered -> history.add(HistoryEntry(now, finding.key.problem, Outcome.GuardianReminded))
                is SendResult.NotDelivered -> {
                    notReached++
                    outbox.add(QueuedMessage(0, topic.value, message.title, message.body, message.urgent, finding.key.problem, now, 1, now))
                    history.add(HistoryEntry(now, finding.key.problem, Outcome.GuardianNotReached))
                }
            }
        }
        return notReached
    }

    /** Null when guardians are not to be told about this finding. */
    private suspend fun tellFirstGuardian(finding: Finding): SendResult? {
        val message = messageFor(finding) ?: return null
        return ntfy.send(message.first, message.second)
    }

    private suspend fun messageFor(finding: Finding): Pair<NtfyTopic, GuardianMessage>? {
        val settings = guardians.current()
        val topic = settings.firstTopic ?: return null
        if (!settings.alertsOn) return null
        val text = finding.key.problem.forGuardian(settings.ownerName) ?: return null
        return topic to GuardianMessage(title = settings.ownerName, body = text, urgent = finding.severity == Severity.Urgent)
    }

    /** A message that did not go is kept and retried by [OutboxFlusher]; the owner is not asked to do anything. */
    private suspend fun holdForRetry(finding: Finding, now: java.time.Instant) {
        val (topic, message) = messageFor(finding) ?: return
        outbox.add(QueuedMessage(0, topic.value, message.title, message.body, message.urgent, finding.key.problem, now, 1, now))
    }

    companion object {
        /**
         * Detectors that look at the phone's settings on every run, so a missing finding means "fixed".
         * Event detectors (links, site notifications) only speak when a notification arrives.
         */
        private val BACKOFF_FIRST: Duration = Duration.ofHours(24)
        private val BACKOFF_LOOKBACK: Duration = Duration.ofDays(7)
        private val REMIND_AFTER: Duration = Duration.ofHours(24)

        val SETTING_BASED = setOf(DetectorId.Dns, DetectorId.Health, DetectorId.Delivery, DetectorId.Drift, DetectorId.NewApp, DetectorId.RemoteControl, DetectorId.Stalkerware)
    }
}

/** Every run reports what it did (design section 1: a write reports what it changed). */
data class PipelineResult(val alerted: Int, val resolved: Int, val guardiansNotReached: Int)
