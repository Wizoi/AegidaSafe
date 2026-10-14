package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.Guardians
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
import com.aegida.core.model.Snapshot
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
    private val line: GuardianLine,
    private val clock: Clock,
) {
    private val reminder = OldWarningReminder(findings, history, line)
    private val telling = FindingTelling(history, line)

    /** [phone] is the phone as read in this check, for what History says when a new-app question closes by itself. */
    suspend fun handle(current: List<Finding>, ran: Set<DetectorId>, phone: Snapshot? = null): PipelineResult {
        val now = clock.now()
        val tally = Tally()
        current.forEach { process(it, now, tally) }
        val resolved = resolveGone(current, ran, phone, now)
        tally.notReached += reminder.remind(now)
        return PipelineResult(
            alerted = tally.alerted,
            resolved = resolved,
            guardiansNotReached = tally.notReached,
            quieted = tally.quieted,
            openLong = tally.openLong,
        )
    }

    /** What one run counted, for [PipelineResult]. */
    private class Tally {
        var alerted = 0
        var notReached = 0
        val quieted = mutableSetOf<FindingKey>()
        val openLong = mutableSetOf<FindingKey>()
    }

    private suspend fun process(finding: Finding, now: Instant, tally: Tally) {
        val stored = findings.get(finding.key)
        if (stored != null && stored.state.needsHer) {
            val held = withFirstSeenPutRight(stored, now)
            findings.put(held.copy(lastSeen = finding.lastSeen))
            if (Duration.between(held.firstSeen, now) > LONG_OPEN) tally.openLong += finding.key
        } else if (isQuiet(stored, finding, now)) {
            tally.quieted += finding.key
        } else {
            announce(finding, now, tally)
        }
    }

    /** Once the owner has seen it, a warning that keeps happening is quiet for a while (an app flooding her with ads would warn on every notice). */
    private fun isQuiet(stored: Finding?, finding: Finding, now: Instant): Boolean {
        val quiet = finding.key.problem.quietAfterSeen
        if (stored == null || quiet == null) return false
        // A seen time from a clock that was ahead does not count (R14).
        return now.isBefore(stored.lastSeen.plus(quiet)) && !stored.lastSeen.isAfter(now.plus(CLOCK_SKEW))
    }

    private suspend fun announce(finding: Finding, now: Instant, tally: Tally) {
        tally.alerted++
        val detail = HistoryDetail.of(finding.key.problem, finding.evidence) // what it was, kept in History
        if (finding.severity >= Severity.NeedsTap) {
            owner.show(finding)
            history.add(HistoryEntry(now, finding.key.problem, Outcome.OwnerWarned, detail))
        }
        val result = telling.tell(finding, now)
        when (result) {
            SendResult.Delivered -> history.add(HistoryEntry(now, finding.key.problem, Outcome.GuardianTold, detail))
            is SendResult.NotDelivered -> {
                tally.notReached++
                telling.hold(finding, now)
                history.add(HistoryEntry(now, finding.key.problem, Outcome.GuardianNotReached, detail))
            }
            null -> Unit
        }
        // Last, so a run killed before this point is repeated whole: a duplicate is better than a lost warning.
        findings.put(finding.copy(state = FindingState.Open))
    }

    /** Settings-based findings that stopped appearing are closed. Returns how many. */
    private suspend fun resolveGone(current: List<Finding>, ran: Set<DetectorId>, phone: Snapshot?, now: Instant): Int {
        val stillThere = current.map { it.key }.toSet()
        val resolved = findings.open().filter { it.key.detectorId in ran && it.key.detectorId in SETTING_BASED && it.key !in stillThere }
        resolved.forEach { finding ->
            findings.put(finding.copy(state = FindingState.Resolved, lastSeen = now))
            owner.clear(finding.key)
            closedOutcome(finding, phone)?.let { history.add(HistoryEntry(now, finding.key.problem, it)) }
        }
        return resolved.size
    }

    /**
     * Tells the guardian the owner said an app is fine (Kevin, Oct 11), so the list in My settings is never a secret. Sent, or queued
     * when offline, as its own kind ([Problem.AppAllowed]) with a History line that says the app. Nothing is sent while alerts are off.
     */
    suspend fun tellAllowed(rawLabel: String, phoneLabel: String, now: Instant) {
        val detail = Texts.History.APP_IS.format("“$phoneLabel”")
        val settings = line.current()
        val topic = settings.firstTopic?.takeIf { settings.alertsOn } ?: return
        // Sent off the phone: masked, and an app with no name is not named by its package (findings #98).
        val sent = Texts.Home.guardianLabel(rawLabel)
        val owner = guardianName(settings)
        val body = if (sent.isEmpty()) Texts.Guardian.ALLOWED_NO_NAME.format(owner) else Texts.Guardian.ALLOWED.format(owner, sent)
        sendOrHold(topic, GuardianMessage(title = owner, body = body, urgent = false), Problem.AppAllowed, now, detail)
    }

    /**
     * She said a change was hers. A guardian who was told about *this* warning hears that, so an alarm about a
     * family-approved install does not need a phone call to settle. Nothing is sent to a guardian who was never told
     * (alerts were off, the repeat back-off held it back, or the kind has no guardian text), nor while the original alarm is still waiting in the
     * outbox (the all-clear must not arrive first). For urgent kinds the words never say everything is fine: it may
     * be a scammer coaching her (findings #50). [asTold] is the finding as it was before she marked it seen, still holding the words
     * the guardian's History lines were made from (findings #117).
     */
    suspend fun tellConfirmed(asTold: Finding) {
        val problem = asTold.key.problem
        val settings = line.current()
        val topic = settings.firstTopic?.takeIf { settings.alertsOn } ?: return
        if (!toldAndNotWaiting(asTold)) return
        val text = if (problem.severity == Severity.Urgent) Texts.Guardian.CONFIRMED_URGENT else Texts.Guardian.CONFIRMED
        val message = GuardianMessage(title = guardianName(settings), body = text.format(guardianName(settings)), urgent = false)
        sendOrHold(topic, message, problem, clock.now())
    }

    /**
     * The guardian heard about this very warning, about this app, and that alarm has left the outbox (so the all-clear cannot arrive
     * first). History is matched by what the entry says it was (the app), not only by kind: two apps of the same kind may be told apart
     * (findings #115). An alarm delivered later from the outbox has no line of its own, so it counts only after a failed try for this app.
     * One rule for every line: the detail is made by [HistoryDetail] from the finding as it was told, before seen took the message words
     * out, exactly as the alarm's and the reminder's lines were made, so they always match (findings #117).
     */
    private suspend fun toldAndNotWaiting(finding: Finding): Boolean {
        val problem = finding.key.problem
        val what = HistoryDetail.of(problem, finding.evidence)
        val entries = history.since(finding.firstSeen).filter { it.problem == problem }
        val toldOutcomes = setOf(Outcome.GuardianTold, Outcome.GuardianReminded)
        val told = entries.any { it.detail == what && it.outcome in toldOutcomes } ||
            (entries.any { it.detail == what && it.outcome == Outcome.GuardianNotReached } &&
                entries.any { it.detail == null && it.outcome in toldOutcomes })
        return told && !line.isWaiting(problem)
    }

    /** Sent, or kept for [OutboxFlusher] when it did not go; History says which. */
    private suspend fun sendOrHold(topic: NtfyTopic, message: GuardianMessage, problem: Problem, now: Instant, detail: String? = null) {
        when (line.send(topic, message)) {
            SendResult.Delivered -> history.add(HistoryEntry(now, problem, Outcome.GuardianTold, detail))
            is SendResult.NotDelivered -> {
                line.hold(topic, message, problem, now)
                history.add(HistoryEntry(now, problem, Outcome.GuardianNotReached, detail))
            }
        }
    }

    companion object {
        /**
         * What History says when a question closes by itself, or null for no line. A new-app question whose app now comes from Play or
         * the phone maker's own store closed because Aegida learned more (findings #106), not because she did anything: "No longer a
         * concern." A new-app question whose app is gone, and everything else that stops appearing, was changed back: "Fixed." [phone]
         * is the phone as read in this check (null: nothing known about its apps, so as if gone).
         */
        internal fun closedOutcome(finding: Finding, phone: Snapshot?): Outcome? {
            val app = phone?.apps?.firstOrNull { it.packageName == finding.key.subject }
            return when {
                finding.key.problem != Problem.NewAppOutsidePlay || app == null -> Outcome.Fixed
                // Still on the phone: reassuring only when it now comes from a store Aegida trusts. Anything else (the spying-app list
                // now names it, findings #118) has its own warning, so this question closes without a line.
                app.fromPlayStore || app.fromStoreIn(phone.makerStores) -> Outcome.NoLongerAConcern
                else -> null
            }
        }


        /** A question still open after this is no longer put aside again and again (findings #94). */
        val LONG_OPEN: Duration = Duration.ofHours(24)
        internal val CLOCK_SKEW: Duration = Duration.ofMinutes(5)

        /**
         * Detectors that look at the phone's settings on every run, so a missing finding means "fixed".
         * Event detectors (links, site notifications) only speak when a notification arrives.
         */
        val SETTING_BASED = setOf(
            DetectorId.Dns, DetectorId.Health, DetectorId.Delivery, DetectorId.Drift, DetectorId.NewApp,
            DetectorId.RemoteControl, DetectorId.Stalkerware,
        )
    }
}

