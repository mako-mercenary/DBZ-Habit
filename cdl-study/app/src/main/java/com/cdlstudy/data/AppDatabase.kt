package com.cdlstudy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecallEntity::class,
        FactMarkEntity::class,
        CardEntity::class,
        ExamAttemptEntity::class,
        ExamAnswerEntity::class,
        ProgressEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recalls(): RecallDao
    abstract fun cards(): CardDao
    abstract fun exams(): ExamDao
    abstract fun progress(): ProgressDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cdl-study.db",
                ).build().also { instance = it }
            }
    }
}
