package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun getAllBlockedApps(): Flow<List<BlockedApp>>

    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    suspend fun getBlockedAppsList(): List<BlockedApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: BlockedApp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<BlockedApp>)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteApp(packageName: String)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_apps WHERE packageName = :packageName AND isBlocked = 1 LIMIT 1)")
    suspend fun isAppBlocked(packageName: String): Boolean
}
