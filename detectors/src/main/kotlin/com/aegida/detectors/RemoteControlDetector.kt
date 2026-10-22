package com.aegida.detectors

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.RemoteControlApps
import com.aegida.core.text.Problem
import javax.inject.Inject

/**
 * "Is there an app on this phone that lets someone else drive it?" Raised whether it came from Play or not
 * (scammers send people to Play for these), until she says it is hers.
 */
class RemoteControlDetector @Inject constructor() : Detector {
    override val id = DetectorId.RemoteControl

    override fun evaluate(input: Inputs): List<Finding> {
        val baseline = input.baseline ?: return emptyList()
        return input.snapshot.apps
            .filter { it.packageName in RemoteControlApps.PACKAGES && it.packageName !in baseline.acceptedRemoteApps }
            .sortedBy { it.packageName }
            .map {
                Finding(
                    FindingKey(id, Problem.RemoteControlApp, it.packageName),
                    mapOf(Evidence.APP_LABEL to it.label) + it.facts(input.snapshot.makerStores),
                    input.now, input.now,
                )
            }
    }
}
