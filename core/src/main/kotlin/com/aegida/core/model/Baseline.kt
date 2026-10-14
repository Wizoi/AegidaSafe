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
    /** The websites that already had notifications when setup ended, plus ones she said were hers; null if not recorded yet. */
    val websites: Set<String>? = null,
) {
    /**
     * She said the change behind [problem] to [subject] (the app) was hers. Only that one thing moves into the
     * baseline, and only if it is really on the phone now: a second app that arrived in the same phone call stays
     * a question (findings #50).
     */
    fun accepting(problem: Problem, subject: String, now: Snapshot): Baseline =
        acceptingDefault(problem, subject, now) ?: acceptingAddition(problem, subject, now) ?: this

    /** The change of a default app (browser, texts, calls, assistant); null when [problem] is not one of those. */
    private fun acceptingDefault(problem: Problem, subject: String, now: Snapshot): Baseline? = when (problem) {
        Problem.BrowserChanged -> if (now.browser == subject) copy(browser = subject) else this
        Problem.SmsAppChanged -> if (now.smsApp == subject) copy(smsApp = subject) else this
        Problem.DialerChanged -> if (now.dialerApp == subject) copy(dialerApp = subject) else this
        Problem.AssistantChanged -> if (now.assistant == subject) copy(assistant = subject) else this
        else -> null
    }

    /** Something added to the phone (an app, access, a website); null when [problem] is not one of those. */
    private fun acceptingAddition(problem: Problem, subject: String, now: Snapshot): Baseline? = when (problem) {
        Problem.ListenerAdded -> if (subject in now.notificationListeners) copy(notificationListeners = notificationListeners + subject) else this
        Problem.AccessibilityAdded -> if (subject in now.accessibilityApps) copy(accessibilityApps = accessibilityApps + subject) else this
        Problem.AdminAdded -> if (subject in now.deviceAdmins) copy(deviceAdmins = deviceAdmins + subject) else this
        Problem.NewAppOutsidePlay -> if (now.apps.any { it.packageName == subject }) copy(apps = apps.orEmpty() + subject) else this
        // Android does not list the sites that have notifications (findings #84), so her yes is the only way a site gets here.
        Problem.NewWebsiteNotifications -> copy(websites = websites.orEmpty() + subject)
        Problem.RemoteControlApp ->
            if (subject in RemoteControlApps.PACKAGES && now.apps.any { it.packageName == subject }) {
                copy(acceptedRemoteApps = acceptedRemoteApps + subject)
            } else {
                this
            }
        else -> null
    }

    companion object {
        fun of(snapshot: Snapshot) = Baseline(
            snapshot.browser, snapshot.smsApp, snapshot.dialerApp,
            snapshot.notificationListeners, snapshot.accessibilityApps, snapshot.deviceAdmins,
            assistant = snapshot.assistant,
            apps = userApps(snapshot),
            websites = emptySet(), // every site is asked about once, the first time it notifies
        )

        fun userApps(snapshot: Snapshot): Set<String> = snapshot.apps.filterNot { it.isSystem }.map { it.packageName }.toSet()
    }
}
