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
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun completionDao(): CompletionDao
    abstract fun streakDao(): StreakDao
    abstract fun questDao(): QuestDao
    abstract fun powerDao(): PowerDao
    abstract fun bondDao(): BondDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "saiyan_habits.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
