package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "strict_schedules")
data class StrictSchedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeek: String, // comma-separated 1=Monday..7=Sunday
    val isEnabled: Boolean = true
)

@Dao
interface StrictScheduleDao {
    @Query("SELECT * FROM strict_schedules ORDER BY startHour, startMinute")
    fun getAllSchedules(): Flow<List<StrictSchedule>>

    @Query("SELECT * FROM strict_schedules WHERE isEnabled = 1")
    suspend fun getEnabledSchedulesSync(): List<StrictSchedule>

    @Query("SELECT * FROM strict_schedules WHERE id = :id")
    suspend fun getScheduleById(id: Int): StrictSchedule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: StrictSchedule): Long

    @Update
    suspend fun updateSchedule(schedule: StrictSchedule)

    @Query("DELETE FROM strict_schedules WHERE id = :id")
    suspend fun deleteSchedule(id: Int)
}