/** Every run reports what it did (design section 1: a write reports what it changed). */
data class PipelineResult(
    val alerted: Int,
    val resolved: Int,
    val guardiansNotReached: Int,
    /** Warnings seen again inside their quiet period: nothing was said (findings #94). */
    val quieted: Set<FindingKey> = emptySet(),
    /** Warnings still open after [AlertPipeline.LONG_OPEN] (a question left unanswered). */
    val openLong: Set<FindingKey> = emptySet(),
)

/** The name the guardian sees; blank falls back to the app name, as [DeadMansSwitch] does. */
internal fun guardianName(settings: Guardians) = settings.ownerName.ifBlank { Texts.APP_NAME }

/** A first-seen time from a clock that was ahead is taken as now: the one rule for both the open check and the reminder (findings #99). */
internal fun withFirstSeenPutRight(finding: Finding, now: Instant): Finding =
    if (finding.firstSeen.isAfter(now.plus(AlertPipeline.CLOCK_SKEW))) finding.copy(firstSeen = now) else finding

/**
 * How the alert classes reach the guardian: who the guardian is now ([GuardianSettings]), the one sender and the outbox that holds what
 * did not go. One thing to hand to [AlertPipeline] instead of three.
 */
class GuardianLine @Inject constructor(
    private val settings: GuardianSettings,
    private val ntfy: NtfySender,
    private val outbox: Outbox,
) {
    suspend fun current(): Guardians = settings.current()

    suspend fun send(topic: NtfyTopic, message: GuardianMessage): SendResult = ntfy.send(topic, message)

    /** Keeps a message that did not go, to be retried by [OutboxFlusher]. */
    suspend fun hold(topic: NtfyTopic, message: GuardianMessage, problem: Problem, now: Instant) {
        outbox.add(QueuedMessage(0, topic.value, message.title, message.body, message.urgent, problem, now, 1, now))
    }

    /** True while an earlier message about [problem] is still waiting to be sent. */
    suspend fun isWaiting(problem: Problem): Boolean = outbox.all().any { it.problem == problem }
}
