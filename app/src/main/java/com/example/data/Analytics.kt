package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analytics")
data class Analytics(
    @PrimaryKey val id: Int = 1,
    val blockedAppLaunches: Int = 0,
    val focusSessionsCompleted: Int = 0,
    val totalFocusTimeMinutes: Long = 0L
)
