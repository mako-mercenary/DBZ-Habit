package com.saiyanhabits.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.saiyanhabits.domain.DayOutcome
import com.saiyanhabits.domain.Gravity
import com.saiyanhabits.domain.HabitCategory
import com.saiyanhabits.domain.Mentor

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
    /** Reminder time as minutes after midnight, or null for no reminder. */
    val reminderMinutes: Int? = null,
    /**
     * Habits are never deleted from the database, only archived. Their past
     * check-offs stay, so the power level never drops.
     */
    val archived: Boolean = false,
    val createdAtEpochDay: Long,
    /**
     * For a habit done a number of times a week on any days: that number.
     * Null for a habit done on the days in [scheduleDays].
     */
    @ColumnInfo(defaultValue = "NULL") val weeklyTarget: Int? = null,
    /** How many of it make a day's check-off: 1 for an ordinary habit, 8 for "8 glasses of water". */
    @ColumnInfo(defaultValue = "1") val dailyTarget: Int = 1,
    /** What [dailyTarget] counts, e.g. "glasses". Blank for an ordinary habit. */
    @ColumnInfo(defaultValue = "") val unit: String = "",
) {
    val isWeekly: Boolean get() = weeklyTarget != null
    val isCounted: Boolean get() = dailyTarget > 1
}

/** How far a counted habit has got on one day. The check-off itself is made when the count reaches the target. */
@Entity(
    tableName = "habit_counts",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["epochDay"])],
)
data class HabitCountEntity(
    val habitId: Long,
    val epochDay: Long,
    val count: Int,
)

/** One finished mini game. */
@Entity(tableName = "minigame_plays", indices = [Index(value = ["game"]), Index(value = ["epochDay"])])
data class MinigamePlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val game: com.saiyanhabits.domain.MiniGame,
    /** The mentor whose game it is, who got the bond XP. */
    val mentor: Mentor,
    val epochDay: Long,
    /** The game's score: metres, minutes, grams, a win (1) or loss (0), points. */
    val score: Long,
    /** Anything else worth keeping, e.g. the fish caught or the song played. */
    val detail: String,
    val bondXp: Int,
    val createdAtMillis: Long,
)

/** A saved routine you can reuse: a name and its checklist. */
@Entity(tableName = "routine_templates")
data class RoutineTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMillis: Long,
)

/** One step of a saved routine. */
@Entity(
    tableName = "routine_template_items",
    foreignKeys = [ForeignKey(entity = RoutineTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["templateId"])],
)
data class RoutineTemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val position: Int,
    val text: String,
)

/** A checklist for one day, planned from a template or from scratch. */
@Entity(tableName = "day_routines", indices = [Index(value = ["epochDay"])])
data class DayRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val name: String,
    /** The template it was made from, if any; kept only as a note, editing one never changes the other. */
    val templateId: Long?,
    val position: Int,
    val createdAtMillis: Long,
)

/** One thing to do in a day's routine. */
@Entity(
    tableName = "day_routine_items",
    foreignKeys = [ForeignKey(entity = DayRoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["routineId"])],
)
data class DayRoutineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val position: Int,
    val text: String,
    /** When it was checked off, or null while it's still to do. */
    val doneAtMillis: Long?,
)

/** One journal entry, written to one of Bulma's prompts. */
@Entity(tableName = "journal_entries", indices = [Index(value = ["epochDay"])])
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** The prompt's id in assets/dialogue/journal.json, or 0 for a free entry. */
    val promptId: Int,
    /** The prompt's words as they were when written, so old entries keep them. */
    val prompt: String,
    val text: String,
    val words: Int,
    /** Bond XP this entry gave Bulma: only the day's first finished entry gives any. */
    val bondXp: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** One answered call from a mentor. */
@Entity(tableName = "mentor_calls", indices = [Index(value = ["epochDay"]), Index(value = ["mentor"])])
data class MentorCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mentor: Mentor,
    val epochDay: Long,
    /** The call's id in assets/dialogue/calls_<mentor>.json. */
    val callId: String,
    /** The replies picked, as their indexes joined by commas, so the call can be re-read as it went. */
    val choices: String,
    val bondXp: Int,
    /** True when it was picked up within the hour after the phone rang. */
    val pickedUpQuickly: Boolean,
    val answeredAtMillis: Long,
)

/** A line written about a day. */
@Entity(tableName = "day_notes")
data class DayNoteEntity(
    @PrimaryKey val epochDay: Long,
    val text: String,
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
    /** The mentor who trained the habit at the time, and so earned the bond XP. Null on check-offs with none. */
    val mentor: Mentor? = null,
    /** Bond XP this check-off gave [mentor]. A mentor's bond XP is the sum of these. */
    @ColumnInfo(defaultValue = "0") val bondXp: Int = 0,
)

/**
 * How one finished day went. Written once the day is over, from the habits and
 * check-offs as they stood then, so editing a habit later never rewrites history.
 */
@Entity(tableName = "day_records")
data class DayRecordEntity(
    @PrimaryKey val epochDay: Long,
    val scheduledCount: Int,
    val completedCount: Int,
    val outcome: DayOutcome,
)

/**
 * A habit that was scheduled on a finished day and not done. A habit's own
 * streak is its check-offs since its latest row here.
 */
@Entity(
    tableName = "habit_misses",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["epochDay"])],
)
data class HabitMissEntity(
    val habitId: Long,
    val epochDay: Long,
)

/** Player-wide progress. There is only ever one row, with id 0. */
@Entity(tableName = "player_state")
data class PlayerStateEntity(
    @PrimaryKey val id: Int = 0,
    val healingBeans: Int = 0,
    /** Highest form unlocked so far (0 = base form). Never goes down. */
    val highestForm: Int = 0,
    /** The day a streak last earned a bean, so one day cannot earn two. */
    val lastBeanAwardEpochDay: Long? = null,
    /** Story chapters that have been read, one bit per chapter. See StoryRules. */
    @ColumnInfo(defaultValue = "0") val chaptersRead: Int = 0,
    /** Wish orbs held, 0 to GameConfig.WISH_ORBS_NEEDED. */
    @ColumnInfo(defaultValue = "0") val wishOrbs: Int = 0,
    /** Monday of the last week judged for a wish orb, so no week is judged twice. */
    val lastOrbWeekEpochDay: Long? = null,
)

/** A real-life reward typed in to be claimed from the dragon. */
@Entity(tableName = "wishes")
data class WishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAtEpochDay: Long,
    /** Null until the wish has been granted. */
    val grantedAtEpochDay: Long? = null,
)

/** Query result: power earned on one day. */
data class DayPoints(val epochDay: Long, val points: Int)

/** Query result: how many power events came from one source. */
data class SourceCount(val source: com.saiyanhabits.domain.PowerSource, val count: Int)

/** Query result: a habit's current streak. */
data class HabitStreak(val habitId: Long, val streak: Int)
