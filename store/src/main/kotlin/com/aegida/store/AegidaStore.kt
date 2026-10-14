package com.aegida.store

import android.content.Context
import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.Outbox
import com.aegida.core.health.CheckLog
import com.aegida.core.health.ListenerTimesStore
import com.aegida.core.notice.AllowList
import com.aegida.core.notice.NoticeLog
import com.aegida.core.store.BaselineStore
import com.aegida.core.store.FeedStore
import com.aegida.core.store.SetupStore
import java.io.File

/**
 * What the rest of the app gets from :store: the :core ports, never Room types. Make one per process
 * (the app's composition root holds it as a singleton).
 */
class AegidaStore(context: Context) {
    private val db = AegidaDatabase.open(context.applicationContext)
    private val settings = RoomSettings(db.settings())

    val checkLog: CheckLog = RoomCheckLog(db.checkRuns())
    val findings: FindingStore = RoomFindingStore(db.findings())
    val history: HistoryLog = RoomHistoryLog(db.history())
    val outbox: Outbox = RoomOutbox(db.outbox())
    val notices: NoticeLog = RoomNoticeLog(db.notices())
    val allowList: AllowList = RoomAllowList(db.allowed())
    val setup: SetupStore = settings
    val listenerTimes: ListenerTimesStore = RoomListenerTimes(db.settings())
    val guardians: GuardianSettings = settings
    val baselines: BaselineStore = settings
    val feed: FeedStore = FileFeedStore(File(context.applicationContext.filesDir, "feed"))
}
