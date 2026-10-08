package com.aegida.detectors

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.Snapshot
import java.time.Instant

/** Each detector owns exactly one question (design section 5). Pure and deterministic. */
interface Detector {
    val id: DetectorId
    fun evaluate(input: Inputs): List<Finding>
}

/** Everything a detector may look at. Baseline, feeds and link events are added with the detectors that need them. */
data class Inputs(
    val snapshot: Snapshot,
    val now: Instant,
)
