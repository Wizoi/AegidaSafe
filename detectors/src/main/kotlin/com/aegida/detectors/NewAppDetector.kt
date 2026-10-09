package com.aegida.detectors

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.RemoteControlApps
import com.aegida.core.text.Problem
import javax.inject.Inject

/**
 * "Did an app arrive that did not come from the Play Store?" Apps that shipped with the phone, apps that were
 * there at setup, apps from Play and known remote-control apps (RemoteControlDetector owns those) are not raised.
 * No list of bad apps is needed: the way it arrived is what is checked. Nothing is raised before a baseline.
 */
class NewAppDetector @Inject constructor() : Detector {
    override val id = DetectorId.NewApp

    override fun evaluate(input: Inputs): List<Finding> {
        val known = input.baseline?.apps ?: return emptyList()
        return input.snapshot.apps
            .filter { !it.isSystem && !it.fromPlayStore && it.packageName !in known && it.packageName !in RemoteControlApps.PACKAGES }
            .sortedBy { it.packageName }
            .map {
                Finding(FindingKey(id, Problem.NewAppOutsidePlay, it.packageName), mapOf(Evidence.APP_LABEL to it.label), input.now, input.now)
            }
    }
}
