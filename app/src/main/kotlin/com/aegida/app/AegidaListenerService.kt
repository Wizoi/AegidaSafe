package com.aegida.app

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.aegida.alerts.CheckRunner
import com.aegida.alerts.ListenerLink
import com.aegida.core.health.Trigger
import com.aegida.platform.notify.NotificationMapper
import com.aegida.platform.notify.RankingFacts
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

    @Inject lateinit var link: ListenerLink

    // A failed check must never crash the process the owner's protection runs in; it is logged only in debug builds.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) Log.w(TAG, "check failed", error)
        },
    )

    /** Checks the phone, then the notices that came while Aegida was not listening (Android does not deliver those again). */
    override fun onListenerConnected() {
        // Read now, on the binder thread: the shade can change while the checks run.
        val inShade = runCatching { activeNotifications.orEmpty().toList() }.getOrDefault(emptyList())
        val ranking = runCatching { currentRanking }.getOrNull()
        val events = inShade.map { eventOf(it, ranking) }
        scope.launch {
            // Each missed notice's hide and snooze, as soon as its check is done (finding #145).
            link.connected(events, packageName) { actions ->
                actions.hide.forEach(::cancelNotification)
                actions.snooze.forEach { snoozeNotification(it, SNOOZE_MILLIS) }
            }
        }
    }

    /**
     * The system can drop the connection (for example after an update): no longer bound, and ask to be bound again. Android does
     * not reliably call this (finding #134); the daily check also asks for a rebind when the listener is not bound.
     */
    override fun onListenerDisconnected() {
        link.disconnected()
        requestRebind(ComponentName(this, AegidaListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (sbn.packageName == packageName) return
        val event = eventOf(sbn, rankingMap)
        scope.launch {
            val outcome = checks.run(Trigger.NotificationPosted, event)
            outcome.notificationsToHide.forEach(::cancelNotification)
            outcome.notificationsToSnooze.forEach { snoozeNotification(it, SNOOZE_MILLIS) }
        }
    }

    private fun eventOf(sbn: StatusBarNotification, rankingMap: RankingMap?) =
        NotificationMapper.toEvent(sbn.key, sbn.packageName, sbn.notification, sbn.postTime, rankingMap?.let { rankingFactsOf(sbn.key, it) })

    /** What the phone's ranking knows about this notice: its channel's name, how it is shown, and whether the owner tends to dismiss it. */
    private fun rankingFactsOf(key: String, rankingMap: RankingMap): RankingFacts? {
        val ranking = Ranking()
        if (!rankingMap.getRanking(key, ranking)) return null
        val channel = ranking.channel
        return RankingFacts(
            channelName = channel?.name?.toString(),
            channelDescription = channel?.description,
            importance = ranking.importance,
            conversation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ranking.isConversation, // API 31+; older phones say no
            sentimentNegative = ranking.userSentiment == Ranking.USER_SENTIMENT_NEGATIVE,
        )
    }

    override fun onDestroy() {
        link.disconnected()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Aegida"

        /** How long a notification from a website that was asked about is put aside; the phone then brings it back for the next check. */
        const val SNOOZE_MILLIS = 60L * 60 * 1000
    }
}
