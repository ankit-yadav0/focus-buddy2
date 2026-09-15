package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WebsiteBlockDao {
    @Query("SELECT * FROM website_blocks ORDER BY endDate ASC")
    fun getAllWebsiteBlocks(): Flow<List<WebsiteBlock>>

    @Query("SELECT * FROM website_blocks WHERE isActive = 1")
    fun getActiveWebsiteBlocks(): Flow<List<WebsiteBlock>>

    @Query("SELECT * FROM website_blocks WHERE isActive = 1")
    suspend fun getActiveWebsiteBlocksList(): List<WebsiteBlock>

    @Query("SELECT * FROM website_blocks WHERE id = :id")
    suspend fun getBlockById(id: Int): WebsiteBlock?

    @Query("SELECT * FROM website_blocks")
    suspend fun getAllWebsiteBlocksList(): List<WebsiteBlock>

    @Query("UPDATE website_blocks SET isActive = 0 WHERE endDate <= :now AND isActive = 1")
    suspend fun deactivateExpiredBlocks(now: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: WebsiteBlock)

    @Query("DELETE FROM website_blocks WHERE id = :id")
    suspend fun deleteBlockById(id: Int)
}
