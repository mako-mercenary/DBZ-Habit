package com.saiyanhabits.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.saiyanhabits.domain.Gravity
import com.saiyanhabits.domain.HabitCategory

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: HabitCategory,
    /** 1 to 3, see GameConfig. */
    val difficulty: Int,
    /** Days-of-week bitmask, see WeekSchedule. */
    val scheduleDays: Int,
    /** At most one habit has this set. Vegeta trains that one. */
    val isHardest: Boolean = false,
    /** Reminder time as minutes after midnight, or null for no reminder. Reminders arrive in milestone 2. */
    val reminderMinutes: Int? = null,
    /**
     * Habits are never deleted from the database, only archived. Their past
     * check-offs stay, so the power level never drops.
     */
    val archived: Boolean = false,
    val createdAtEpochDay: Long,
)

/** One check-off of one habit on one day. */
@Entity(
    tableName = "completions",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["habitId", "epochDay"], unique = true),
        Index(value = ["epochDay"]),
    ],
)
data class CompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    /** The day the habit was done, as LocalDate.toEpochDay(). */
    val epochDay: Long,
    val gravity: Gravity,
    /** Points awarded, fixed at check-off time so later GameConfig changes don't rewrite history. */
    val points: Int,
    val completedAtMillis: Long,
)
