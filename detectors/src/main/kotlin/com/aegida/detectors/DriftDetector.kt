package com.aegida.detectors

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.text.Problem
import javax.inject.Inject

/**
 * "Is something different from when setup finished?" Compares the phone with its baseline: the default
 * browser, text-message and phone apps, and the apps with notification access, screen control or device
 * administration. A change she says was hers moves the baseline, so it is never raised again.
 *
 * A default that is now unset is not a change (the phone is asking, not an app taking over), and with no
 * baseline yet there is nothing to compare with.
 */
class DriftDetector @Inject constructor() : Detector {
    override val id = DetectorId.Drift

    override fun evaluate(input: Inputs): List<Finding> {
        val baseline = input.baseline ?: return emptyList()
        val phone = input.snapshot
        fun finding(problem: Problem, subject: String) =
            Finding(FindingKey(id, problem, subject), emptyMap(), input.now, input.now)

        val role = buildList {
            if (phone.browser != null && phone.browser != baseline.browser) add(finding(Problem.BrowserChanged, phone.browser!!))
            if (phone.smsApp != null && phone.smsApp != baseline.smsApp) add(finding(Problem.SmsAppChanged, phone.smsApp!!))
            if (phone.dialerApp != null && phone.dialerApp != baseline.dialerApp) add(finding(Problem.DialerChanged, phone.dialerApp!!))
            if (phone.assistant != null && phone.assistant != baseline.assistant) add(finding(Problem.AssistantChanged, phone.assistant!!))
        }
        val added = (phone.notificationListeners - baseline.notificationListeners).sorted().map { finding(Problem.ListenerAdded, it) } +
            (phone.accessibilityApps - baseline.accessibilityApps).sorted().map { finding(Problem.AccessibilityAdded, it) } +
            (phone.deviceAdmins - baseline.deviceAdmins).sorted().map { finding(Problem.AdminAdded, it) }
        return role + added
    }
}
