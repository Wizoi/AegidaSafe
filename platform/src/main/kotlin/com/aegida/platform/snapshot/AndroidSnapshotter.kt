package com.aegida.platform.snapshot

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.core.net.toUri
import com.aegida.core.model.InstalledApp
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Snapshot
import com.aegida.core.model.Snapshotter
import com.aegida.core.oem.OemSource
import com.aegida.core.time.Clock
import com.aegida.platform.notify.NotificationPoster
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

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
        val apps = apps()
        return Snapshot(
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
            notificationListeners = packagesIn(Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS)) - context.packageName,
            accessibilityApps = packagesIn(Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)),
            deviceAdmins = context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins.orEmpty().map { it.packageName }.toSet(),
            apps = apps,
            makerStores = oem.appStores().filter { store -> apps.any { it.packageName == store && it.isSystem } }.toSet(),
        )
    }

    private var appsReadAt: Instant? = null
    private var appsRead: List<InstalledApp> = emptyList()

    /**
     * Every app, with the store that installed it. Listing them is the slowest thing a check does and a check
     * runs for every notification, so the list is kept for [APPS_FRESH_FOR]. An app installed a minute ago is
     * seen by the next check after that.
     */
    @Synchronized
    private fun apps(): List<InstalledApp> {
        val now = clock.now()
        val readAt = appsReadAt
        // A negative elapsed time (the wall clock was set back) counts as stale, so the list is re-read (R14).
        if (readAt != null) {
            val age = Duration.between(readAt, now)
            if (!age.isNegative && age < APPS_FRESH_FOR) return appsRead
        }
        appsRead = readApps()
        appsReadAt = now
        return appsRead
    }

    @Suppress("DEPRECATION")
    private fun readApps(): List<InstalledApp> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0).map { info ->
            val system = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val (installer, initiating) = installersOf(pm, info.packageName)
            InstalledApp(
                packageName = info.packageName,
                label = pm.getApplicationLabel(info).toString(),
                installer = installer,
                initiatingInstaller = initiating,
                isSystem = system,
                // Apps that came with the phone are matched by name only, which keeps a check to about a second.
                certSha1s = if (system) emptySet() else certificatesOf(pm, info.packageName),
                category = info.category,
            )
        }
    }

    /** SHA-1 of every certificate the app is signed with (the form Echap lists them in). */
    @Suppress("DEPRECATION")
    private fun certificatesOf(pm: PackageManager, pkg: String): Set<String> = try {
        val signers = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners.orEmpty()
        signers.map { signature ->
            MessageDigest.getInstance("SHA-1").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    } catch (expectedNotInstalled: PackageManager.NameNotFoundException) { // gone since the list was read: no certificates to report
        emptySet()
    }

    /**
     * The store that installed the app and the app that asked for the install, from one lookup. Android 10 records only the first,
     * so the second is null there (a maker's store is then not trusted, GS19).
     */
    @Suppress("DEPRECATION")
    private fun installersOf(pm: PackageManager, pkg: String): Pair<String?, String?> = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(pkg).let { it.installingPackageName to it.initiatingPackageName }
        } else {
            pm.getInstallerPackageName(pkg) to null
        }
    } catch (expectedNotInstalled: PackageManager.NameNotFoundException) { // gone since the list was read: no installer known
        null to null
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
        val APPS_FRESH_FOR: Duration = Duration.ofMinutes(5)
        const val ENABLED_LISTENERS = "enabled_notification_listeners"
        const val PRIVATE_DNS_MODE = "private_dns_mode"
        const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"
    }
}
