package com.saiyanhabits.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HabitEntity::class,
        CompletionEntity::class,
        DayRecordEntity::class,
        HabitMissEntity::class,
        PlayerStateEntity::class,
        SagaEntity::class,
        MilestoneEntity::class,
        SpiritBombEntity::class,
        BombLogEntity::class,
        PowerEventEntity::class,
        FocusSessionEntity::class,
        TechniqueUseEntity::class,
        HabitExcuseEntity::class,
        WishEntity::class,
        HabitCountEntity::class,
        DayNoteEntity::class,
        MinigamePlayEntity::class,
        JournalEntryEntity::class,
        MentorCallEntity::class,
        RoutineTemplateEntity::class,
        RoutineTemplateItemEntity::class,
        DayRoutineEntity::class,
        DayRoutineItemEntity::class,
        CardOwnedEntity::class,
        CardHeldEntity::class,
        CardMatchEntity::class,
        CardPackEntity::class,
        CardPendingEntity::class,
        CardGrantEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun completionDao(): CompletionDao
    abstract fun streakDao(): StreakDao
    abstract fun questDao(): QuestDao
    abstract fun powerDao(): PowerDao
    abstract fun bondDao(): BondDao
    abstract fun wishDao(): WishDao
    abstract fun countDao(): CountDao
    abstract fun noteDao(): NoteDao
    abstract fun achievementDao(): AchievementDao
    abstract fun gameDao(): GameDao
    abstract fun journalDao(): JournalDao
    abstract fun callDao(): CallDao
    abstract fun routineDao(): RoutineDao
    abstract fun cardDao(): CardDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "saiyan_habits.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build()
    }
}
