package com.aegida.core.model

import com.aegida.core.text.Problem

/**
 * The defaults and special access the phone had when the owner finished setup, plus every change she has since
 * said was hers. Drift is the difference between this and a [Snapshot] made by the same Snapshotter.
 */
data class Baseline(
    val browser: String?,
    val smsApp: String?,
    val dialerApp: String?,
    val notificationListeners: Set<String>,
    val accessibilityApps: Set<String>,
    val deviceAdmins: Set<String>,
    val assistant: String? = null,
    /** The apps that were on the phone (not counting those that shipped with it), or null if not recorded yet. */
    val apps: Set<String>? = null,
    /** Remote-control apps she said were hers. */
    val acceptedRemoteApps: Set<String> = emptySet(),
) {
    /**
     * She said the change behind [problem] to [subject] (the app) was hers. Only that one thing moves into the
     * baseline, and only if it is really on the phone now: a second app that arrived in the same phone call stays
     * a question (findings #50).
     */
    fun accepting(problem: Problem, subject: String, now: Snapshot): Baseline = when (problem) {
        Problem.BrowserChanged -> if (now.browser == subject) copy(browser = subject) else this
        Problem.SmsAppChanged -> if (now.smsApp == subject) copy(smsApp = subject) else this
        Problem.DialerChanged -> if (now.dialerApp == subject) copy(dialerApp = subject) else this
        Problem.AssistantChanged -> if (now.assistant == subject) copy(assistant = subject) else this
        Problem.ListenerAdded -> if (subject in now.notificationListeners) copy(notificationListeners = notificationListeners + subject) else this
        Problem.AccessibilityAdded -> if (subject in now.accessibilityApps) copy(accessibilityApps = accessibilityApps + subject) else this
        Problem.AdminAdded -> if (subject in now.deviceAdmins) copy(deviceAdmins = deviceAdmins + subject) else this
        Problem.NewAppOutsidePlay -> if (now.apps.any { it.packageName == subject }) copy(apps = apps.orEmpty() + subject) else this
        Problem.RemoteControlApp -> if (subject in RemoteControlApps.PACKAGES && now.apps.any { it.packageName == subject }) copy(acceptedRemoteApps = acceptedRemoteApps + subject) else this
        else -> this
    }

    companion object {
        fun of(snapshot: Snapshot) = Baseline(
            snapshot.browser, snapshot.smsApp, snapshot.dialerApp,
            snapshot.notificationListeners, snapshot.accessibilityApps, snapshot.deviceAdmins,
            assistant = snapshot.assistant,
            apps = userApps(snapshot),
        )

        fun userApps(snapshot: Snapshot): Set<String> = snapshot.apps.filterNot { it.isSystem }.map { it.packageName }.toSet()
    }
}
