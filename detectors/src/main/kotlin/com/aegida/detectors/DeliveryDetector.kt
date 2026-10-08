package com.aegida.detectors

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.text.Problem
import java.time.Duration
import javax.inject.Inject

/** Owns "can Aegida still reach the guardian?": a message held for a day means nobody has been told. */
class DeliveryDetector @Inject constructor() : Detector {
    override val id = DetectorId.Delivery

    override fun evaluate(input: Inputs): List<Finding> {
        val oldest = input.oldestUndelivered ?: return emptyList()
        if (Duration.between(oldest, input.now) <= UNREACHABLE_AFTER) return emptyList()
        return listOf(Finding(FindingKey(id, Problem.GuardianUnreachable, "guardian"), emptyMap(), input.now, input.now))
    }

    companion object {
        val UNREACHABLE_AFTER: Duration = Duration.ofHours(24)
    }
}
