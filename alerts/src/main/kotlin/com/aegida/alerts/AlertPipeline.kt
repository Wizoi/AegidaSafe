package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.MomNotifier
import com.aegida.core.alert.Outcome
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingState
import com.aegida.core.model.Severity
import com.aegida.core.time.Clock
import javax.inject.Inject

/**
 * The only path from findings to people (design section 5). A finding alerts once: a repeat only updates
 * lastSeen. Findings from settings-based detectors that stop appearing are resolved. Guardians hear only
 * when Mom turned alerts on, and only the coarse Problem sentence. Escalation is build step 2.
 */
class AlertPipeline @Inject constructor(
    private val findings: FindingStore,
    private val mom: MomNotifier,
    private val history: HistoryLog,
    private val guardians: GuardianSettings,
    private val ntfy: NtfySender,
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
            findings.put(finding.copy(state = FindingState.Open))
            alerted++
            if (finding.severity >= Severity.NeedsTap) {
                mom.show(finding)
                history.add(HistoryEntry(now, finding.key.problem, Outcome.MomWarned))
            }
            when (tellFirstGuardian(finding)) {
                SendResult.Delivered -> history.add(HistoryEntry(now, finding.key.problem, Outcome.GuardianTold))
                is SendResult.NotDelivered -> {
                    notReached++
                    history.add(HistoryEntry(now, finding.key.problem, Outcome.GuardianNotReached))
                }
                null -> Unit
            }
        }

        val stillThere = current.map { it.key }.toSet()
        val resolved = findings.open().filter { it.key.detectorId in ran && it.key.detectorId in SETTING_BASED && it.key !in stillThere }
        resolved.forEach { finding ->
            findings.put(finding.copy(state = FindingState.Resolved, lastSeen = now))
            mom.clear(finding.key)
            history.add(HistoryEntry(now, finding.key.problem, Outcome.Fixed))
        }
        return PipelineResult(alerted = alerted, resolved = resolved.size, guardiansNotReached = notReached)
    }

    /** Null when guardians are not to be told about this finding. */
    private suspend fun tellFirstGuardian(finding: Finding): SendResult? {
        val settings = guardians.current()
        val topic = settings.firstTopic ?: return null
        if (!settings.alertsOn) return null
        val text = finding.key.problem.forGuardian(settings.momName) ?: return null
        return ntfy.send(topic, GuardianMessage(title = settings.momName, body = text, urgent = finding.severity == Severity.Urgent))
    }

    companion object {
        /**
         * Detectors that look at the phone's settings on every run, so a missing finding means "fixed".
         * Event detectors (links, site notifications) only speak when a notification arrives.
         */
        val SETTING_BASED = setOf(DetectorId.Dns, DetectorId.Health)
    }
}

/** Every run reports what it did (design section 1: a write reports what it changed). */
data class PipelineResult(val alerted: Int, val resolved: Int, val guardiansNotReached: Int)
