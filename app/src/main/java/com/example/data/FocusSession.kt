package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val durationMinutes: Int,
    val endTime: Long,
    val isActive: Boolean,
    val isStrict: Boolean = false,
    val actualEndTime: Long = 0L,
    val plannedDurationMinutes: Int = durationMinutes,
    val actualDurationSeconds: Long = 0L,
    val sessionStatus: String = "Completed", // "Completed", "Ended Early", "Expired"
    val plantStatus: String = "SEED", // "SEED", "GROWING", "MATURED", "WITHERED"
    val assetPath: String = "img_plant_seed",
    val origin: String = "MANUAL"
)
