package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BlockedApp::class, FocusSession::class, LongTermBlock::class, Analytics::class, WebsiteBlock::class, AppSetting::class, ChatMessage::class, StrictSchedule::class, ReflectionNote::class, TestEntry::class, PyqQuestion::class, PyqQuizAttempt::class, PyqQuizAnswer::class], version = 15, exportSchema = false)
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_buddy_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
