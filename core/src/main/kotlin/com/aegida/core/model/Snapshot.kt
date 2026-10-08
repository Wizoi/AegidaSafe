package com.aegida.core.model

import com.aegida.core.oem.Reading
import java.time.Instant

/**
 * An immutable reading of the phone at one moment. Baseline and current are both made by the same
 * Snapshotter so there is no second reading path. Fields are added as detectors need them.
 */
data class Snapshot(
    val takenAt: Instant,
    val installBlocker: Reading<Boolean>,
    val neverSleeping: Reading<Boolean>,
)
