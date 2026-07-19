package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isBlocked: Boolean = true,
    val timestampAdded: Long = System.currentTimeMillis()
)
