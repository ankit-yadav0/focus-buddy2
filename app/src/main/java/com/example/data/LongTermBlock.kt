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
    val isActive: Boolean = true
)
