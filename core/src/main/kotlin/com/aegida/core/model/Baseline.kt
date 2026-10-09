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
    /** The apps that were on the phone (not counting those that shipped with it), or null if not recorded yet. */
    val apps: Set<String>? = null,
    /** Remote-control apps she said were hers. */
    val acceptedRemoteApps: Set<String> = emptySet(),
) {
    /** She said the change behind [problem] was hers: only that part of the baseline moves to what the phone has now. */
    fun accepting(problem: Problem, now: Snapshot): Baseline = when (problem) {
        Problem.BrowserChanged -> copy(browser = now.browser)
        Problem.SmsAppChanged -> copy(smsApp = now.smsApp)
        Problem.DialerChanged -> copy(dialerApp = now.dialerApp)
        Problem.ListenerAdded -> copy(notificationListeners = now.notificationListeners)
        Problem.AccessibilityAdded -> copy(accessibilityApps = now.accessibilityApps)
        Problem.AdminAdded -> copy(deviceAdmins = now.deviceAdmins)
        Problem.NewAppOutsidePlay -> copy(apps = userApps(now))
        Problem.RemoteControlApp -> copy(
            acceptedRemoteApps = acceptedRemoteApps + now.apps.map { it.packageName }.filter { it in RemoteControlApps.PACKAGES },
        )
        else -> this
    }

    companion object {
        fun of(snapshot: Snapshot) = Baseline(
            snapshot.browser, snapshot.smsApp, snapshot.dialerApp,
            snapshot.notificationListeners, snapshot.accessibilityApps, snapshot.deviceAdmins,
            apps = userApps(snapshot),
        )

        fun userApps(snapshot: Snapshot): Set<String> = snapshot.apps.filterNot { it.isSystem }.map { it.packageName }.toSet()
    }
}
