package com.aegida.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version 1. A schema change needs an explicit Migration, a MigrationTestHelper test against the
 * exported schema in store/schemas, and Kevin's OK first. No destructive fallback, ever.
 */
@Database(entities = [CheckRunEntity::class], version = 1, exportSchema = true)
abstract class AegidaDatabase : RoomDatabase() {
    abstract fun checkRuns(): CheckRunDao

    companion object {
        const val NAME = "aegida.db"

        fun open(context: Context): AegidaDatabase =
            Room.databaseBuilder(context, AegidaDatabase::class.java, NAME).build()
    }
}
