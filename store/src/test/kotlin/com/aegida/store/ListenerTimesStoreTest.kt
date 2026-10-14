package com.aegida.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aegida.core.health.ListenerTimes
import com.aegida.core.model.Setup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Finding #112: the two listener times are named settings, kept apart from what setup saves. */
@RunWith(RobolectricTestRunner::class)
class ListenerTimesStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AegidaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    @After fun close() = db.close()

    @Test fun theLastBindAndUnbindAreKeptAndSavingSetupDoesNotWipeThem() = runTest {
        val times = RoomListenerTimes(db.settings())
        assertEquals("nothing recorded on a new install", ListenerTimes(), times.times())
        times.connected(now.minus(Duration.ofHours(2)))
        times.disconnected(now.minus(Duration.ofHours(1)))
        times.connected(now)
        RoomSettings(db.settings()).save(Setup("security.cloudflare-dns.com", emptySet()))
        assertEquals(ListenerTimes(connectedAt = now, disconnectedAt = now.minus(Duration.ofHours(1))), times.times())
    }
}
