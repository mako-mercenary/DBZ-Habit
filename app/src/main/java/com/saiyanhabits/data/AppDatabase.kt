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
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun completionDao(): CompletionDao
    abstract fun streakDao(): StreakDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "saiyan_habits.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
