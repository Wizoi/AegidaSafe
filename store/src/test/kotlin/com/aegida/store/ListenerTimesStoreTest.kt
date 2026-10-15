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

/** Findings #112, #134: the last time a check saw the listener bound is a named setting, kept apart from what setup saves. */
@RunWith(RobolectricTestRunner::class)
class ListenerTimesStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AegidaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    @After fun close() = db.close()

    @Test fun theLastTimeACheckSawTheListenerBoundIsKeptAndSavingSetupDoesNotWipeIt() = runTest {
        val times = RoomListenerTimes(db.settings())
        assertEquals("nothing recorded on a new install", ListenerTimes(), times.times())
        times.seenBound(now.minus(Duration.ofHours(2)))
        times.seenBound(now)
        RoomSettings(db.settings()).save(Setup("security.cloudflare-dns.com", emptySet()))
        assertEquals("each check replaces the time before", ListenerTimes(lastSeenBound = now), times.times())
    }
}
