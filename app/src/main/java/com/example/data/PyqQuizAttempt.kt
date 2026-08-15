package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pyq_quiz_attempts")
data class PyqQuizAttempt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val subjectFilter: String = "ALL",     // "Physics" / "Chemistry" / "Mathematics" / "ALL"
    val difficultyFilter: String = "ALL",  // "EASY" / "MEDIUM" / "TOUGH" / "ALL"
    val requestedQuestionCount: Int = 0,
    val totalQuestions: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val skippedCount: Int = 0,
    val totalTimeSeconds: Long = 0L
)

@Entity(tableName = "pyq_quiz_answers")
data class PyqQuizAnswer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val attemptId: Int,
    val questionId: Int,
    val subject: String,
    val chapter: String,
    val difficulty: String,
    val selectedAnswer: String = "",   // blank if skipped
    val correctAnswer: String,
    val isCorrect: Boolean,
    val timeTakenSeconds: Long = 0L
)

@Dao
interface PyqQuizAttemptDao {
    @Insert
    suspend fun insertAttempt(attempt: PyqQuizAttempt): Long

    @Query("UPDATE pyq_quiz_attempts SET completedAt = :completedAt, totalQuestions = :totalQuestions, " +
        "correctCount = :correctCount, wrongCount = :wrongCount, skippedCount = :skippedCount, " +
        "totalTimeSeconds = :totalTimeSeconds WHERE id = :id")
    suspend fun completeAttempt(
        id: Int,
        completedAt: Long,
        totalQuestions: Int,
        correctCount: Int,
        wrongCount: Int,
        skippedCount: Int,
        totalTimeSeconds: Long
    )

    @Query("SELECT * FROM pyq_quiz_attempts WHERE id = :id")
    suspend fun getAttemptById(id: Int): PyqQuizAttempt?

    @Query("SELECT * FROM pyq_quiz_attempts WHERE completedAt > 0 ORDER BY completedAt DESC")
    fun getAllCompletedAttempts(): Flow<List<PyqQuizAttempt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswers(answers: List<PyqQuizAnswer>)

    @Query("SELECT * FROM pyq_quiz_answers WHERE attemptId = :attemptId")
    suspend fun getAnswersForAttempt(attemptId: Int): List<PyqQuizAnswer>

    @Query("SELECT * FROM pyq_quiz_answers")
    suspend fun getAllAnswers(): List<PyqQuizAnswer>
}
