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
    entities = [
        CheckRunEntity::class, FindingEntity::class, HistoryEntity::class, OutboxEntity::class,
        SettingEntity::class, NoticeEntity::class, AllowedEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AegidaDatabase : RoomDatabase() {
    abstract fun checkRuns(): CheckRunDao
    abstract fun findings(): FindingDao
    abstract fun history(): HistoryDao
    abstract fun outbox(): OutboxDao
    abstract fun settings(): SettingDao
    abstract fun notices(): NoticeDao
    abstract fun allowed(): AllowedDao

    companion object {
        const val NAME = "aegida.db"

        /** History gains an empty detail column; every existing row is kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN detail TEXT")
            }
        }

        /** The notice counts and the list of things the owner said are fine (finding #92). Nothing existing is touched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notice` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, " +
                        "`keyHash` TEXT NOT NULL, `noticeHash` TEXT NOT NULL, `atMillis` INTEGER NOT NULL, `signals` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notice_packageName_atMillis` ON `notice` (`packageName`, `atMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notice_keyHash` ON `notice` (`keyHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notice_noticeHash` ON `notice` (`noticeHash`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `allowed` (`problem` TEXT NOT NULL, `subject` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`atMillis` INTEGER NOT NULL, PRIMARY KEY(`problem`, `subject`))",
                )
            }
        }

        fun open(context: Context): AegidaDatabase =
            Room.databaseBuilder(context, AegidaDatabase::class.java, NAME).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
