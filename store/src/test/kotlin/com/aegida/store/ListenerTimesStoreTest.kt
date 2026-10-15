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

    @Test fun theNoticesCheckedAtTheLastTimeAreKeptWithItAndAnOrdinaryCheckClearsThem() = runTest { // finding #146 c
        val times = RoomListenerTimes(db.settings())
        val keys = setOf("0|com.google.android.apps.messaging|0|g:a\nb|10123", "0|com.google.android.apps.messaging|7|null|10123")
        times.seenBound(now, keys)
        assertEquals(ListenerTimes(now, keys), times.times())
        times.seenBound(now.plusSeconds(1))
        assertEquals(ListenerTimes(now.plusSeconds(1)), times.times())
    }

    @Test fun anOldOrMalformedListOfCheckedNoticesReadsAsEmpty() = runTest { // findings #151 c
        val times = RoomListenerTimes(db.settings())
        for (stored in listOf("", "not json", "{\"a\":1}", "[1,2]", "null")) {
            val bound = SettingEntity("listener_last_seen_bound", now.toEpochMilli().toString())
            db.settings().put(listOf(bound, SettingEntity("listener_checked_at_last_seen", stored)))
            assertEquals(stored, ListenerTimes(now), times.times())
        }
    }
}
