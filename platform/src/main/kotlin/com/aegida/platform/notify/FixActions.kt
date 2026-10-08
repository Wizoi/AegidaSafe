package com.aegida.platform.notify

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.text.Problem

/**
 * The one place that knows which standard Android screen fixes a problem. Used by the banner (tap) and by
 * the app ("Fix my phone"), so they cannot disagree. All AOSP actions; Samsung's own wording is validated
 * last (design 8b).
 */
object FixActions {

    /** The Android screen that fixes [finding], or null when the fix is in Aegida's own screens. */
    fun settingsIntentFor(finding: Finding, ownPackage: String): Intent? = when (finding.key.problem) {
        Problem.SiteNotifications -> {
            val pkg = finding.evidence[Evidence.PACKAGE]
            val channel = finding.evidence[Evidence.CHANNEL_ID]
            if (pkg != null && channel != null) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
            } else {
                null
            }
        }
        Problem.ListenerOff -> notificationAccess()
        Problem.NotificationsBlocked -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ownPackage)
        else -> null
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Where Mom turns notification access on for Aegida (setup and "Fix my phone"). */
    fun notificationAccess(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The network settings page, where Private DNS lives (there is no public action for Private DNS itself). */
    fun networkSettings(): Intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Security settings, the closest standard place to Samsung's Auto Blocker (findings #12). */
    fun securitySettings(): Intent = Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the dialer with the number filled in, or the empty dialer. Dialing needs no permission, and Mom presses Call. */
    fun callGuardian(phone: String): Intent {
        val number = phone.filter { it.isDigit() || it == '+' }
        val dial = if (number.isEmpty()) Intent(Intent.ACTION_DIAL) else Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        return dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
