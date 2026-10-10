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
}
