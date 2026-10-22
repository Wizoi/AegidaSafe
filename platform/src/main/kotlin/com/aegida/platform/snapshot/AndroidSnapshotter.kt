package com.aegida.platform.snapshot

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.core.net.toUri
import com.aegida.core.health.ListenerBinding
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Snapshot
import com.aegida.core.model.Snapshotter
import com.aegida.core.oem.OemSource
import com.aegida.core.time.Clock
import com.aegida.platform.notify.NotificationPoster
import java.time.Instant

/**
 * Reads the phone with plain Android calls. Each field is something Aegida actually read; anything it
 * cannot read stays out (design principle: never claim what it cannot verify).
 *
 * [listener] is the app's own notification listener service (named by :app, which owns the class); [binding] says whether the
 * system has it bound in this process now.
 */
class AndroidSnapshotter(
    private val context: Context,
    private val clock: Clock,
    private val oem: OemSource,
    private val listener: ComponentName,
    private val binding: ListenerBinding = ListenerBinding.None,
) : Snapshotter {

    /** One app list per snapshotter, so one 5-minute cache (R5). */
    private val installedApps = InstalledApps(context, clock)

    /** The packages in one of the settings that list enabled services ("package/Class:package/Class"). */
    private fun enabledPackages(setting: String) = packagesIn(Settings.Secure.getString(context.contentResolver, setting))

    override fun take(alsoApps: Set<String>): Snapshot {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val read = Snapshot(
            takenAt = clock.now(),
            privateDns = readPrivateDns(),
            browser = readDefaultBrowser(),
            listenerEnabled = notifications.isNotificationListenerAccessGranted(listener),
            canPostNotifications = canPost(notifications),
            installBlocker = oem.installBlocker(),
            neverSleeping = oem.neverSleeping(),
            smsApp = defaultHandler(Intent(Intent.ACTION_SENDTO, "smsto:5550100".toUri())),
            assistant = defaultHandler(Intent(Intent.ACTION_ASSIST)),
            dialerApp = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
            notificationListeners = enabledPackages(ENABLED_LISTENERS) - context.packageName,
            accessibilityApps = enabledPackages(ENABLED_ACCESSIBILITY),
            deviceAdmins = context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins.orEmpty().map { it.packageName }.toSet(),
            listenerBound = binding.boundNow,
            aegidaUpdatedAt = runCatching { Instant.ofEpochMilli(context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime) }.getOrNull(),
        )
        val wanted = alsoApps + setOfNotNull(read.browser, read.smsApp, read.dialerApp, read.assistant) +
            read.notificationListeners + read.accessibilityApps + read.deviceAdmins
        val apps = installedApps.read(wanted)
        return read.copy(apps = apps, makerStores = oem.appStores().filter { store -> apps.any { it.packageName == store && it.isSystem } }.toSet())
    }

    /** The settings that list enabled services hold "package/Class:package/Class"; only the packages matter. */
    internal fun packagesIn(raw: String?): Set<String> =
        raw.orEmpty().split(':').mapNotNull { ComponentName.unflattenFromString(it)?.packageName }.toSet()

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
    private fun readDefaultBrowser(): String? =
        defaultHandler(Intent(Intent.ACTION_VIEW, "https://example.com".toUri()).addCategory(Intent.CATEGORY_BROWSABLE))

    /**
     * The app that would open [probe] by default, found by asking the system to resolve it, never by launching it.
     * "android" is the system chooser, which means no default is set. Ordinary apps cannot list app roles, and the
     * text-message and assistant settings are hidden on newer Android, so the resolution is the one way to read them.
     * (This is not an SMS API: no permission, nothing is read or sent.)
     */
    private fun defaultHandler(probe: Intent): String? =
        context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeIf { it != "android" }

    /** Banners need the app allowed and the "needs a tap" channel not switched off. */
    private fun canPost(notifications: NotificationManager): Boolean {
        if (!notifications.areNotificationsEnabled()) return false
        val channel = notifications.getNotificationChannel(NotificationPoster.CHANNEL_NEEDS_TAP)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private companion object {
        const val ENABLED_LISTENERS = "enabled_notification_listeners"
        const val ENABLED_ACCESSIBILITY = "enabled_accessibility_services" // ENABLED_ACCESSIBILITY_SERVICES
        const val PRIVATE_DNS_MODE = "private_dns_mode"
        const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"
    }
}
