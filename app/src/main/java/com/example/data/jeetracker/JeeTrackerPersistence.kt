package com.example.data.jeetracker

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One row per day of the 120-day plan that the user has interacted with at all
 * (rows are created lazily on first checkbox tap, not pre-seeded for all 120 days,
 * to keep the table small on a 2GB device).
 */
@Entity(tableName = "jee_day_progress")
data class JeeDayProgressEntity(
    @PrimaryKey val dayNumber: Int,
    val completedTaskIds: String = "", // comma-separated task ids
    val isDayCompleted: Boolean = false,
    val notes: String = "",
    val studyMinutesLogged: Int = 0,
    val isBookmarked: Boolean = false
)

@Entity(tableName = "jee_custom_tasks")
data class JeeCustomTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayNumber: Int,
    val text: String,
    val completed: Boolean = false
)

@Entity(tableName = "jee_mock_records")
data class JeeMockRecordEntity(
    @PrimaryKey val dayNumber: Int,
    val testName: String,
    val dateMillis: Long,
    val physicsScore: Int,
    val chemistryScore: Int,
    val mathsScore: Int,
    val accuracyPercentage: Int,
    val mistakesCount: Int,
    val analysisNotes: String,
    val weakTopics: String = "" // comma-separated
)

@Dao
interface JeeTrackerDao {
    @Query("SELECT * FROM jee_day_progress")
    fun getAllProgress(): Flow<List<JeeDayProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: JeeDayProgressEntity)

    @Query("SELECT * FROM jee_custom_tasks")
    fun getAllCustomTasks(): Flow<List<JeeCustomTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomTask(task: JeeCustomTaskEntity): Long

    @Query("DELETE FROM jee_custom_tasks WHERE id = :id")
    suspend fun deleteCustomTask(id: Long)

    @Query("SELECT * FROM jee_mock_records")
    fun getAllMockRecords(): Flow<List<JeeMockRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMockRecord(record: JeeMockRecordEntity)

    @Query("DELETE FROM jee_mock_records WHERE dayNumber = :dayNumber")
    suspend fun deleteMockRecord(dayNumber: Int)

    @Query("DELETE FROM jee_day_progress")
    suspend fun resetProgress()

    @Query("DELETE FROM jee_custom_tasks")
    suspend fun resetCustomTasks()

    @Query("DELETE FROM jee_mock_records")
    suspend fun resetMockRecords()
}
