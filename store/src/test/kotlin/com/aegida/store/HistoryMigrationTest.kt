package com.aegida.store

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Version 2 adds a detail column to History (Kevin, Oct 10). Phones on version 1 keep every row and gain a column that is empty. */
@RunWith(RobolectricTestRunner::class)
class HistoryMigrationTest {
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AegidaDatabase::class.java)

    @Test fun versionOneRowsSurviveAndGetAnEmptyDetail() {
        migrations.createDatabase("history-migration", 1).apply {
            execSQL("INSERT INTO history (rowId, atMillis, historyProblem, outcome) VALUES (1, 1000, 'ScamLink', 'OwnerWarned')")
            close()
        }
        val db = migrations.runMigrationsAndValidate("history-migration", 2, true, AegidaDatabase.MIGRATION_1_2)
        db.query("SELECT atMillis, historyProblem, outcome, detail FROM history").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals(1000L, c.getLong(0))
            assertEquals("ScamLink", c.getString(1))
            assertEquals("OwnerWarned", c.getString(2))
            assertNull(c.getString(3))
        }
    }

    /** Version 3 adds the notice counts and the list of things the owner said are fine (finding #92). Nothing from version 2 is touched. */
    @Test fun versionTwoGainsTheNoticeAndAllowedTablesAndKeepsItsRows() {
        migrations.createDatabase("notice-migration", 2).apply {
            execSQL(
                "INSERT INTO history (rowId, atMillis, historyProblem, outcome, detail) " +
                    "VALUES (1, 1000, 'ScamLink', 'OwnerWarned', 'It came from the app.')",
            )
            close()
        }
        val db = migrations.runMigrationsAndValidate("notice-migration", 3, true, AegidaDatabase.MIGRATION_2_3)
        db.execSQL("INSERT INTO notice (packageName, keyHash, noticeHash, atMillis, signals) VALUES ('a', 'k', 'n', 5, 2)")
        db.execSQL("INSERT INTO allowed (problem, subject, label, atMillis) VALUES ('NoisyApp', 'a', 'A', 5)")
        db.query("SELECT detail FROM history").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals("It came from the app.", c.getString(0))
        }
    }

    /** Version 4 gives the outbox a place for the History detail (findings #116, Kevin approved Oct 14). Notes already waiting are kept, with no detail. */
    @Test fun versionThreeKeepsItsWaitingNotesAndTheyHaveNoDetail() {
        migrations.createDatabase("outbox-migration", 3).apply {
            execSQL(
                "INSERT INTO outbox (rowId, topic, title, body, urgent, outboxProblem, queuedAtMillis, attempts, lastTriedMillis) " +
                    "VALUES (7, 'aegida_x', 'Ruth', 'A note', 0, 'NewAppOutsidePlay', 5000, 2, 6000)",
            )
            close()
        }
        val db = migrations.runMigrationsAndValidate("outbox-migration", 4, true, AegidaDatabase.MIGRATION_3_4)
        db.query("SELECT rowId, topic, title, body, urgent, outboxProblem, queuedAtMillis, attempts, lastTriedMillis, outboxDetail FROM outbox").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals(7L, c.getLong(0))
            assertEquals("aegida_x", c.getString(1))
            assertEquals("A note", c.getString(3))
            assertEquals("NewAppOutsidePlay", c.getString(5))
            assertEquals(2, c.getInt(7))
            assertNull(c.getString(9))
        }
        db.execSQL(
            "INSERT INTO outbox (topic, title, body, urgent, outboxProblem, queuedAtMillis, attempts, lastTriedMillis, outboxDetail) " +
                "VALUES ('t', 'T', 'B', 0, 'ScamLink', 1, 1, 1, 'The app.')",
        )
    }
}
