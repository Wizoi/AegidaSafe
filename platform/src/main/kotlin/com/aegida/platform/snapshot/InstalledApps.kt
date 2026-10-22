package com.aegida.platform.snapshot

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.aegida.core.model.InstalledApp
import com.aegida.core.time.Clock
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * The phone's app list for [AndroidSnapshotter], which owns the one instance: every app with its name, the store that installed it,
 * its certificates and when it was first installed, kept for [APPS_FRESH_FOR] because a check runs for every notification.
 */
internal class InstalledApps(private val context: Context, private val clock: Clock) {
    private var appsReadAt: Instant? = null
    private var appsRead: List<InstalledApp> = emptyList()

    /** Packages looked up on their own and not found (uninstalled, another profile), with when: not asked again for [APPS_FRESH_FOR]. */
    private val missedAt = mutableMapOf<String, Instant>()

    /**
     * Every app, with the store that installed it. Listing them is the slowest thing a check does and a check
     * runs for every notification, so the list is kept for [APPS_FRESH_FOR]. A package in [wanted] that the kept list does not have
     * (installed since) is looked up on its own and added, so a warning about it can name it (#164); one that is not there is
     * remembered as missing for [APPS_FRESH_FOR], so a stale package never costs a lookup per check.
     */
    @Synchronized
    fun read(wanted: Set<String>): List<InstalledApp> {
        val now = clock.now()
        if (!isFresh(appsReadAt, now)) {
            appsRead = readApps()
            appsReadAt = now
            missedAt.clear()
        }
        val known = appsRead.mapTo(HashSet()) { it.packageName }
        val found = wanted.filter { it !in known && !isFresh(missedAt[it], now) }.mapNotNull { pkg ->
            readApp(pkg).also { if (it == null) missedAt[pkg] = now }
        }
        appsRead = appsRead + found
        return appsRead
    }

    /** Read at [readAt] and still fresh at [now]. A negative elapsed time (the wall clock was set back) counts as stale (R14). */
    private fun isFresh(readAt: Instant?, now: Instant): Boolean {
        if (readAt == null) return false
        val age = Duration.between(readAt, now)
        return !age.isNegative && age < APPS_FRESH_FOR
    }

    @Suppress("DEPRECATION")
    private fun readApps(): List<InstalledApp> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0).map { appFrom(pm, it) }
    }

    /** One package, or null when there is no such package for this user. */
    private fun readApp(pkg: String): InstalledApp? = try {
        val pm = context.packageManager
        appFrom(pm, pm.getApplicationInfo(pkg, 0))
    } catch (expectedNotInstalled: PackageManager.NameNotFoundException) { // uninstalled, or in another profile: nothing to name
        null
    }

    private fun appFrom(pm: PackageManager, info: ApplicationInfo): InstalledApp {
        val system = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val (installer, initiating) = installersOf(pm, info.packageName)
        // Apps that came with the phone are matched by name only and need no install time, which keeps a check to about a second.
        val signed = if (system) null else signedPackageOf(pm, info.packageName)
        return InstalledApp(
            packageName = info.packageName,
            label = labelOf(pm, info),
            installer = installer,
            initiatingInstaller = initiating,
            isSystem = system,
            certSha1s = signed?.let(::certificatesOf).orEmpty(),
            category = info.category,
            firstInstalled = signed?.firstInstallTime?.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        )
    }

    /**
     * The app's own name, or empty when it has none: with no android:label, Android hands back the Application class name or the package
     * name, and neither is a name (findings #169); the owner then reads "Aegida can't read this app's name." and never the package.
     */
    private fun labelOf(pm: PackageManager, info: ApplicationInfo): String {
        val label = pm.getApplicationLabel(info).toString()
        return if (label == info.packageName || label == info.name || label == info.className) "" else label
    }

    /** The package with its signing certificates and install time, from one lookup; null when it is gone since the list was read. */
    @Suppress("DEPRECATION")
    private fun signedPackageOf(pm: PackageManager, pkg: String): PackageInfo? = try {
        pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
    } catch (expectedNotInstalled: PackageManager.NameNotFoundException) { // gone since the list was read: nothing to report
        null
    }

    /** SHA-1 of every certificate the app is signed with (the form Echap lists them in). */
    private fun certificatesOf(info: PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-1").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()

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

    private companion object {
        val APPS_FRESH_FOR: Duration = Duration.ofMinutes(5)
    }
}
