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

/** Milestone 3: sagas, spirit bombs, the power ledger and time chamber sessions. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sagas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `boss` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAtEpochDay` " +
                "INTEGER NOT NULL, `finishedAtEpochDay` INTEGER, `victoryBonus` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saga_milestones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT " +
                "NULL, `sagaId` INTEGER NOT NULL, `name` TEXT NOT NULL, `weight` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, `focusSessions` INTEGER NOT NULL, `completedAtEpochDay` " +
                "INTEGER, `damageDealt` INTEGER NOT NULL, `points` INTEGER NOT NULL, FOREIGN " +
                "KEY(`sagaId`) REFERENCES `sagas`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_saga_milestones_sagaId` ON `saga_milestones` " +
                "(`sagaId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `spirit_bombs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT " +
                "NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `target` INTEGER NOT NULL, `progress` " +
                "INTEGER NOT NULL, `size` INTEGER NOT NULL, `status` TEXT NOT NULL, `createdAtEpochDay` " +
                "INTEGER NOT NULL, `finishedAtEpochDay` INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bomb_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`bombId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, " +
                "`createdAtMillis` INTEGER NOT NULL, `viaTimeChamber` INTEGER NOT NULL, FOREIGN " +
                "KEY(`bombId`) REFERENCES `spirit_bombs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bomb_logs_bombId` ON `bomb_logs` (`bombId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `power_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT " +
                "NULL, `epochDay` INTEGER NOT NULL, `points` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                "`sourceId` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_power_events_source_sourceId` ON `power_events` " +
                "(`source`, `sourceId`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_power_events_epochDay` ON `power_events` (`epochDay`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT " +
                "NULL, `epochDay` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, `targetType` TEXT NOT " +
                "NULL, `targetId` INTEGER NOT NULL, `finishedAtMillis` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_focus_sessions_epochDay` ON `focus_sessions` " +
                "(`epochDay`)",
        )
    }
}

/**
 * Milestone 4: bond XP on check-offs, technique uses and excused habits.
 * Check-offs made before bonds existed are credited to the mentor who trains
 * the habit now, so earlier training counts towards the bonds.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `completions` ADD COLUMN `mentor` TEXT")
        db.execSQL("ALTER TABLE `completions` ADD COLUMN `bondXp` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            UPDATE completions SET
                bondXp = $BACKFILL_BOND_XP,
                mentor = (
                    SELECT CASE
                        WHEN h.isHardest = 1 THEN 'VEGETA'
                        WHEN h.category = 'DAILY_BASICS' THEN 'ROSHI'
                        WHEN h.category = 'WORKOUT' THEN 'GOKU'
                        WHEN h.category = 'FOCUS' THEN 'PICCOLO'
                        WHEN h.category = 'PROJECTS' THEN 'BULMA'
                    END
                    FROM habits h WHERE h.id = completions.habitId
                )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `technique_uses` (`technique` TEXT NOT NULL, " +
                "`lastUsedEpochDay` INTEGER NOT NULL, PRIMARY KEY(`technique`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habit_excuses` (`habitId` INTEGER NOT NULL, `epochDay` " +
                "INTEGER NOT NULL, PRIMARY KEY(`habitId`, `epochDay`), FOREIGN KEY(`habitId`) REFERENCES " +
                "`habits`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_habit_excuses_epochDay` ON `habit_excuses` " +
                "(`epochDay`)",
        )
    }
}

/** Bond XP credited per old check-off. Fixed here so the migration never changes after release. */
private const val BACKFILL_BOND_XP = 10
