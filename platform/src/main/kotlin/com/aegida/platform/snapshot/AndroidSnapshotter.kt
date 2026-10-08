package com.aegida.platform.snapshot

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Snapshot
import com.aegida.core.model.Snapshotter
import com.aegida.core.oem.OemSource
import com.aegida.core.time.Clock
import com.aegida.platform.notify.NotificationPoster

/**
 * Reads the phone with plain Android calls. Each field is something Aegida actually read; anything it
 * cannot read stays out (design principle: never claim what it cannot verify).
 *
 * [listener] is the app's own notification listener service (named by :app, which owns the class).
 */
class AndroidSnapshotter(
    private val context: Context,
    private val clock: Clock,
    private val oem: OemSource,
    private val listener: ComponentName,
) : Snapshotter {

    override fun take(): Snapshot {
        val notifications = context.getSystemService(NotificationManager::class.java)
        return Snapshot(
            takenAt = clock.now(),
            privateDns = readPrivateDns(),
            browser = readDefaultBrowser(),
            listenerEnabled = notifications.isNotificationListenerAccessGranted(listener),
            canPostNotifications = canPost(notifications),
            installBlocker = oem.installBlocker(),
            neverSleeping = oem.neverSleeping(),
        )
    }

    /** An absent key is Android's default (Automatic), not "off" (findings #14). */
    private fun readPrivateDns(): PrivateDns {
        val resolver = context.contentResolver
        return PrivateDns.fromSettings(
            mode = Settings.Global.getString(resolver, PRIVATE_DNS_MODE),
            specifier = Settings.Global.getString(resolver, PRIVATE_DNS_SPECIFIER),
        )
    }

    /**
     * The app that opens an ordinary web link by default. Only resolved, never launched. "android" is the
     * system chooser, which means no default is set. App roles cannot be listed by an ordinary app.
     */
    private fun readDefaultBrowser(): String? {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        val resolved = context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName?.takeIf { it != "android" }
    }

    /** Banners need the app allowed and the "needs a tap" channel not switched off. */
    private fun canPost(notifications: NotificationManager): Boolean {
        if (!notifications.areNotificationsEnabled()) return false
        val channel = notifications.getNotificationChannel(NotificationPoster.CHANNEL_NEEDS_TAP)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private companion object {
        const val PRIVATE_DNS_MODE = "private_dns_mode"
        const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"
    }
}
