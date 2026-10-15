package com.aegida.store

import com.aegida.core.health.ListenerTimes
import com.aegida.core.health.ListenerTimesStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * The last time a check saw the listener bound, and the late notices checked at exactly that time (finding #146 c), as two named values
 * in the settings table written together (findings #112, #134; no new table). Rows left by 0.3.9 (listener_connected_at,
 * listener_disconnected_at) are not read. A list that does not decode counts as empty: those notices are checked again, never skipped.
 */
internal class RoomListenerTimes(private val dao: SettingDao) : ListenerTimesStore {
    override suspend fun times(): ListenerTimes {
        val values = dao.all().associate { it.name to it.value }
        val checked = values[CHECKED_AT_LAST_SEEN]?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()
        return ListenerTimes(values[LAST_SEEN_BOUND]?.toLongOrNull()?.let(Instant::ofEpochMilli), checked.toSet())
    }

    override suspend fun seenBound(at: Instant, checked: Set<String>) = dao.put(
        listOf(
            SettingEntity(LAST_SEEN_BOUND, at.toEpochMilli().toString()),
            SettingEntity(CHECKED_AT_LAST_SEEN, Json.encodeToString(checked.sorted())),
        ),
    )

    internal companion object {
        const val LAST_SEEN_BOUND = "listener_last_seen_bound"
        const val CHECKED_AT_LAST_SEEN = "listener_checked_at_last_seen"
    }
}
