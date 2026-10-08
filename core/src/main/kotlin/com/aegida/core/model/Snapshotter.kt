package com.aegida.core.model

/**
 * The one way to read the phone into a [Snapshot]. The baseline and the current reading both come from
 * here, so there is no second reading path (design section 1). Implemented in :platform.
 */
fun interface Snapshotter {
    fun take(): Snapshot
}
