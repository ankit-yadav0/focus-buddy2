package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "long_term_blocks")
data class LongTermBlock(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "APP" or "WEBSITE"
    val target: String, // package name or domain (e.g. "youtube.com")
    val targetLabel: String, // app name or website name
    val reason: String,
    val startDate: Long,
    val endDate: Long,
    val isActive: Boolean = true,
    // Daily time allowance: 0 = fully blocked all day (original behavior, unchanged for
    // every existing block). > 0 = the app/website may be used for up to this many seconds
    // per day before Focus Buddy blocks it for the rest of that day; the allowance resets
    // at the start of the next day.
    val dailyLimitSeconds: Long = 0L,
    val usedSecondsToday: Long = 0L,
    val usageDateKey: String = "" // "yyyy-MM-dd" - which day usedSecondsToday belongs to
)
