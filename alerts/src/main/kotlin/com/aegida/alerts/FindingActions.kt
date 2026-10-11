package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.OwnerNotifier
import com.aegida.core.alert.Outcome
import com.aegida.core.model.Evidence
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.Snapshotter
import com.aegida.core.notice.AllowList
import com.aegida.core.notice.AllowedItem
import com.aegida.core.store.BaselineStore
import com.aegida.core.text.Problem
import com.aegida.core.text.Fix
import com.aegida.core.time.Clock
import javax.inject.Inject

/**
 * What the owner can do to a finding. Findings from events (a scam link, a website pop-up) never clear by
 * themselves, so without this Home would say "needs a tap" forever.
 */
class FindingActions @Inject constructor(
    private val findings: FindingStore,
    private val history: HistoryLog,
    private val owner: OwnerNotifier,
    private val baselines: BaselineStore,
    private val snapshotter: Snapshotter,
    private val pipeline: AlertPipeline,
    private val gate: AlertGate,
    private val allowList: AllowList = AllowList.None,
    private val clock: Clock,
) {
    /** Marks an open finding as seen: off Home, banner cleared, one History line. False if there is nothing open to mark. */
    suspend fun acknowledge(key: FindingKey): Boolean = gate.run { markSeen(key, clock.now()) }

    private suspend fun markSeen(key: FindingKey, now: java.time.Instant): Boolean {
        val finding = findings.get(key)?.takeIf { it.state.needsHer } ?: return false
        findings.put(finding.withoutMessage().copy(state = FindingState.Acknowledged, lastSeen = now))
        owner.clear(key)
        history.add(HistoryEntry(now, key.problem, Outcome.Acknowledged))
        return true
    }

    /**
     * "This one is fine": for a warning about an app that sends ads as notices only (never a spying or remote-control app). The app goes on
     * the list shown in My settings, the warning is marked seen, and the guardian is told. False for anything else or if nothing is open.
     */
    suspend fun allow(key: FindingKey): Boolean = gate.run {
        if (key.problem !in ALLOWABLE) return@run false
        val finding = findings.get(key)?.takeIf { it.state.needsHer } ?: return@run false
        val now = clock.now()
        val label = finding.evidence[Evidence.APP_LABEL].orEmpty().ifBlank { key.subject }
        allowList.add(AllowedItem(key.problem, key.subject, label, now))
        markSeen(key, now)
        pipeline.tellAllowed(label)
        true
    }

    /** Takes an item off the list in My settings, so Aegida warns about it again. */
    suspend fun stopAllowing(problem: Problem, subject: String) = gate.run { allowList.remove(problem, subject) }

    /**
     * "Yes, that was me": the part of the baseline this finding is about moves to what the phone has now, then the
     * finding is marked seen, and the guardian who was told about it hears she said so. False if it is not open or is not a "was that you?" finding.
     */
    suspend fun acceptChange(key: FindingKey): Boolean = gate.run {
        accept(key)
    }

    private suspend fun accept(key: FindingKey): Boolean {
        if (key.problem.fix != Fix.ConfirmChange && key.problem.fix != Fix.ConfirmEvent) return false
        if (findings.get(key)?.state?.needsHer != true) return false
        val baseline = baselines.baseline() ?: return false
        baselines.save(baseline.accepting(key.problem, key.subject, snapshotter.take()))
        val seen = markSeen(key, clock.now())
        if (seen) pipeline.tellConfirmed(key)
        return seen
    }

    private companion object {
        /** What an owner may tell Aegida to leave alone. Not urgent kinds: a coached "it is fine" must not silence a spying app. */
        val ALLOWABLE = setOf(Problem.NoisyApp)
    }
}
