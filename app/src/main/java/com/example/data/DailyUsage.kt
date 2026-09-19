package com.example.data

import androidx.room.Entity

/**
 * Tracks how many minutes a given app/website (identified by [target], the
 * package name or domain) has been used on a given day ([dateKey], formatted
 * "yyyy-MM-dd" in the device's local timezone). Used to enforce the optional
 * daily time limit on a [LongTermBlock]. Rows naturally "reset" each day
 * since a new day gets a new dateKey.
 */
@Entity(tableName = "daily_usage", primaryKeys = ["target", "dateKey"])
data class DailyUsage(
    val target: String,
    val dateKey: String,
    val minutesUsed: Int = 0
)
