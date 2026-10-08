package com.aegida.app

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.aegida.alerts.CheckRunner
import com.aegida.core.health.Trigger
import com.aegida.platform.notify.NotificationMapper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sees every notification other apps post, hands it to the one check path, and hides the ones the check
 * names (fake website warnings). No decisions here: CheckRunner and the detectors make them.
 */
@AndroidEntryPoint
class AegidaListenerService : NotificationListenerService() {

    @Inject lateinit var checks: CheckRunner

    // A failed check must never crash the process Mom's protection runs in; it is logged only in debug builds.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) Log.w(TAG, "check failed", error)
        },
    )

    override fun onListenerConnected() {
        scope.launch { checks.run(Trigger.AppStart) }
    }

    /** The system can drop the connection (for example after an update); ask to be bound again. */
    override fun onListenerDisconnected() {
        requestRebind(ComponentName(this, AegidaListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val event = NotificationMapper.toEvent(sbn.key, sbn.packageName, sbn.notification, sbn.postTime)
        scope.launch {
            checks.run(Trigger.NotificationPosted, event).notificationsToHide.forEach(::cancelNotification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Aegida"
    }
}
