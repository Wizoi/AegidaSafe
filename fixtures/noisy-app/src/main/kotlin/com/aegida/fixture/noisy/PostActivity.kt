package com.aegida.fixture.noisy

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle

/**
 * Posts one notice and finishes. Everything comes from the start intent, so the emulator script can make each kind:
 *   am start -n com.aegida.fixture.noisy/.PostActivity --ei id 1 --es title "File converter is ready!" --es text "Convert"
 *     --es channel offers --es channelName "Special offers" --ei importance 4 --es category promo
 * Test fixture only (finding #92); contains no real ad.
 */
class PostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras ?: Bundle()
        val manager = getSystemService(NotificationManager::class.java)
        val channelId = extras.getString("channel") ?: "general"
        val channel = NotificationChannel(channelId, extras.getString("channelName") ?: "General", extras.getInt("importance", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(channel)
        val notice = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(extras.getString("title") ?: "Fixture")
            .setContentText(extras.getString("text") ?: "")
            .apply { extras.getString("category")?.let { setCategory(it) } }
            .build()
        manager.notify(extras.getInt("id", 1), notice)
        finish()
    }
}
