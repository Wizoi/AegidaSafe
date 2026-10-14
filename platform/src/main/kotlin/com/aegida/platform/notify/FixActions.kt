package com.aegida.platform.notify

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
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
        Problem.SiteNotifications, Problem.NewWebsiteNotifications -> {
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
        Problem.NoisyApp -> appNotificationSettings(finding.key.subject)
        Problem.ListenerOff, Problem.ListenerAdded -> notificationAccess()
        Problem.BrowserChanged, Problem.SmsAppChanged, Problem.DialerChanged, Problem.AssistantChanged -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        Problem.AccessibilityAdded -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        Problem.AdminAdded -> Intent(Settings.ACTION_SECURITY_SETTINGS)
        // The app's own page, where she can uninstall it.
        Problem.NewAppOutsidePlay, Problem.RemoteControlApp, Problem.StalkerwareApp ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", finding.key.subject, null))
        Problem.NotificationsBlocked -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ownPackage)
        else -> null
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Puts [text] on the clipboard (no permission needed) so the owner can paste it instead of remembering it. */
    fun copyToClipboard(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Aegida", text))
    }

    /** Where the owner turns notification access on for Aegida (setup and "Fix my phone"). */
    fun notificationAccess(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens [com.aegida.core.net.PublicPages.HOW_IT_WORKS] in her own browser. The page is only shown to her; Aegida does not fetch it. */
    fun howItWorks(): Intent =
        Intent(Intent.ACTION_VIEW, com.aegida.core.net.PublicPages.HOW_IT_WORKS.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** An app's own notification switch, so the owner can turn its notices off (finding #92). */
    fun appNotificationSettings(packageName: String): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** An app's App info page, where Uninstall is. */
    fun appDetails(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The network settings page, where Private DNS lives (there is no public action for Private DNS itself). */
    fun networkSettings(): Intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Security settings, the closest standard place to Samsung's Auto Blocker (findings #12). */
    fun securitySettings(): Intent = Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the dialer with the number filled in, or the empty dialer. Dialing needs no permission, and the owner presses Call. */
    fun callGuardian(phone: String): Intent {
        val number = phone.filter { it.isDigit() || it == '+' }
        val dial = if (number.isEmpty()) Intent(Intent.ACTION_DIAL) else Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        return dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
