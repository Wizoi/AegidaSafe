package com.aegida.alerts

import com.aegida.core.model.Baseline
import com.aegida.core.model.Snapshot
import com.aegida.core.store.BaselineStore
import com.aegida.core.store.SetupStore
import javax.inject.Inject

/**
 * Takes the baseline once, at the first check after the owner finished setup: what the phone looked like when
 * she said it was how she wants it. Never replaced by a later check (only by her saying a change was hers).
 */
class BaselineKeeper @Inject constructor(
    private val setup: SetupStore,
    private val baselines: BaselineStore,
) {
    suspend fun keep(snapshot: Snapshot) {
        if (!setup.setup().done) return
        val kept = baselines.baseline()
        when {
            kept == null -> baselines.save(Baseline.of(snapshot))
            // A baseline from before apps were recorded: add them once, change nothing else.
            kept.apps == null && snapshot.apps.isNotEmpty() -> baselines.save(kept.copy(apps = Baseline.userApps(snapshot)))
            // A baseline from before websites were recorded: no site is known yet, so each is asked about once.
            kept.websites == null -> baselines.save(kept.copy(websites = emptySet()))
            // A default the baseline could not read at setup (null) is taken once it can be read; any later change is drift.
            (kept.smsApp == null && snapshot.smsApp != null) || (kept.assistant == null && snapshot.assistant != null) ->
                baselines.save(kept.copy(smsApp = kept.smsApp ?: snapshot.smsApp, assistant = kept.assistant ?: snapshot.assistant))
        }
    }
}
