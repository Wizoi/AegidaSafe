package com.aegida.store

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.aegida.core.health.CheckRun
import com.aegida.core.health.Trigger
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
class RoomCheckLogTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AegidaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val log = RoomCheckLog(db.checkRuns())
    private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    @get:Rule val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AegidaDatabase::class.java,
    )

    @After fun close() = db.close()

    @Test fun emptyLogHasNoLatestRun() = runTest {
        assertNull(log.latest())
    }

    @Test fun latestIsTheMostRecentlyStartedRunWithEveryFieldKept() = runTest {
        val older = CheckRun(Trigger.Daily, now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(1)).plusSeconds(3))
        val newer = CheckRun(Trigger.PackageChanged, now.minus(Duration.ofMinutes(5)), null)
        log.record(newer)
        log.record(older)
        assertEquals(newer, log.latest())
    }

    @Test fun latestForATriggerIgnoresNewerRunsOfOtherTriggers() = runTest {
        val daily = CheckRun(Trigger.Daily, now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(2)))
        log.record(daily)
        log.record(CheckRun(Trigger.NotificationPosted, now.minus(Duration.ofMinutes(1)), now))
        assertEquals(daily, log.latest(Trigger.Daily))
        assertNull(log.latest(Trigger.SettingChanged))
    }

    @Test fun pruneDropsRunsOlderThanTheCutoffButNeverTheNewestDailyOne() = runTest {
        val oldDaily = CheckRun(Trigger.Daily, now.minus(Duration.ofDays(50)), now.minus(Duration.ofDays(50)))
        val olderDaily = CheckRun(Trigger.Daily, now.minus(Duration.ofDays(60)), null)
        val oldOther = CheckRun(Trigger.NotificationPosted, now.minus(Duration.ofDays(45)), null)
        val recent = CheckRun(Trigger.NotificationPosted, now.minus(Duration.ofDays(2)), null)
        listOf(olderDaily, oldOther, oldDaily, recent).forEach { log.record(it) }
        assertEquals(2, log.prune(now.minus(Duration.ofDays(30)))) // olderDaily and oldOther
        assertEquals(oldDaily, log.latest(Trigger.Daily))
        assertEquals(recent, log.latest())
        assertEquals(0, log.prune(now.minus(Duration.ofDays(30))))
    }

    @Test fun pruneOnAnEmptyLogDoesNothing() = runTest {
        assertEquals(0, log.prune(now))
    }

    @Test fun exportedSchemaVersion1Opens() {
        migrations.createDatabase("schema-check", 1).close()
    }
}
