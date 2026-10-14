package com.aegida.store

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.Outcome
import com.aegida.core.alert.QueuedMessage
import com.aegida.core.health.CheckRun
import com.aegida.core.health.Trigger
import com.aegida.core.model.Baseline
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.Setup
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * docs/scenarios/phone-changes.md PC12: a phone that has used Aegida on database version 1 (the first Play build) is upgraded straight
 * to today's version 3. The rows are written by today's stores into a database of today's shape and copied, column for column, into
 * a version-1 database built from store/schemas/1.json (every table version 1 had is unchanged in shape except history). After the
 * 1-to-2-to-3 migrations the real stores read back the same settings, guardian, baseline, findings, history, outbox and check log.
 * The second test starts from a real version-1 file (finding #113), see store/src/test/resources/upgrade/README.txt.
 */
@RunWith(RobolectricTestRunner::class)
class UpgradeWithDataTest {
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AegidaDatabase::class.java)

    private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    private val topic = NtfyTopic("aegida_" + "Ab3dE5gH".repeat(5))
    private val setup = Setup("security.cloudflare-dns.com", setOf("https://news.example"), pictureId = "sunflower", stepsDone = 6)
    private val guardians = Guardians(
        alertsOn = true, ownerName = "Ruth", firstTopic = topic,
        firstGuardianName = "Kevin", firstGuardianEmail = "kevin@example.com", firstGuardianPhone = "555 0100",
    )
    private val baseline = Baseline(
        "com.android.chrome", "com.google.android.apps.messaging", "com.google.android.dialer", emptySet(), emptySet(), emptySet(),
        apps = setOf("com.example.bank"), websites = setOf("https://news.example"),
    )
    private val finding = Finding(
        FindingKey(DetectorId.Dns, Problem.WebsiteFilterOff, "dns"), emptyMap(), now.minus(Duration.ofDays(2)), now.minus(Duration.ofHours(1)),
        FindingState.Escalated,
    )
    private val historyEntry = HistoryEntry(now.minus(Duration.ofDays(2)), Problem.WebsiteFilterOff, Outcome.GuardianTold)
    private val queued = QueuedMessage(
        id = 0, topic = topic.value, title = "Ruth", body = "A message", urgent = false, problem = Problem.WebsiteFilterOff,
        queuedAt = now.minus(Duration.ofHours(3)), attempts = 2, lastTriedAt = now.minus(Duration.ofHours(1)),
    )
    private val run = CheckRun(Trigger.Daily, now.minus(Duration.ofHours(5)), now.minus(Duration.ofHours(5)).plusSeconds(4))

    /** Today's stores write the rows the way the app writes them; they are returned as raw rows per version-1 table. */
    private suspend fun rowsWrittenByTheApp(): Map<String, List<ContentValues>> {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AegidaDatabase::class.java).allowMainThreadQueries().build()
        val settings = RoomSettings(db.settings())
        settings.save(setup)
        settings.save(guardians)
        settings.save(baseline)
        RoomFindingStore(db.findings()).put(finding)
        RoomHistoryLog(db.history()).add(historyEntry)
        RoomOutbox(db.outbox()).add(queued)
        RoomCheckLog(db.checkRuns()).record(run)
        val versionOneTables = mapOf(
            "setting" to "name, value",
            "finding" to "detectorId, problem, subject, evidenceJson, firstSeenMillis, lastSeenMillis, state",
            "history" to "rowId, atMillis, historyProblem, outcome",
            "outbox" to "rowId, topic, title, body, urgent, outboxProblem, queuedAtMillis, attempts, lastTriedMillis",
            "check_run" to "id, trigger, startedAtMillis, finishedAtMillis",
        )
        val rows = versionOneTables.mapValues { (table, columns) ->
            db.openHelper.readableDatabase.query("SELECT $columns FROM $table").use { c ->
                buildList {
                    while (c.moveToNext()) add(ContentValues().apply {
                        for (i in 0 until c.columnCount) when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> putNull(c.getColumnName(i))
                            android.database.Cursor.FIELD_TYPE_INTEGER -> put(c.getColumnName(i), c.getLong(i))
                            else -> put(c.getColumnName(i), c.getString(i))
                        }
                    })
                }
            }
        }
        db.close()
        return rows
    }

    @Test fun aPhoneUpgradedFromTheFirstPlayBuildKeepsItsSetupGuardianBaselineFindingsHistoryOutboxAndChecks() = runTest {
        val rows = rowsWrittenByTheApp()
        migrations.createDatabase(NAME, 1).apply {
            for ((table, list) in rows) for (values in list) insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
            close()
        }
        migrations.runMigrationsAndValidate(NAME, 3, true, AegidaDatabase.MIGRATION_1_2, AegidaDatabase.MIGRATION_2_3).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AegidaDatabase::class.java, NAME)
            .addMigrations(AegidaDatabase.MIGRATION_1_2, AegidaDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        val settings = RoomSettings(db.settings())
        assertEquals(setup, settings.setup())
        assertEquals(guardians, settings.current())
        assertEquals(baseline, settings.baseline())
        assertEquals(listOf(finding), RoomFindingStore(db.findings()).open())
        assertEquals("history is kept and has no detail yet", listOf(historyEntry), RoomHistoryLog(db.history()).since(now.minus(Duration.ofDays(90))))
        assertEquals(listOf(queued.copy(id = 1)), RoomOutbox(db.outbox()).all())
        assertEquals(run, RoomCheckLog(db.checkRuns()).latest(Trigger.Daily))
        assertEquals("the new tables start empty", emptyList<Any>(), RoomAllowList(db.allowed()).all())
        db.close()
    }

    @Test fun aDatabaseFileWrittenByTheRealVersionOneBuildOnTheEmulatorIsUpgradedToVersionThreeAndReadBackThroughTheRealStores() = runTest {
        val target = ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(NAME_REAL)
        target.parentFile!!.mkdirs()
        val file = checkNotNull(javaClass.getResourceAsStream("/upgrade/aegida-v1-emulator.db")) { "missing resource" }
        file.use { input -> target.outputStream().use { input.copyTo(it) } }
        migrations.runMigrationsAndValidate(NAME_REAL, 3, true, AegidaDatabase.MIGRATION_1_2, AegidaDatabase.MIGRATION_2_3).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AegidaDatabase::class.java, NAME_REAL)
            .addMigrations(AegidaDatabase.MIGRATION_1_2, AegidaDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        val settings = RoomSettings(db.settings())
        val setup = settings.setup()
        assertEquals(8, setup.stepsDone)
        assertEquals("sunflower", setup.pictureId)
        val guardians = settings.current()
        assertEquals("Ruth", guardians.ownerName)
        assertEquals(false, guardians.alertsOn)
        val baseline = checkNotNull(settings.baseline())
        assertEquals("com.android.chrome", baseline.browser)
        assertEquals("com.google.android.apps.messaging", baseline.smsApp)
        assertEquals("com.google.android.dialer", baseline.dialerApp)
        assertEquals(setOf("com.aegida.app", "com.aegida.fixture.noisy"), baseline.apps)
        val finding = checkNotNull(RoomFindingStore(db.findings()).get(FindingKey(DetectorId.Health, Problem.ListsOld, "aegida")))
        assertEquals(FindingState.Resolved, finding.state)
        val history = RoomHistoryLog(db.history()).since(Instant.EPOCH)
        assertEquals(listOf(Problem.ListsOld to Outcome.Fixed), history.map { it.problem to it.outcome })
        assertEquals("the version-1 file kept its history time", 1792001741006L, history.single().at.toEpochMilli())
        assertEquals(emptyList<Any>(), RoomOutbox(db.outbox()).all())
        assertEquals(Trigger.Daily, RoomCheckLog(db.checkRuns()).latest(Trigger.Daily)?.trigger)
        assertEquals("the new tables start empty", emptyList<Any>(), RoomAllowList(db.allowed()).all())
        db.close()
    }

    private companion object {
        const val NAME = "upgrade-with-data"
        const val NAME_REAL = "upgrade-real-v1"
    }
}
