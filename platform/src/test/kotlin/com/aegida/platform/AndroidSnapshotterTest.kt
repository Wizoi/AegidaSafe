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
}
