package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BlockedApp::class, FocusSession::class, LongTermBlock::class, Analytics::class, WebsiteBlock::class, AppSetting::class, ChatMessage::class, StrictSchedule::class, ReflectionNote::class, TestEntry::class, PyqQuestion::class, PyqQuizAttempt::class, PyqQuizAnswer::class, LockedApp::class], version = 16, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun longTermBlockDao(): LongTermBlockDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun websiteBlockDao(): WebsiteBlockDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun strictScheduleDao(): StrictScheduleDao
    abstract fun reflectionNoteDao(): ReflectionNoteDao
    abstract fun testEntryDao(): TestEntryDao
    abstract fun pyqQuestionDao(): PyqQuestionDao
    abstract fun pyqQuizAttemptDao(): PyqQuizAttemptDao
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_buddy_database"
                )
                .addMigrations(MIGRATION_15_16)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
