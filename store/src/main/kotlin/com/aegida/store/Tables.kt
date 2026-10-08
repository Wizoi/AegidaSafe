package com.aegida.store

import androidx.room.Dao
import androidx.room.Entity
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

    @Query("SELECT * FROM finding WHERE state = 'Open' ORDER BY firstSeenMillis")
    suspend fun open(): List<FindingEntity>
}

@Entity(tableName = "history")
data class HistoryEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val atMillis: Long,
    val historyProblem: String,
    val outcome: String,
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

/** Mom's choices as key/value rows, so a new setting needs no schema change. */
@Entity(tableName = "setting", primaryKeys = ["name"])
data class SettingEntity(val name: String, val value: String)

@Dao
interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(rows: List<SettingEntity>)

    @Query("SELECT * FROM setting")
    suspend fun all(): List<SettingEntity>
}
