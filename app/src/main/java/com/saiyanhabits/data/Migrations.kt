package com.saiyanhabits.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database upgrades. Each one must leave the tables exactly as Room expects
 * them for the new version: the statements are copied from the exported schema
 * in app/schemas.
 */

/** Milestone 2: finished-day records, per-habit misses and player state. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `day_records` (`epochDay` INTEGER NOT NULL, " +
                "`scheduledCount` INTEGER NOT NULL, `completedCount` INTEGER NOT NULL, " +
                "`outcome` TEXT NOT NULL, PRIMARY KEY(`epochDay`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habit_misses` (`habitId` INTEGER NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, PRIMARY KEY(`habitId`, `epochDay`), " +
                "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_misses_epochDay` ON `habit_misses` (`epochDay`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `player_state` (`id` INTEGER NOT NULL, " +
                "`healingBeans` INTEGER NOT NULL, `highestForm` INTEGER NOT NULL, " +
                "`lastBeanAwardEpochDay` INTEGER, PRIMARY KEY(`id`))",
        )
    }
}
