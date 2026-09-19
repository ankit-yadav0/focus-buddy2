package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pyq_questions")
data class PyqQuestion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subject: String,          // "Physics", "Chemistry", "Mathematics"
    val chapter: String = "",     // best-effort topic tag, may be blank if unclear
    val year: Int,
    val examStage: String = "JEE Main", // "JEE Main" or "JEE Advanced"
    val session: String = "",     // e.g. "Jan Shift 1", blank if not applicable
    val questionText: String,
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val isNumerical: Boolean = false,   // true for integer/numeric-answer type (no options)
    val correctAnswer: String = "",     // "A"/"B"/"C"/"D", or the numeric value as text
    val difficulty: String = "MEDIUM"   // "EASY", "MEDIUM", "TOUGH" - estimated, not official
)

@Dao
interface PyqQuestionDao {
    @Query("SELECT * FROM pyq_questions ORDER BY year DESC, id ASC")
    fun getAllQuestions(): Flow<List<PyqQuestion>>

    @Query(
        "SELECT * FROM pyq_questions WHERE " +
        "(:subject IS NULL OR subject = :subject) AND " +
        "(:difficulty IS NULL OR difficulty = :difficulty) " +
        "ORDER BY RANDOM() LIMIT :limit"
    )
    suspend fun getRandomQuestions(subject: String?, difficulty: String?, limit: Int): List<PyqQuestion>

    @Query(
        "SELECT COUNT(*) FROM pyq_questions WHERE " +
        "(:subject IS NULL OR subject = :subject) AND " +
        "(:difficulty IS NULL OR difficulty = :difficulty)"
    )
    suspend fun getMatchingCount(subject: String?, difficulty: String?): Int

    @Query("SELECT DISTINCT year FROM pyq_questions ORDER BY year DESC")
    suspend fun getAvailableYears(): List<Int>

    @Query("SELECT COUNT(*) FROM pyq_questions")
    suspend fun getQuestionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<PyqQuestion>)

    @Query("DELETE FROM pyq_questions WHERE year = :year")
    suspend fun deleteByYear(year: Int)

    @Query("DELETE FROM pyq_questions")
    suspend fun deleteAll()
}
