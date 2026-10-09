package com.aegida.platform

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import com.aegida.platform.notify.NotificationPoster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class NotificationPosterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val poster = NotificationPoster(context).also { it.ensureChannels() }
    private val now = Instant.now()

    private fun finding(problem: Problem, detector: DetectorId, subject: String, evidence: Map<String, String> = emptyMap()) =
        Finding(FindingKey(detector, problem, subject), evidence, now, now)

    private val siteFinding = finding(
        Problem.SiteNotifications, DetectorId.PopupSpam, "http://localhost:8765",
        mapOf(Evidence.PACKAGE to "com.android.chrome", Evidence.CHANNEL_ID to "web:http://localhost:8765;13435952006713133"),
    )

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    @Test fun theNeedsTapChannelIsAHeadsUpChannelAndTheStatusOneIsQuiet() {
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(NotificationPoster.CHANNEL_NEEDS_TAP).importance)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(NotificationPoster.CHANNEL_STATUS).importance)
    }

    @Test fun aBannerCarriesTheProblemSentenceUnderTheAppNameAndNeverTakesOverTheScreen() {
        poster.show(siteFinding)
        val n = posted().single()
        assertEquals(NotificationPoster.CHANNEL_NEEDS_TAP, n.channelId)
        assertEquals(Texts.APP_NAME, n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(Problem.SiteNotifications.forMom, n.extras.getString(Notification.EXTRA_TEXT))
        assertNull("no full-screen takeover", n.fullScreenIntent)
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test fun aBannerCarriesHerPictureWhenThereIsOneAndNothingWhenThereIsNot() {
        val picture = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
        NotificationPoster(context, com.aegida.platform.notify.BannerArt { picture }).show(siteFinding)
        assertTrue(posted().single().getLargeIcon() != null)
        manager.cancelAll()
        poster.show(siteFinding)
        assertNull(posted().single().getLargeIcon())
    }

    @Test fun aSiteBannerOpensThatSitesOwnSwitch() {
        poster.show(siteFinding)
        val intent = shadowOf(posted().single().contentIntent).savedIntent
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals("com.android.chrome", intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals("web:http://localhost:8765;13435952006713133", intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test fun permissionProblemsOpenTheScreenThatFixesThem() {
        poster.show(finding(Problem.ListenerOff, DetectorId.Health, "aegida"))
        poster.show(finding(Problem.NotificationsBlocked, DetectorId.Health, "aegida2"))
        val actions = posted().map { shadowOf(it.contentIntent).savedIntent.action }
        assertEquals(setOf(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, Settings.ACTION_APP_NOTIFICATION_SETTINGS), actions.toSet())
    }

    @Test fun oneBannerPerFindingAndClearRemovesOnlyThatOne() {
        val dns = finding(Problem.WebsiteFilterOff, DetectorId.Dns, "security.cloudflare-dns.com")
        poster.show(siteFinding)
        poster.show(dns)
        poster.show(dns) // same finding again replaces its banner
        assertEquals(2, posted().size)
        poster.clear(dns.key)
        assertEquals(listOf(Problem.SiteNotifications.forMom), posted().map { it.extras.getString(Notification.EXTRA_TEXT) })
    }

    @Test fun theStatusNoticeIsPersistentQuietAndSaysAegidaIsProtectingYou() {
        val n = poster.statusNotification()
        assertEquals(NotificationPoster.CHANNEL_STATUS, n.channelId)
        assertEquals(Texts.APP_NAME, n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Aegida is protecting you", n.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNull(n.fullScreenIntent)
    }
}
