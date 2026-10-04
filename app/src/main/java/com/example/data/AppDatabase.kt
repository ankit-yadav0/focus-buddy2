package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BlockedApp::class, FocusSession::class, LongTermBlock::class, Analytics::class, WebsiteBlock::class, AppSetting::class, StrictSchedule::class, ReflectionNote::class, LockedApp::class], version = 17, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun longTermBlockDao(): LongTermBlockDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun websiteBlockDao(): WebsiteBlockDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun strictScheduleDao(): StrictScheduleDao
    abstract fun reflectionNoteDao(): ReflectionNoteDao
    abstract fun lockedAppDao(): LockedAppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Adds the locked_apps table for App Lock (PIN-gated apps). Written as a real
        // Migration rather than fallbackToDestructiveMigration() - a destructive fallback
        // silently drops and recreates EVERY table on any schema/version bump, which is
        // exactly what caused the real data-loss incident on this project before. Any
        // future schema change must add its own Migration here, not lean on the fallback.
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `locked_apps` (" +
                        "`packageName` TEXT NOT NULL, " +
                        "`appName` TEXT NOT NULL, " +
                        "`timestampAdded` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`packageName`))"
                )
            }
        }

        // Drops the tables of features that were removed from the app (JEE test calendar,
        // PYQ practice, study chat). A real Migration - never the destructive fallback, which
        // would wipe every table (blocks, sessions, settings) on a version bump.
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `chat_messages`")
                db.execSQL("DROP TABLE IF EXISTS `test_entries`")
                db.execSQL("DROP TABLE IF EXISTS `pyq_questions`")
                db.execSQL("DROP TABLE IF EXISTS `pyq_quiz_attempts`")
                db.execSQL("DROP TABLE IF EXISTS `pyq_quiz_answers`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_buddy_database"
                )
                .addMigrations(MIGRATION_15_16, MIGRATION_16_17)
                // Only builds older than v15 (no migration path exists for them) - never v15+.
                .fallbackToDestructiveMigrationFrom(*IntArray(14) { it + 1 })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
