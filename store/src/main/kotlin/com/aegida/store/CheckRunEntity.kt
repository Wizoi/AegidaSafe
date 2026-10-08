package com.aegida.store

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "check_run")
data class CheckRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trigger: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long?,
)

@Dao
interface CheckRunDao {
    @Insert
    suspend fun insert(run: CheckRunEntity): Long

    @Query("SELECT * FROM check_run ORDER BY startedAtMillis DESC, id DESC LIMIT 1")
    suspend fun latest(): CheckRunEntity?

    @Query("SELECT * FROM check_run WHERE trigger = :trigger ORDER BY startedAtMillis DESC, id DESC LIMIT 1")
    suspend fun latest(trigger: String): CheckRunEntity?
}
