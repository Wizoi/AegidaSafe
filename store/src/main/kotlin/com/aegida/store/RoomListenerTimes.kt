package com.aegida.store

import com.aegida.core.health.ListenerTimes
import com.aegida.core.health.ListenerTimesStore
import java.time.Instant

/**
 * The last time a check saw the listener bound, as one named value in the settings table (findings #112, #134; no new table).
 * Rows left by 0.3.9 (listener_connected_at, listener_disconnected_at) are not read.
 */
internal class RoomListenerTimes(private val dao: SettingDao) : ListenerTimesStore {
    override suspend fun times(): ListenerTimes =
        ListenerTimes(dao.all().firstOrNull { it.name == LAST_SEEN_BOUND }?.value?.toLongOrNull()?.let(Instant::ofEpochMilli))

    override suspend fun seenBound(at: Instant) = dao.put(listOf(SettingEntity(LAST_SEEN_BOUND, at.toEpochMilli().toString())))

    internal companion object {
        const val LAST_SEEN_BOUND = "listener_last_seen_bound"
    }
}
