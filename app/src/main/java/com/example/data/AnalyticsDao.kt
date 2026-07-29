package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnalyticsDao {
    @Query("SELECT * FROM analytics WHERE id = 1 LIMIT 1")
    fun getAnalytics(): kotlinx.coroutines.flow.Flow<Analytics?>

    @Query("SELECT * FROM analytics WHERE id = 1 LIMIT 1")
    suspend fun getAnalyticsSync(): Analytics?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalytics(analytics: Analytics)
}
