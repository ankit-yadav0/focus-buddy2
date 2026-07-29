package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "test_entries")
data class TestEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateMillis: Long,
    val testType: String,   // "AITS" or "Milestone"
    val testNumber: Int,
    val level: String,      // "Main" or "Advanced"
    val physicsTopics: String,   // semicolon-separated
    val chemistryTopics: String, // semicolon-separated
    val mathTopics: String       // semicolon-separated
)

@Dao
interface TestEntryDao {
    @Query("SELECT * FROM test_entries ORDER BY dateMillis ASC")
    fun getAllTests(): Flow<List<TestEntry>>

    @Query("SELECT * FROM test_entries WHERE dateMillis >= :fromMillis ORDER BY dateMillis ASC LIMIT 1")
    suspend fun getNextTest(fromMillis: Long): TestEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tests: List<TestEntry>)

    @Query("DELETE FROM test_entries WHERE id = :id")
    suspend fun deleteTest(id: Int)

    @Query("DELETE FROM test_entries")
    suspend fun deleteAll()
}
