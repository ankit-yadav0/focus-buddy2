package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reflection_notes")
data class ReflectionNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val noteText: String
)

@Dao
interface ReflectionNoteDao {
    @Insert
    suspend fun insertNote(note: ReflectionNote)

    @Query("SELECT * FROM reflection_notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<ReflectionNote>>
}
