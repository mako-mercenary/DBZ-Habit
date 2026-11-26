package com.cdlstudy.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cdlstudy.domain.Review

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

@Entity(tableName = "cards", indices = [Index("subsectionId"), Index("dueAt")])
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subsectionId: String,
    val factId: String?,
    val question: String,
    val answer: String,
    val createdAt: Long,
    /**
     * Dead since 0.10.0, when the app started keeping its own schedule instead
     * of handing the cards to Anki.
     *
     * It stays because dropping a column means rebuilding the table, and a
     * table rebuild cannot be tested in this build environment: there is no
     * device to run an instrumented migration test on. A dead column is untidy.
     * Moving every card you have ever made through an untested rebuild to
     * tidy it up is worse.
     */
    val exportedAt: Long? = null,
    /** recall, exam, numbers or order: why the card exists. */
    val source: String,

    // --- the schedule. See domain/Review.kt for what these mean. ---

    /** NEW, LEARNING or REVIEW. */
    val stage: String = "NEW",
    /** Minutes from the last answer until the next one is due. */
    val intervalMinutes: Long = 0,
    /** SM-2's ease factor. */
    val ease: Double = 2.5,
    val reps: Int = 0,
    val lapses: Int = 0,
    /** Epoch millis when it is next due. 0 means now, which is what a new card wants. */
    val dueAt: Long = 0,
    val lastReviewedAt: Long? = null,
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

/** The schedule a card carries, in the form the scheduler reads. */
fun CardEntity.state(): Review.State = Review.State(
    stage = Review.Stage.of(stage),
    intervalMinutes = intervalMinutes,
    ease = ease,
    reps = reps,
    lapses = lapses,
    dueAt = dueAt,
)
