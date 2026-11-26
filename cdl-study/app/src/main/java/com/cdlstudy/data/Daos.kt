package com.cdlstudy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecallDao {
    @Insert
    suspend fun insert(recall: RecallEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarks(marks: List<FactMarkEntity>)

    @Query("SELECT * FROM recalls WHERE subsectionId = :subsectionId ORDER BY gradedAt DESC")
    fun forSubsection(subsectionId: String): Flow<List<RecallEntity>>

    @Query("SELECT * FROM recalls ORDER BY gradedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<RecallEntity>>

    @Query("SELECT * FROM recalls ORDER BY gradedAt")
    suspend fun allNow(): List<RecallEntity>

    @Query("SELECT * FROM fact_marks WHERE recallId = :recallId")
    suspend fun marksOf(recallId: Long): List<FactMarkEntity>

    @Query("DELETE FROM recalls")
    suspend fun deleteAll()

    @Query("DELETE FROM fact_marks")
    suspend fun deleteAllMarks()

    /**
     * The facts you get wrong most often. A miss counts 2 and a part miss 1,
     * so a fact you keep missing comes first.
     */
    @Query(
        """
        SELECT factId, SUM(CASE mark WHEN 'MISS' THEN 2 WHEN 'PARTIAL' THEN 1 ELSE 0 END) AS weight
        FROM fact_marks
        GROUP BY factId
        HAVING weight > 0
        ORDER BY weight DESC
        LIMIT :limit
        """
    )
    fun weakFacts(limit: Int): Flow<List<WeakFact>>
}

data class WeakFact(val factId: String, val weight: Int)

@Dao
interface CardDao {
    @Insert
    suspend fun insert(cards: List<CardEntity>)

    /**
     * What is due, oldest due first, and a new card before one that is merely
     * late. A card you have never seen is worth more than one more sighting of
     * a card you already know.
     */
    @Query(
        """
        SELECT * FROM cards WHERE dueAt <= :now
        ORDER BY CASE stage WHEN 'NEW' THEN 0 ELSE 1 END, dueAt
        """
    )
    fun due(now: Long): Flow<List<CardEntity>>

    @Query(
        """
        SELECT * FROM cards WHERE dueAt <= :now
        ORDER BY CASE stage WHEN 'NEW' THEN 0 ELSE 1 END, dueAt
        """
    )
    suspend fun dueNow(now: Long): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE dueAt <= :now")
    fun dueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE stage = 'NEW'")
    fun newCount(): Flow<Int>

    /**
     * Cards you have learned and that have come round again. A card you have
     * never seen is not counted: it is due from birth, and counting it would
     * mean the app told you to review before you had read anything.
     */
    @Query("SELECT COUNT(*) FROM cards WHERE stage != 'NEW' AND dueAt <= :now")
    fun dueReviewCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE stage != 'NEW' AND dueAt <= :now")
    suspend fun dueReviewCountNow(now: Long): Int

    @Query("SELECT * FROM cards ORDER BY createdAt DESC")
    fun all(): Flow<List<CardEntity>>

    @Query(
        """
        UPDATE cards SET stage = :stage, intervalMinutes = :intervalMinutes, ease = :ease,
            reps = :reps, lapses = :lapses, dueAt = :dueAt, lastReviewedAt = :at
        WHERE id = :id
        """
    )
    suspend fun schedule(
        id: Long,
        stage: String,
        intervalMinutes: Long,
        ease: Double,
        reps: Int,
        lapses: Int,
        dueAt: Long,
        at: Long,
    )

    /** Puts one card back to never-seen, for a card you want to start over. */
    @Query(
        """
        UPDATE cards SET stage = 'NEW', intervalMinutes = 0, ease = 2.5, reps = 0,
            lapses = 0, dueAt = 0, lastReviewedAt = NULL WHERE id = :id
        """
    )
    suspend fun reset(id: Long)

    @Query("SELECT COUNT(*) FROM cards")
    fun count(): Flow<Int>

    /** Used to keep the same question out of the queue twice. */
    @Query("SELECT question FROM cards")
    suspend fun allQuestions(): List<String>

    @Query("SELECT * FROM cards ORDER BY createdAt")
    suspend fun allNow(): List<CardEntity>

    @Query("DELETE FROM cards")
    suspend fun deleteAll()
}

@Dao
interface ExamDao {
    @Insert
    suspend fun insert(attempt: ExamAttemptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswers(answers: List<ExamAnswerEntity>)

    @Query("SELECT * FROM exam_attempts WHERE track = :track ORDER BY finishedAt DESC")
    fun forTrack(track: String): Flow<List<ExamAttemptEntity>>

    @Query("SELECT * FROM exam_attempts ORDER BY finishedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ExamAttemptEntity>>

    @Query("SELECT MAX(percent) FROM exam_attempts WHERE track = :track")
    fun bestPercent(track: String): Flow<Int?>

    @Query("SELECT MAX(percent) FROM exam_attempts WHERE track = :track")
    suspend fun bestPercentNow(track: String): Int?

    /** The best score from an attempt that was not weighted in your favour. */
    @Query(
        "SELECT MAX(percent) FROM exam_attempts WHERE track = :track AND realConditions = 1"
    )
    fun bestRealPercent(track: String): Flow<Int?>

    @Query("SELECT COUNT(*) FROM exam_attempts WHERE track = :track AND realConditions = 1")
    fun realAttemptCount(track: String): Flow<Int>

    @Query(
        """
        SELECT factId, COUNT(*) AS weight
        FROM exam_answers
        WHERE wasCorrect = 0 AND factId IS NOT NULL
        GROUP BY factId
        ORDER BY weight DESC
        LIMIT :limit
        """
    )
    fun missedFacts(limit: Int): Flow<List<WeakFact>>

    @Query("SELECT factId FROM exam_answers WHERE wasCorrect = 0 AND factId IS NOT NULL")
    suspend fun allMissedFactIds(): List<String>

    @Query("SELECT * FROM exam_attempts ORDER BY finishedAt")
    suspend fun allAttemptsNow(): List<ExamAttemptEntity>

    @Query("SELECT * FROM exam_answers WHERE attemptId = :attemptId")
    suspend fun answersOf(attemptId: Long): List<ExamAnswerEntity>

    @Query("DELETE FROM exam_attempts")
    suspend fun deleteAllAttempts()

    @Query("DELETE FROM exam_answers")
    suspend fun deleteAllAnswers()
}

@Dao
interface ProgressDao {
    @Upsert
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT * FROM progress")
    fun all(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE subsectionId = :id")
    suspend fun get(id: String): ProgressEntity?

    @Query("SELECT * FROM progress")
    suspend fun allNow(): List<ProgressEntity>

    @Query("DELETE FROM progress")
    suspend fun deleteAll()
}
