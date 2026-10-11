package com.aegida.store

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** One row per FindingKey; a repeat replaces the row (FindingStore.put). */
@Entity(tableName = "finding", primaryKeys = ["detectorId", "problem", "subject"])
data class FindingEntity(
    val detectorId: String,
    val problem: String,
    val subject: String,
    val evidenceJson: String,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val state: String,
)

@Dao
interface FindingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: FindingEntity)

    @Query("SELECT * FROM finding WHERE detectorId = :detectorId AND problem = :problem AND subject = :subject")
    suspend fun get(detectorId: String, problem: String, subject: String): FindingEntity?

    @Query("SELECT * FROM finding WHERE state IN ('Open', 'Escalated') ORDER BY firstSeenMillis")
    suspend fun open(): List<FindingEntity>

    @Query("DELETE FROM finding WHERE state = 'Resolved' AND lastSeenMillis < :beforeMillis")
    suspend fun prune(beforeMillis: Long): Int
}

@Entity(tableName = "history")
data class HistoryEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val atMillis: Long,
    val historyProblem: String,
    val outcome: String,
    /** What it was (app, sender, masked address ...), added in schema 2; null for older rows. */
    val detail: String? = null,
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun add(row: HistoryEntity)

    @Query("SELECT * FROM history WHERE atMillis >= :fromMillis ORDER BY atMillis DESC, rowId DESC")
    suspend fun since(fromMillis: Long): List<HistoryEntity>

    @Query("DELETE FROM history WHERE atMillis < :beforeMillis")
    suspend fun prune(beforeMillis: Long): Int
}

/** the owner's choices as key/value rows, so a new setting needs no schema change. */
@Entity(tableName = "setting", primaryKeys = ["name"])
data class SettingEntity(val name: String, val value: String)

@Dao
interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(rows: List<SettingEntity>)

    @Query("SELECT * FROM setting")
    suspend fun all(): List<SettingEntity>
}

/** Guardian messages not delivered yet (core Outbox). */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val topic: String,
    val title: String,
    val body: String,
    val urgent: Boolean,
    val outboxProblem: String,
    val queuedAtMillis: Long,
    val attempts: Int,
    val lastTriedMillis: Long,
)

@Dao
interface OutboxDao {
    @Insert
    suspend fun add(row: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY queuedAtMillis, rowId")
    suspend fun all(): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE rowId = :id")
    suspend fun remove(id: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1, lastTriedMillis = :atMillis WHERE rowId = :id")
    suspend fun failedAgain(id: Long, atMillis: Long)
}

/** One notice an app posted that Aegida counted (finding #92): when and how many ad signals, never its words. */
@Entity(tableName = "notice", indices = [Index("packageName", "atMillis"), Index("notifKey")])
data class NoticeEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val packageName: String,
    /** The notification's own key, so the same notice updated a moment later is not counted twice. */
    val notifKey: String,
    val atMillis: Long,
    val signals: Int,
)

@Dao
interface NoticeDao {
    @Insert
    suspend fun add(row: NoticeEntity)

    @Query("SELECT atMillis FROM notice WHERE notifKey = :key ORDER BY atMillis DESC LIMIT 1")
    suspend fun lastAt(key: String): Long?

    @Query("SELECT * FROM notice WHERE packageName = :packageName AND atMillis >= :fromMillis ORDER BY atMillis")
    suspend fun since(packageName: String, fromMillis: Long): List<NoticeEntity>

    @Query("DELETE FROM notice WHERE atMillis < :beforeMillis")
    suspend fun prune(beforeMillis: Long): Int
}

/** Something the owner told Aegida to leave alone, shown in My settings with a way to take it back. */
@Entity(tableName = "allowed", primaryKeys = ["problem", "subject"])
data class AllowedEntity(val problem: String, val subject: String, val label: String, val atMillis: Long)

@Dao
interface AllowedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: AllowedEntity)

    @Query("SELECT * FROM allowed ORDER BY atMillis DESC")
    suspend fun all(): List<AllowedEntity>

    @Query("DELETE FROM allowed WHERE problem = :problem AND subject = :subject")
    suspend fun remove(problem: String, subject: String)
}
