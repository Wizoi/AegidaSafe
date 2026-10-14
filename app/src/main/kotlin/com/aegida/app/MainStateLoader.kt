package com.aegida.app

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.HistoryRow
import com.aegida.core.alert.HistoryRows
import com.aegida.core.health.CheckLog
import com.aegida.core.model.HomeState
import com.aegida.core.model.Snapshotter
import com.aegida.core.notice.AllowList
import com.aegida.core.notice.AllowedItem
import com.aegida.core.oem.OemSource
import com.aegida.core.oem.Reading
import com.aegida.core.store.FeedStore
import com.aegida.core.store.SetupStore
import com.aegida.core.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** What the stores hold that the screens list: findings, history, the allow list, the feed date and whether the phone may have Auto Blocker. */
class StoredLists @Inject constructor(
    private val findings: FindingStore,
    private val historyLog: HistoryLog,
    private val checkLog: CheckLog,
    private val feedStore: FeedStore,
    private val allowList: AllowList,
    private val oem: OemSource,
) {
    suspend fun home(): HomeState = HomeState.of(findings.open(), checkLog.latest())

    suspend fun history(now: Instant): List<HistoryRow> = HistoryRows.of(historyLog.since(now.minus(HISTORY_SHOWN)))

    suspend fun allowed(): List<AllowedItem> = allowList.all()

    suspend fun blockerRelevant(): Boolean = oem.installBlocker() != Reading.NotPresent

    suspend fun listsUpdatedAt(): Instant? = feedStore.current()?.generatedAt

    private companion object {
        val HISTORY_SHOWN: Duration = Duration.ofDays(90)
    }
}

/** Reads everything the screens draw, in one go, into a [MainState]. */
class MainStateLoader @Inject constructor(
    private val setupStore: SetupStore,
    private val guardianSettings: GuardianSettings,
    private val snapshotter: Snapshotter,
    private val clock: Clock,
    private val lists: StoredLists,
) {
    suspend fun load(): MainState {
        val now = clock.now()
        val snapshot = snapshotter.take()
        val setup = setupStore.setup()
        return MainState(
            loaded = true,
            now = now,
            setup = setup,
            guardians = guardianSettings.current(),
            home = lists.home(),
            history = lists.history(now),
            snapshot = snapshot,
            allowed = lists.allowed(),
            blockerRelevant = lists.blockerRelevant(),
            listsUpdatedAt = lists.listsUpdatedAt(),
        )
    }
}
