package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LongTermBlockDao {
    @Query("SELECT * FROM long_term_blocks ORDER BY endDate ASC")
    fun getAllLongTermBlocks(): Flow<List<LongTermBlock>>

    @Query("SELECT * FROM long_term_blocks WHERE isActive = 1")
    fun getActiveLongTermBlocks(): Flow<List<LongTermBlock>>

    @Query("SELECT * FROM long_term_blocks WHERE isActive = 1")
    suspend fun getActiveLongTermBlocksList(): List<LongTermBlock>

    @Query("SELECT * FROM long_term_blocks WHERE id = :id")
    suspend fun getBlockById(id: Int): LongTermBlock?

    @Query("SELECT * FROM long_term_blocks")
    suspend fun getAllLongTermBlocksList(): List<LongTermBlock>

    @Query("UPDATE long_term_blocks SET isActive = 0 WHERE endDate <= :now AND isActive = 1")
    suspend fun deactivateExpiredBlocks(now: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: LongTermBlock)

    @Query("DELETE FROM long_term_blocks WHERE id = :id")
    suspend fun deleteBlockById(id: Int)
}
