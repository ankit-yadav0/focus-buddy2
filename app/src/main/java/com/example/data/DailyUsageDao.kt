package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE target = :target AND dateKey = :dateKey LIMIT 1")
    suspend fun getUsage(target: String, dateKey: String): DailyUsage?

    @Query("SELECT * FROM daily_usage WHERE dateKey = :dateKey")
    fun getUsageForDate(dateKey: String): Flow<List<DailyUsage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(usage: DailyUsage)

    @Query("DELETE FROM daily_usage WHERE dateKey < :cutoffDateKey")
    suspend fun deleteOlderThan(cutoffDateKey: String)
}
