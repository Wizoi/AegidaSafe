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
import com.aegida.core.model.FindingState
import com.aegida.core.model.Severity
import com.aegida.core.net.NtfyTopic
import com.aegida.core.time.Clock
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
            if (stored != null && stored.state == FindingState.Open) {
                findings.put(stored.copy(lastSeen = finding.lastSeen))
                return@forEach
            }
            alerted++
            if (finding.severity >= Severity.NeedsTap) {
                owner.show(finding)
                history.add(HistoryEntry(now, finding.key.problem, Outcome.OwnerWarned))
            }
            when (tellFirstGuardian(finding)) {
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
        return PipelineResult(alerted = alerted, resolved = resolved.size, guardiansNotReached = notReached)
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
        val SETTING_BASED = setOf(DetectorId.Dns, DetectorId.Health, DetectorId.Delivery)
    }
}

/** Every run reports what it did (design section 1: a write reports what it changed). */
data class PipelineResult(val alerted: Int, val resolved: Int, val guardiansNotReached: Int)
