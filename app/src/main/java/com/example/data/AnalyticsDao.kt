package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnalyticsDao {
    @Query("SELECT * FROM analytics WHERE id = 1 LIMIT 1")
    fun getAnalytics(): kotlinx.coroutines.flow.Flow<Analytics?>

    /** Creates the single analytics row if it doesn't exist yet; leaves an existing row untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(analytics: Analytics)

    @Query("UPDATE analytics SET blockedAppLaunches = blockedAppLaunches + 1 WHERE id = 1")
    suspend fun incrementBlockedLaunches()

    @Query("UPDATE analytics SET focusSessionsCompleted = focusSessionsCompleted + :completed, totalFocusTimeMinutes = totalFocusTimeMinutes + :minutes WHERE id = 1")
    suspend fun addSessionResult(completed: Int, minutes: Long)
}
