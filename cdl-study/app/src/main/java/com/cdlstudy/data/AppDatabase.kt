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
    version = 3,
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

        /**
         * Version 3 gives every card its own schedule, because the app now
         * keeps the schedule instead of exporting the deck.
         *
         * Only additions. Every card you have made keeps its question, its
         * answer and the fact it came from, and arrives due now with a fresh
         * ease, which is exactly right: the app has never asked you any of
         * them. The old exportedAt column is left alone rather than dropped,
         * because dropping it means rebuilding the table and there is no
         * device here to test a rebuild on.
         */
        private val TO_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN stage TEXT NOT NULL DEFAULT 'NEW'")
                db.execSQL("ALTER TABLE cards ADD COLUMN intervalMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cards ADD COLUMN ease REAL NOT NULL DEFAULT 2.5")
                db.execSQL("ALTER TABLE cards ADD COLUMN reps INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cards ADD COLUMN lapses INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cards ADD COLUMN dueAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cards ADD COLUMN lastReviewedAt INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cards_dueAt ON cards (dueAt)")
                // The exportedAt index goes: nothing reads it any more.
                db.execSQL("DROP INDEX IF EXISTS index_cards_exportedAt")
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
                ).addMigrations(TO_2, TO_3).build().also { instance = it }
            }
    }
}
