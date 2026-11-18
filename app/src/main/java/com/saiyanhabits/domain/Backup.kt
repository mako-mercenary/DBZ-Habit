package com.saiyanhabits.domain

/**
 * The export file: every row of every table, as JSON.
 *
 * ```
 * {"app": "saiyan-habits", "format": 1, "databaseVersion": 5,
 *  "exportedAt": "2026-11-17T09:30:00", "tables": {"habits": [{"id": 1, ...}], ...}}
 * ```
 *
 * Values are what SQLite stores: whole numbers, decimals, text or null.
 * Pure Kotlin, so it can be unit tested.
 */
data class Backup(
    val databaseVersion: Int,
    val exportedAt: String,
    /** Table name to rows; a row is column name to value. */
    val tables: Map<String, List<Map<String, Any?>>>,
) {
    fun count(table: String): Int = tables[table]?.size ?: 0
}

object BackupFormat {
    const val APP = "saiyan-habits"
    const val FORMAT = 1

    /**
     * The oldest database version an export can come from and still be
     * restored. Later versions only added tables and columns that have
     * defaults, so an older export's rows still fit.
     */
    const val OLDEST_IMPORTABLE_VERSION = 5

    fun encode(backup: Backup): String {
        val out = StringBuilder()
        out.append("{\n")
        out.append("  \"app\": ").append(quote(APP)).append(",\n")
        out.append("  \"format\": ").append(FORMAT).append(",\n")
        out.append("  \"databaseVersion\": ").append(backup.databaseVersion).append(",\n")
        out.append("  \"exportedAt\": ").append(quote(backup.exportedAt)).append(",\n")
        out.append("  \"tables\": {")
        backup.tables.entries.forEachIndexed { t, (table, rows) ->
            out.append(if (t == 0) "\n" else ",\n")
            out.append("    ").append(quote(table)).append(": [")
            rows.forEachIndexed { r, row ->
                out.append(if (r == 0) "\n" else ",\n")
                out.append("      {")
                row.entries.forEachIndexed { c, (column, value) ->
                    if (c > 0) out.append(", ")
                    out.append(quote(column)).append(": ").append(value(value))
                }
                out.append("}")
            }
            out.append(if (rows.isEmpty()) "]" else "\n    ]")
        }
        out.append(if (backup.tables.isEmpty()) "}\n" else "\n  }\n")
        out.append("}\n")
        return out.toString()
    }

    /**
     * Reads an export file. Throws IllegalArgumentException with a message
     * that can be shown to the user if it isn't one, or is damaged.
     */
    fun decode(json: String): Backup {
        val root = try {
            MiniJson.parse(json)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("This file isn't valid JSON: ${e.message}")
        } as? Map<*, *> ?: throw IllegalArgumentException("This isn't a Saiyan Habits export.")
        if (root["app"] != APP) throw IllegalArgumentException("This isn't a Saiyan Habits export.")
        val format = (root["format"] as? Long)?.toInt()
        if (format == null || format > FORMAT) {
            throw IllegalArgumentException("This export was made by a newer version of the app.")
        }
        val version = (root["databaseVersion"] as? Long)?.toInt()
            ?: throw IllegalArgumentException("The export is missing its database version.")
        val tablesJson = root["tables"] as? Map<*, *>
            ?: throw IllegalArgumentException("The export has no data in it.")
        val tables = tablesJson.entries.associate { (name, rows) ->
            val table = name as? String ?: throw IllegalArgumentException("The export has a bad table name.")
            val list = rows as? List<*> ?: throw IllegalArgumentException("\"$table\" in the export isn't a list.")
            table to list.map { row ->
                val columns = row as? Map<*, *> ?: throw IllegalArgumentException("A row of \"$table\" is damaged.")
                columns.entries.associate { (column, value) ->
                    val key = column as? String ?: throw IllegalArgumentException("A row of \"$table\" is damaged.")
                    if (value != null && value !is Long && value !is Double && value !is String) {
                        throw IllegalArgumentException("\"$table\".\"$key\" has a value the app can't store.")
                    }
                    key to value
                }
            }
        }
        return Backup(version, root["exportedAt"] as? String ?: "", tables)
    }

    /**
     * Problems that stop [backup] being restored into a database of
     * [currentVersion] whose tables have [columns]; empty if it can be.
     */
    fun problems(backup: Backup, currentVersion: Int, columns: Map<String, Set<String>>): List<String> {
        if (backup.databaseVersion > currentVersion) {
            return listOf("This export was made by a newer version of the app. Update the app first.")
        }
        if (backup.databaseVersion < OLDEST_IMPORTABLE_VERSION) {
            return listOf(
                "This export was made by an older version of the app (database version " +
                    "${backup.databaseVersion}, this one is $currentVersion), so it can't be restored here.",
            )
        }
        val problems = mutableListOf<String>()
        for ((table, rows) in backup.tables) {
            val known = columns[table]
            if (known == null) {
                problems += "The export has a table the app doesn't know: \"$table\"."
                continue
            }
            val unknown = rows.flatMap { it.keys }.toSet() - known
            if (unknown.isNotEmpty()) problems += "\"$table\" in the export has unknown columns: ${unknown.sorted()}."
        }
        return problems
    }

    private fun value(value: Any?): String = when (value) {
        null -> "null"
        is Long, is Int, is Short, is Byte -> value.toString()
        is Double -> if (value.isFinite()) value.toString() else "null"
        is Float -> if (value.isFinite()) value.toDouble().toString() else "null"
        is Boolean -> if (value) "1" else "0"
        else -> quote(value.toString())
    }

    private fun quote(text: String): String {
        val out = StringBuilder("\"")
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        return out.append('"').toString()
    }
}
