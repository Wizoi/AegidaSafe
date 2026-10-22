package com.aegida.platform.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.aegida.core.alert.OwnerNotifier
import com.aegida.core.model.Finding
import com.aegida.core.model.DetectorId
import com.aegida.core.model.FindingKey
import com.aegida.core.text.Fix
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import com.aegida.platform.R

/**
 * The only code that creates channels or posts notifications (NotificationSingleOwnerTest). Alerts on
 * the owner's phone are heads-up banners: no full-screen intent, no overlay. A tap opens the screen that fixes
 * the problem, or the app when there is no single screen for it.
 */
class NotificationPoster(private val context: Context, private val art: BannerArt = BannerArt { null }) : OwnerNotifier {

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
            .apply { art.bitmap()?.let { setLargeIcon(it) } }
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(tapIntent(finding))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(tagOf(finding.key), NOTIFICATION_ID, notification)
    }

    /** The persistent notice the foreground service shows, so the owner can always see that Aegida is running. */
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

    /**
     * Where a tap goes: a "Was that you?" question opens Aegida's own question page for that warning (Kevin, Oct 22, #164: the banner
     * stays generic, the page names the app or website); others open the screen that fixes the problem, or Aegida itself.
     */
    private fun tapIntent(finding: Finding): PendingIntent? {
        val asks = finding.key.problem.fix == Fix.ConfirmChange || finding.key.problem.fix == Fix.ConfirmEvent
        val intent = (if (asks) openQuestion(finding.key) else FixActions.settingsIntentFor(finding, context.packageName)) ?: openApp() ?: return null
        return PendingIntent.getActivity(
            context,
            tagOf(finding.key).hashCode(),
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openApp(): Intent? = context.packageManager.getLaunchIntentForPackage(context.packageName)

    /** Aegida, told which warning's question to open; brought to the front (or started) with the extras delivered either way. */
    private fun openQuestion(key: FindingKey): Intent? = openApp()?.apply {
        putExtra(EXTRA_DETECTOR, key.detectorId.name)
        putExtra(EXTRA_PROBLEM, key.problem.name)
        putExtra(EXTRA_SUBJECT, key.subject)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private fun tagOf(key: FindingKey) = "${key.detectorId}:${key.problem}:${key.subject}"

    companion object {
        const val CHANNEL_STATUS = "status"
        const val CHANNEL_NEEDS_TAP = "needs_tap"
        private const val NOTIFICATION_ID = 1000

        /** The warning a tapped "Was that you?" banner is about ([FindingKey]); read back by [questionFrom]. */
        const val EXTRA_DETECTOR = "com.aegida.extra.DETECTOR"
        const val EXTRA_PROBLEM = "com.aegida.extra.PROBLEM"
        const val EXTRA_SUBJECT = "com.aegida.extra.SUBJECT"

        /** The warning a banner's tap asked for, or null (opened from the launcher, or extras from an older or foreign intent). */
        fun questionFrom(intent: Intent?): FindingKey? {
            val detector = DetectorId.entries.firstOrNull { it.name == intent?.getStringExtra(EXTRA_DETECTOR) }
            val problem = Problem.entries.firstOrNull { it.name == intent?.getStringExtra(EXTRA_PROBLEM) }
            val subject = intent?.getStringExtra(EXTRA_SUBJECT)
            return if (detector == null || problem == null || subject == null) null else FindingKey(detector, problem, subject)
        }
    }
}
