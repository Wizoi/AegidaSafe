package com.aegida.platform.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.aegida.core.text.Texts

/**
 * The only code that creates channels or posts notifications (NotificationSingleOwnerTest). Alerts on
 * Mom's phone are heads-up banners: no full-screen intent, no overlay.
 */
class NotificationPoster(private val context: Context) {

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_STATUS, Texts.CHANNEL_STATUS, NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_NEEDS_TAP, Texts.CHANNEL_NEEDS_TAP, NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    companion object {
        const val CHANNEL_STATUS = "status"
        const val CHANNEL_NEEDS_TAP = "needs_tap"
    }
}
