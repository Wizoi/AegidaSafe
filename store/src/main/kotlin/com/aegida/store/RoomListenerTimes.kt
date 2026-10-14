package com.aegida.store

import com.aegida.core.health.ListenerTimes
import com.aegida.core.health.ListenerTimesStore
import java.time.Instant

/** When the listener was last bound and unbound, as two named values in the settings table (finding #112; no new table). */
internal class RoomListenerTimes(private val dao: SettingDao) : ListenerTimesStore {
    override suspend fun times(): ListenerTimes {
        val v = dao.all().associate { it.name to it.value }
        return ListenerTimes(v[CONNECTED_AT].toInstant(), v[DISCONNECTED_AT].toInstant())
    }

    override suspend fun connected(at: Instant) = dao.put(listOf(SettingEntity(CONNECTED_AT, at.toEpochMilli().toString())))

    override suspend fun disconnected(at: Instant) = dao.put(listOf(SettingEntity(DISCONNECTED_AT, at.toEpochMilli().toString())))

    private fun String?.toInstant(): Instant? = this?.toLongOrNull()?.let(Instant::ofEpochMilli)

    private companion object {
        const val CONNECTED_AT = "listener_connected_at"
        const val DISCONNECTED_AT = "listener_disconnected_at"
    }
}
