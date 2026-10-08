package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.MomNotifier
import com.aegida.core.alert.Outcome
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.time.Clock
import javax.inject.Inject

/**
 * What Mom can do to a finding. Findings from events (a scam link, a website pop-up) never clear by
 * themselves, so without this Home would say "needs a tap" forever.
 */
class FindingActions @Inject constructor(
    private val findings: FindingStore,
    private val history: HistoryLog,
    private val mom: MomNotifier,
    private val clock: Clock,
) {
    /** Marks an open finding as seen: off Home, banner cleared, one History line. False if there is nothing open to mark. */
    suspend fun acknowledge(key: FindingKey): Boolean {
        val finding = findings.get(key)?.takeIf { it.state == FindingState.Open } ?: return false
        val now = clock.now()
        findings.put(finding.copy(state = FindingState.Acknowledged, lastSeen = now))
        mom.clear(key)
        history.add(HistoryEntry(now, key.problem, Outcome.Acknowledged))
        return true
    }
}
