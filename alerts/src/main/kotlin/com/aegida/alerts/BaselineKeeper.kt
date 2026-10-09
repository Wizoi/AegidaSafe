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
        if (baselines.baseline() != null || !setup.setup().done) return
        baselines.save(Baseline.of(snapshot))
    }
}
