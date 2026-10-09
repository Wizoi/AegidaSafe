package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.OwnerNotifier
import com.aegida.core.alert.Outcome
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.Snapshotter
import com.aegida.core.store.BaselineStore
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
    private val clock: Clock,
) {
    /** Marks an open finding as seen: off Home, banner cleared, one History line. False if there is nothing open to mark. */
    suspend fun acknowledge(key: FindingKey): Boolean {
        val finding = findings.get(key)?.takeIf { it.state == FindingState.Open } ?: return false
        val now = clock.now()
        findings.put(finding.copy(state = FindingState.Acknowledged, lastSeen = now))
        owner.clear(key)
        history.add(HistoryEntry(now, key.problem, Outcome.Acknowledged))
        return true
    }

    /**
     * "Yes, that was me": the part of the baseline this finding is about moves to what the phone has now, then the
     * finding is marked seen. False if it is not open or is not a "was that you?" finding.
     */
    suspend fun acceptChange(key: FindingKey): Boolean {
        if (key.problem.fix != Fix.ConfirmChange) return false
        if (findings.get(key)?.state != FindingState.Open) return false
        val baseline = baselines.baseline() ?: return false
        baselines.save(baseline.accepting(key.problem, snapshotter.take()))
        return acknowledge(key)
    }
}
