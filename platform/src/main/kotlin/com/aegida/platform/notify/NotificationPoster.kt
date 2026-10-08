package com.aegida.platform.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.aegida.core.alert.MomNotifier
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import com.aegida.platform.R

/**
 * The only code that creates channels or posts notifications (NotificationSingleOwnerTest). Alerts on
 * Mom's phone are heads-up banners: no full-screen intent, no overlay. A tap opens the screen that fixes
 * the problem, or the app when there is no single screen for it.
 */
class NotificationPoster(private val context: Context) : MomNotifier {

    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_STATUS, Texts.CHANNEL_STATUS, NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_NEEDS_TAP, Texts.CHANNEL_NEEDS_TAP, NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    override fun show(finding: Finding) {
        val text = finding.key.problem.forMom
        val notification = Notification.Builder(context, CHANNEL_NEEDS_TAP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Texts.APP_NAME)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(tapIntent(finding))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(tagOf(finding.key), NOTIFICATION_ID, notification)
    }

    /** The persistent notice the foreground service shows, so Mom can always see that Aegida is running. */
    fun statusNotification(): Notification {
        val open = openApp()?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        return Notification.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Texts.APP_NAME)
            .setContentText(Texts.STATUS_NOTICE)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    override fun clear(key: FindingKey) {
        manager.cancel(tagOf(key), NOTIFICATION_ID)
    }

    /** Where a tap goes. All standard Android screens; Samsung's own wording is validated last (design 8b). */
    private fun tapIntent(finding: Finding): PendingIntent? {
        val intent = when (finding.key.problem) {
            Problem.SiteNotifications -> {
                val pkg = finding.evidence[Evidence.PACKAGE]
                val channel = finding.evidence[Evidence.CHANNEL_ID]
                if (pkg != null && channel != null) {
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
                } else {
                    openApp()
                }
            }
            Problem.ListenerOff -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Problem.NotificationsBlocked ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            else -> openApp()
        } ?: return null
        return PendingIntent.getActivity(
            context,
            tagOf(finding.key).hashCode(),
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openApp(): Intent? = context.packageManager.getLaunchIntentForPackage(context.packageName)

    private fun tagOf(key: FindingKey) = "${key.detectorId}:${key.problem}:${key.subject}"

    companion object {
        const val CHANNEL_STATUS = "status"
        const val CHANNEL_NEEDS_TAP = "needs_tap"
        private const val NOTIFICATION_ID = 1000
    }
}
