package com.aegida.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.aegida.platform.notify.NotificationPoster
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps the persistent "Aegida is looking out for you" notice up. It is visible, has Aegida's own icon and
 * can be swiped away by the owner; it is also what keeps the phone from putting Aegida to sleep when she never
 * opens it (design section 8b, Samsung validation still to do).
 */
@AndroidEntryPoint
class ProtectionService : Service() {

    @Inject lateinit var notifications: NotificationPoster

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notifications.ensureChannels()
        val notice = notifications.statusNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTICE_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTICE_ID, notice)
        }
        return START_STICKY
    }

    companion object {
        private const val NOTICE_ID = 1

        /** Starting is only allowed while the app is visible or right after boot; if Android refuses, the listener still works. */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, ProtectionService::class.java))
            } catch (ignored: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException is an IllegalStateException; nothing to do here.
            }
        }
    }
}
