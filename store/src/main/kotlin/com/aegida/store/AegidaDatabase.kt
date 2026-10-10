package com.aegida.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 shipped with the first Play build; version 2 adds History.detail (Kevin, Oct 10). A schema change needs an explicit Migration, a MigrationTestHelper test against the exported
 * schema in store/schemas, and Kevin's OK first. No destructive fallback, ever.
 */
@Database(
    entities = [CheckRunEntity::class, FindingEntity::class, HistoryEntity::class, OutboxEntity::class, SettingEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AegidaDatabase : RoomDatabase() {
    abstract fun checkRuns(): CheckRunDao
    abstract fun findings(): FindingDao
    abstract fun history(): HistoryDao
    abstract fun outbox(): OutboxDao
    abstract fun settings(): SettingDao

    companion object {
        const val NAME = "aegida.db"

        /** History gains an empty detail column; every existing row is kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN detail TEXT")
            }
        }

        fun open(context: Context): AegidaDatabase =
            Room.databaseBuilder(context, AegidaDatabase::class.java, NAME).addMigrations(MIGRATION_1_2).build()
    }
}
