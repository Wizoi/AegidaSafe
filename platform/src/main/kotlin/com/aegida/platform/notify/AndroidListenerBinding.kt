package com.aegida.platform.notify

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import com.aegida.core.health.ListenerBinding

/**
 * Whether the system has the listener [component] bound in this process (finding #134). The listener service runs in the app
 * process (no android:process in the manifest) and sets it; a check reads it through AndroidSnapshotter. One instance per process.
 */
class AndroidListenerBinding(private val context: Context, private val component: ComponentName) : ListenerBinding {
    @Volatile private var bound = false

    override val boundNow: Boolean get() = bound

    override fun markBound(bound: Boolean) { this.bound = bound }

    /** False when it cannot be read: then nothing is asked of Android and nothing waits for a bind. */
    override val accessOn: Boolean
        get() = runCatching {
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        }.getOrDefault(false)

    /** NotificationListenerService.requestRebind (API 24+). Only while access is on; Android refuses it otherwise. */
    override fun requestRebind() {
        if (accessOn) runCatching { NotificationListenerService.requestRebind(component) }
    }
}
