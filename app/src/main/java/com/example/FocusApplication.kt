package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.FocusRepository

class FocusApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { FocusRepository(database.blockedAppDao(), database.focusSessionDao(), database.longTermBlockDao(), database.analyticsDao(), database.websiteBlockDao(), database.appSettingDao(), database.chatMessageDao(), database.strictScheduleDao(), database.reflectionNoteDao(), database.testEntryDao(), database.dailyUsageDao()) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: FocusApplication
            private set
    }
}
