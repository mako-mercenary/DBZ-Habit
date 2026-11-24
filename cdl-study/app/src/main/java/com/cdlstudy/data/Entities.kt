package com.cdlstudy.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The study record. The content comes from the asset files, so only your own
 * work is in the database: your recalls, your marks, your cards and your scores.
 */

@Entity(tableName = "recalls")
data class RecallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subsectionId: String,
    val gradedAt: Long,
    /** Your closed-book text, kept so you can read your own words again. */
    val blurt: String,
    val overall: Int,
    val critical: Int,
    val factCount: Int,
    val criticalCount: Int,
)

@Entity(
    tableName = "fact_marks",
    primaryKeys = ["recallId", "factId"],
    indices = [Index("factId")],
)
data class FactMarkEntity(
    val recallId: Long,
    val factId: String,
    /** HIT, PARTIAL or MISS. */
    val mark: String,
)

@Entity(tableName = "cards", indices = [Index("subsectionId"), Index("exportedAt")])
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subsectionId: String,
    val factId: String?,
    val question: String,
    val answer: String,
    val createdAt: Long,
    /** Null until the card is in an export file. */
    val exportedAt: Long? = null,
    /** recall or exam: why the card exists. */
    val source: String,
)

@Entity(tableName = "exam_attempts")
data class ExamAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val track: String,
    val finishedAt: Long,
    val total: Int,
    val correct: Int,
    val percent: Int,
    /**
     * True for an attempt run under test-day conditions: full length, and no
     * weighting toward the facts you have missed before.
     *
     * A practice attempt is weighted toward your weak facts, which is right for
     * learning and wrong for measuring. Without this column a 92% from a
     * weighted practice run and a 92% from a straight one look the same, and
     * the number stops meaning anything.
     */
    val realConditions: Boolean = false,
    /** How long the attempt took, in seconds. Null for an older attempt. */
    val seconds: Int? = null,
)

@Entity(
    tableName = "exam_answers",
    primaryKeys = ["attemptId", "itemId"],
    indices = [Index("factId")],
)
data class ExamAnswerEntity(
    val attemptId: Long,
    val itemId: String,
    val chosen: Int,
    val wasCorrect: Boolean,
    val factId: String?,
)

/** One row per subsection, so the app can show where you left off. */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val subsectionId: String,
    /** new, reading, recalled or mastered. */
    val status: String,
    val bestOverall: Int = 0,
    val bestCritical: Int = 0,
    val timesRecalled: Int = 0,
    val lastStudiedAt: Long? = null,
)
