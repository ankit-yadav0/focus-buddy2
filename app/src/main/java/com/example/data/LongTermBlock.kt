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
    // Daily time-quota mode: when null, behaves exactly as before (fully blocked
    // for the whole startDate..endDate window). When set, the app is ALLOWED
    // during that window but only for this many seconds per day - once the
    // daily quota is used up, it's blocked for the rest of that day and the
    // counter resets at midnight.
    val dailyLimitSeconds: Long? = null,
    val usedSecondsToday: Long = 0,
    // Precise internal accumulator (milliseconds) the service tracks against, so
    // brief sub-second foreground segments (e.g. rapid Reels scrolling generating
    // many short window-state-change events) don't get truncated to 0 and dropped.
    // usedSecondsToday above is always derived from this (floor to whole seconds)
    // and is what the UI displays - it no longer accumulates lossy per-tick
    // truncations itself.
    val usedMillisToday: Long = 0,
    val lastUsageResetEpochDay: Long = 0
)
