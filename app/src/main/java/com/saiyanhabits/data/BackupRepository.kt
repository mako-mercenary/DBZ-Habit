package com.saiyanhabits.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saiyanhabits.domain.Backup
import com.saiyanhabits.domain.BackupFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What reading an export file found. */
sealed interface BackupCheck {
    data class Ready(val backup: Backup) : BackupCheck
    data class Refused(val reason: String) : BackupCheck
}

/**
 * Export and import of all the app's data as one JSON file. Every table is
 * dumped as it stands, so nothing needs updating here when tables are added.
 * Pictures picked under Settings > Art are not included.
 */
class BackupRepository(private val context: Context, private val db: AppDatabase) {

    /** Tables in an order where every row's parents come before it: restored in this order, cleared in reverse. */
    private val tableOrder = listOf(
        "habits", "habit_counts", "day_notes", "completions", "day_records", "habit_misses", "habit_excuses", "player_state",
        "technique_uses", "wishes", "sagas", "saga_milestones", "spirit_bombs", "bomb_logs",
        "power_events", "focus_sessions", "minigame_plays", "journal_entries", "mentor_calls",
        "routine_templates", "routine_template_items", "day_routines", "day_routine_items",
    )

    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    /** Writes everything to [uri]. Returns the backup written, for its counts. */
    suspend fun export(uri: Uri): Backup = withContext(Dispatchers.IO) {
        val backup = db.withTransaction {
            Backup(
                databaseVersion = sql.version,
                exportedAt = LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                tables = currentTables().associateWith(::dump),
            )
        }
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Could not open the file to write.")
        out.bufferedWriter().use { it.write(BackupFormat.encode(backup)) }
        backup
    }

    /** Reads and checks the export at [uri], without changing anything. */
    suspend fun check(uri: Uri): BackupCheck = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext BackupCheck.Refused("Could not open that file.")
            val backup = BackupFormat.decode(text)
            val columns = currentTables().associateWith(::columnsOf)
            val problems = BackupFormat.problems(backup, sql.version, columns)
            if (problems.isEmpty()) BackupCheck.Ready(backup) else BackupCheck.Refused(problems.joinToString(" "))
        } catch (e: IllegalArgumentException) {
            BackupCheck.Refused(e.message ?: "That file couldn't be read.")
        } catch (e: OutOfMemoryError) {
            BackupCheck.Refused("That file is far too large to be an export.")
        }
    }

    /**
     * Replaces everything in the app with [backup], in one transaction: if any
     * row fails, nothing changes.
     */
    suspend fun restore(backup: Backup) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val tables = currentTables()
            val ordered = tableOrder.filter { it in tables } + tables.filterNot { it in tableOrder }
            for (table in ordered.reversed()) sql.execSQL("DELETE FROM `$table`")
            // AUTOINCREMENT counters restart from the imported ids.
            sql.execSQL("DELETE FROM sqlite_sequence")
            for (table in ordered) {
                for (row in backup.tables[table].orEmpty()) {
                    val values = ContentValues()
                    for ((column, value) in row) {
                        when (value) {
                            null -> values.putNull(column)
                            is Long -> values.put(column, value)
                            is Double -> values.put(column, value)
                            is String -> values.put(column, value)
                        }
                    }
                    sql.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
                }
            }
            sql.query("PRAGMA foreign_key_check").use { cursor ->
                if (cursor.moveToFirst()) throw IllegalStateException("The export's rows don't fit together.")
            }
        }
    }

    /** The app's own tables, leaving out SQLite's and Room's bookkeeping. */
    private fun currentTables(): List<String> =
        sql.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun columnsOf(table: String): Set<String> =
        sql.query("PRAGMA table_info(`$table`)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
        }

    private fun dump(table: String): List<Map<String, Any?>> =
        sql.query("SELECT * FROM `$table`").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        (0 until cursor.columnCount).associate { i ->
                            cursor.getColumnName(i) to when (cursor.getType(i)) {
                                Cursor.FIELD_TYPE_NULL -> null
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                                else -> cursor.getString(i)
                            }
                        },
                    )
                }
            }
        }
}
