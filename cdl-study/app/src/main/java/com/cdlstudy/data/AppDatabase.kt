package com.cdlstudy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecallEntity::class,
        FactMarkEntity::class,
        CardEntity::class,
        ExamAttemptEntity::class,
        ExamAnswerEntity::class,
        ProgressEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recalls(): RecallDao
    abstract fun cards(): CardDao
    abstract fun exams(): ExamDao
    abstract fun progress(): ProgressDao

    companion object {
        /**
         * Version 2 records whether an exam attempt ran under test-day
         * conditions, and how long it took.
         *
         * It adds columns and touches no row, so every score you have already
         * recorded survives. An attempt from version 1 reads as a practice
         * attempt with no time, which is what it was. There is no destructive
         * fallback anywhere in this file: losing your study record to a schema
         * change would be worse than any bug it could hide.
         */
        private val TO_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE exam_attempts " +
                        "ADD COLUMN realConditions INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE exam_attempts ADD COLUMN seconds INTEGER")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cdl-study.db",
                ).addMigrations(TO_2).build().also { instance = it }
            }
    }
}
