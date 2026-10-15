package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.HistoryDetail
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.OwnerNotifier
import com.aegida.core.alert.Outcome
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.Snapshotter
import com.aegida.core.notice.AllowList
import com.aegida.core.notice.AllowedItem
import com.aegida.core.store.BaselineStore
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import com.aegida.core.text.Fix
import com.aegida.core.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * What the owner can do to a finding. Findings from events (a scam link, a website pop-up) never clear by
 * themselves, so without this Home would say "needs a tap" forever.
 */
class FindingActions @Inject constructor(
    private val seen: SeenMarker,
    private val allowing: AppAllowing,
    private val accepting: ChangeAcceptor,
    private val gate: AlertGate,
    private val clock: Clock,
) {
    /** Marks an open finding as seen: off Home, banner cleared, one History line. False if there is nothing open to mark. */
    suspend fun acknowledge(key: FindingKey): Boolean = gate.run { seen.markSeen(key, clock.now()) }

    /**
     * "This one is fine": for a warning about an app that sends ads as notices only (never a spying or remote-control app). The app goes on
     * the list shown in My settings, the warning is marked seen, and the guardian is told. False for anything else or if nothing is open.
     */
    suspend fun allow(key: FindingKey): Boolean = gate.run { allowing.allow(key) }

    /** Takes an item off the list in My settings, so Aegida warns about it again, at once: the quiet week is over (finding #93). */
    suspend fun stopAllowing(problem: Problem, subject: String) = gate.run { allowing.stopAllowing(problem, subject) }

    /**
     * "Yes, that was me": the part of the baseline this finding is about moves to what the phone has now, then the
     * finding is marked seen, and the guardian who was told about it hears she said so. False if it is not open or is not a "was that you?" finding.
     */
    suspend fun acceptChange(key: FindingKey): Boolean = gate.run { accepting.accept(key) }
}

/** Marking a finding as seen: the one place that does it, for Acknowledge, "This one is fine" and "Yes, that was me". */
class SeenMarker @Inject constructor(
    private val findings: FindingStore,
    private val history: HistoryLog,
    private val owner: OwnerNotifier,
) {
    suspend fun markSeen(key: FindingKey, now: Instant): Boolean {
        val finding = findings.get(key)?.takeIf { it.state.needsHer } ?: return false
        // Named as the warning was, from the evidence before the message words go, so History turns its row green (findings #129).
        val detail = HistoryDetail.of(key.problem, finding.evidence)
        findings.put(finding.withoutMessage().copy(state = FindingState.Acknowledged, lastSeen = now))
        owner.clear(key)
        history.add(HistoryEntry(now, key.problem, Outcome.Acknowledged, detail))
        return true
    }
}

/** The list of apps she said are fine ("This one is fine"), shown in My settings. */
class AppAllowing @Inject constructor(
    private val findings: FindingStore,
    private val history: HistoryLog,
    private val pipeline: AlertPipeline,
    private val seen: SeenMarker,
    private val allowList: AllowList = AllowList.None,
    private val clock: Clock,
) {
    suspend fun allow(key: FindingKey): Boolean {
        val finding = (if (key.problem in ALLOWABLE) findings.get(key) else null)?.takeIf { it.state.needsHer }
        if (finding == null) return false
        record(key, finding)
        return true
    }

    private suspend fun record(key: FindingKey, finding: Finding) {
        val now = clock.now()
        val raw = finding.evidence[Evidence.APP_LABEL].orEmpty()
        val label = Texts.Home.cleanLabel(raw).ifEmpty { key.subject } // on the phone the package name is fine
        // The guardian first (sent or queued), so a check killed half way never leaves a silent allow (finding #93).
        pipeline.tellAllowed(raw, label, now)
        history.add(HistoryEntry(now, Problem.AppAllowed, Outcome.Acknowledged, Texts.History.APP_IS.format("“$label”")))
        allowList.add(AllowedItem(key.problem, key.subject, label, now))
        seen.markSeen(key, now)
    }

    suspend fun stopAllowing(problem: Problem, subject: String) {
        allowList.remove(problem, subject)
        val quiet = problem.quietAfterSeen
        val stored = ALLOWABLE[problem]?.let { findings.get(FindingKey(it, problem, subject)) }
        if (quiet != null && stored != null && !stored.state.needsHer) {
            findings.put(stored.copy(lastSeen = stored.lastSeen.minus(quiet)))
        }
    }

    private companion object {
        /** What an owner may tell Aegida to leave alone. Not urgent kinds: a coached "it is fine" must not silence a spying app. */
        val ALLOWABLE = mapOf(Problem.NoisyApp to DetectorId.NoisyApp)
    }
}

/** "Yes, that was me": moves the baseline to the phone as it is now. */
class ChangeAcceptor @Inject constructor(
    private val findings: FindingStore,
    private val baselines: BaselineStore,
    private val snapshotter: Snapshotter,
    private val pipeline: AlertPipeline,
    private val seen: SeenMarker,
    private val clock: Clock,
) {
    suspend fun accept(key: FindingKey): Boolean {
        val question = openQuestion(key)
        val baseline = (if (question != null) baselines.baseline() else null)
        if (question == null || baseline == null) return false
        baselines.save(baseline.accepting(key.problem, key.subject, snapshotter.take()))
        val marked = seen.markSeen(key, clock.now())
        // The finding as the guardian was told about it: seen takes out the words a website said (findings #117).
        if (marked) pipeline.tellConfirmed(question)
        return marked
    }

    private suspend fun openQuestion(key: FindingKey): Finding? =
        if (key.problem.fix == Fix.ConfirmChange || key.problem.fix == Fix.ConfirmEvent) findings.get(key)?.takeIf { it.state.needsHer } else null
}
