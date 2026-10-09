package com.aegida.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.aegida.core.model.PrivateDns
import com.aegida.core.oem.OemSource
import com.aegida.core.oem.Reading
import com.aegida.platform.notify.NotificationPoster
import com.aegida.platform.snapshot.AndroidSnapshotter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AndroidSnapshotterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val listener = ComponentName(context.packageName, "com.aegida.app.AegidaListenerService")
    private val now = Instant.now().minus(Duration.ofSeconds(30))

    private class FixedOem(val blocker: Reading<Boolean>, val sleeping: Reading<Boolean>) : OemSource {
        override fun installBlocker() = blocker
        override fun neverSleeping() = sleeping
    }

    private fun take(oem: OemSource = FixedOem(Reading.NotPresent, Reading.NotPresent)) =
        AndroidSnapshotter(context, { now }, oem, listener).take()

    private fun setDns(mode: String?, host: String? = null) {
        Settings.Global.putString(context.contentResolver, "private_dns_mode", mode)
        Settings.Global.putString(context.contentResolver, "private_dns_specifier", host)
    }

    // The probe the snapshotter resolves: an ordinary https link, never opened.
    private val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)

    private fun resolvesTo(packageName: String) {
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { this.packageName = packageName; name = "Main" } }
        shadowOf(context.packageManager).addResolveInfoForIntent(probe, info)
    }

    @Test fun anAbsentPrivateDnsKeyIsAutomaticNotOff() { // findings #14: a never-set phone has no key at all
        assertEquals(PrivateDns.Automatic, take().privateDns)
    }

    @Test fun privateDnsModesAreReadAsTheyAre() {
        setDns("hostname", "Security.Cloudflare-DNS.com")
        assertEquals(PrivateDns.Hostname("security.cloudflare-dns.com"), take().privateDns)
        setDns("off")
        assertEquals(PrivateDns.Off, take().privateDns)
        setDns("opportunistic")
        assertEquals(PrivateDns.Automatic, take().privateDns)
    }

    @Test fun theDefaultBrowserIsTheAppThatResolvesAWebLink() {
        assertNull(take().browser)
        resolvesTo("com.android.chrome")
        assertEquals("com.android.chrome", take().browser)
    }

    @Test fun theSystemChooserMeansNoDefaultBrowser() {
        resolvesTo("android")
        assertNull(take().browser)
    }

    @Test fun listenerAccessIsReadFromTheSystem() {
        assertFalse(take().listenerEnabled)
        shadowOf(notifications).setNotificationListenerAccessGranted(listener, true)
        assertTrue(take().listenerEnabled)
    }

    @Test fun bannersCanBePostedByDefaultAndWithAnOrdinaryChannel() {
        assertTrue(take().canPostNotifications)
        notifications.createNotificationChannel(NotificationChannel(NotificationPoster.CHANNEL_NEEDS_TAP, "x", NotificationManager.IMPORTANCE_HIGH))
        assertTrue(take().canPostNotifications)
    }

    @Test fun bannersAreBlockedWhenTheAppIsSwitchedOff() {
        shadowOf(notifications).setNotificationsEnabled(false)
        assertFalse(take().canPostNotifications)
    }

    // Only the user can switch a channel off and back on; an app cannot raise it again, so one scenario each.
    @Test fun bannersAreBlockedWhenOwnerSwitchedOffTheNeedsTapChannel() {
        notifications.createNotificationChannel(NotificationChannel(NotificationPoster.CHANNEL_NEEDS_TAP, "x", NotificationManager.IMPORTANCE_NONE))
        assertFalse(take().canPostNotifications)
    }

    @Test fun theTimeIsTheClocksAndOemReadingsPassThroughUnchanged() {
        val snapshot = take(FixedOem(Reading.Unreadable, Reading.Present(true)))
        assertEquals(now, snapshot.takenAt)
        assertEquals(Reading.Unreadable, snapshot.installBlocker)
        assertEquals(Reading.Present(true), snapshot.neverSleeping)
    }

    // Special access, as the emulator's settings strings look (package/Class, colon separated).

    @Test fun appsWithNotificationAccessAreReadWithoutAegidaItself() {
        Settings.Secure.putString(
            context.contentResolver, "enabled_notification_listeners",
            "${context.packageName}/com.aegida.app.AegidaListenerService:org.other.app/org.other.Listener",
        )
        assertEquals(setOf("org.other.app"), take().notificationListeners)
    }

    @Test fun appsWithScreenControlAreReadAndNoneMeansEmpty() {
        assertEquals(emptySet<String>(), take().accessibilityApps)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "org.helper/org.helper.Service:org.other/org.other.Svc")
        assertEquals(setOf("org.helper", "org.other"), take().accessibilityApps)
    }

    @Test fun deviceAdminsAreReadByPackage() {
        val admin = android.content.ComponentName("org.mdm", "org.mdm.Admin")
        shadowOf(context.getSystemService(android.app.admin.DevicePolicyManager::class.java)).setActiveAdmin(admin)
        assertEquals(setOf("org.mdm"), take().deviceAdmins)
    }

    @Test fun theDefaultTextAppIsReadFromTheSetting() {
        assertEquals(null, take().smsApp)
        Settings.Secure.putString(context.contentResolver, "sms_default_application", "org.texts")
        assertEquals("org.texts", take().smsApp)
    }

    // Installed apps

    private fun install(pkg: String, label: String, installer: String?, system: Boolean = false, signedWith: ByteArray? = null) {
        val info = android.content.pm.PackageInfo().apply {
            packageName = pkg
            if (signedWith != null) {
                signingInfo = android.content.pm.SigningInfo().also { shadowOf(it).setSignatures(arrayOf(android.content.pm.Signature(signedWith))) }
            }
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                packageName = pkg
                this.name = label
                nonLocalizedLabel = label
                flags = if (system) android.content.pm.ApplicationInfo.FLAG_SYSTEM else 0
            }
        }
        shadowOf(context.packageManager).installPackage(info)
        @Suppress("DEPRECATION") context.packageManager.setInstallerPackageName(pkg, installer)
    }

    @Test fun appsAreReadWithTheirNameTheStoreThatInstalledThemAndWhetherTheyShippedWithThePhone() {
        install("org.play.app", "Play App", "com.android.vending")
        install("org.byhand.app", "By Hand", null)
        install("com.android.phone", "Phone", null, system = true)
        val apps = take().apps.associateBy { it.packageName }
        assertEquals("Play App", apps.getValue("org.play.app").label)
        assertTrue(apps.getValue("org.play.app").fromPlayStore)
        assertFalse(apps.getValue("org.byhand.app").fromPlayStore)
        assertNull(apps.getValue("org.byhand.app").installer)
        assertTrue(apps.getValue("com.android.phone").isSystem)
        assertFalse(apps.getValue("org.byhand.app").isSystem)
    }

    @Test fun theListIsKeptForAFewMinutesBecauseEveryNotificationRunsACheck() {
        var at = now
        val snapshotter = AndroidSnapshotter(context, { at }, FixedOem(Reading.NotPresent, Reading.NotPresent), listener)
        install("org.first.app", "First", "com.android.vending")
        assertTrue(snapshotter.take().apps.any { it.packageName == "org.first.app" })
        install("org.second.app", "Second", null)
        at = at.plus(Duration.ofMinutes(2))
        assertFalse(snapshotter.take().apps.any { it.packageName == "org.second.app" })
        at = at.plus(Duration.ofMinutes(4))
        assertTrue(snapshotter.take().apps.any { it.packageName == "org.second.app" })
    }

    @Test fun aClockSetBackwardsDoesNotFreezeTheList() { // R14: wall clock set ahead, then corrected
        var at = now.plus(Duration.ofDays(2))
        val snapshotter = AndroidSnapshotter(context, { at }, FixedOem(Reading.NotPresent, Reading.NotPresent), listener)
        install("org.first.app", "First", "com.android.vending")
        assertTrue(snapshotter.take().apps.any { it.packageName == "org.first.app" })
        install("org.second.app", "Second", null)
        at = now // two days earlier than when the list was read
        assertTrue(snapshotter.take().apps.any { it.packageName == "org.second.app" })
    }

    @Test fun anAppsSigningCertificateIsReadAsTheSha1HexEchapListsAndSystemAppsAreNotChecked() {
        val certificate = ByteArray(40) { (it * 7).toByte() }
        val expected = java.security.MessageDigest.getInstance("SHA-1").digest(certificate).joinToString("") { "%02x".format(it) }
        install("org.signed.app", "Signed", null, signedWith = certificate)
        install("com.android.phone", "Phone", null, system = true, signedWith = certificate)
        val apps = take().apps.associateBy { it.packageName }
        assertEquals(setOf(expected), apps.getValue("org.signed.app").certSha1s)
        assertEquals(emptySet<String>(), apps.getValue("com.android.phone").certSha1s)
    }
}
