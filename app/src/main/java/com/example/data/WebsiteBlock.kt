package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "website_blocks")
data class WebsiteBlock(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val domain: String,
    val reason: String,
    val startDate: Long,
    val endDate: Long,
    val isActive: Boolean = true
)
