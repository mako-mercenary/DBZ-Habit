package com.saiyanhabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupFormatTest {

    private val sample = Backup(
        databaseVersion = 5,
        exportedAt = "2026-11-17T09:30:00",
        tables = linkedMapOf(
            "habits" to listOf(
                linkedMapOf("id" to 1L, "name" to "Push-ups \"daily\"\nnew line", "reminderMinutes" to null, "isHardest" to 0L),
                linkedMapOf("id" to 2L, "name" to "Read · 本", "reminderMinutes" to 480L, "isHardest" to 1L),
            ),
            "completions" to listOf(
                linkedMapOf("id" to 7L, "completedAtMillis" to 1_794_863_584_190L, "points" to 20L),
            ),
            "wishes" to emptyList(),
            "focus" to listOf(linkedMapOf("ratio" to 1.5)),
        ),
    )

    @Test
    fun `an export reads back exactly as it was written`() {
        assertEquals(sample, BackupFormat.decode(BackupFormat.encode(sample)))
    }

    @Test
    fun `large whole numbers keep every digit`() {
        val big = Backup(5, "", mapOf("t" to listOf(mapOf("v" to Long.MAX_VALUE, "w" to -9_007_199_254_740_993L))))
        assertEquals(big, BackupFormat.decode(BackupFormat.encode(big)))
    }

    @Test
    fun `an empty database exports and reads back`() {
        val empty = Backup(5, "", emptyMap())
        assertEquals(empty, BackupFormat.decode(BackupFormat.encode(empty)))
    }

    @Test
    fun `other files are refused with a reason`() {
        val cases = mapOf(
            "not json at all" to "valid JSON",
            "[1, 2]" to "isn't a Saiyan Habits export",
            """{"app": "something-else", "format": 1}""" to "isn't a Saiyan Habits export",
            """{"app": "saiyan-habits", "format": 99, "databaseVersion": 5, "tables": {}}""" to "newer version",
            """{"app": "saiyan-habits", "format": 1, "tables": {}}""" to "database version",
            """{"app": "saiyan-habits", "format": 1, "databaseVersion": 5}""" to "no data",
            """{"app": "saiyan-habits", "format": 1, "databaseVersion": 5, "tables": {"t": [{"a": [1]}]}}""" to "can't store",
        )
        for ((json, reason) in cases) {
            try {
                BackupFormat.decode(json)
                fail("Expected $json to be refused")
            } catch (e: IllegalArgumentException) {
                assertTrue("\"${e.message}\" should mention \"$reason\"", e.message.orEmpty().contains(reason))
            }
        }
    }

    @Test
    fun `an export matching this database has no problems`() {
        val columns = mapOf(
            "habits" to setOf("id", "name", "reminderMinutes", "isHardest", "archived"),
            "completions" to setOf("id", "completedAtMillis", "points"),
            "wishes" to setOf("id", "text"),
            "focus" to setOf("ratio"),
        )
        assertEquals(emptyList<String>(), BackupFormat.problems(sample, currentVersion = 5, columns = columns))
    }

    @Test
    fun `exports from too old or newer database versions are refused`() {
        val tooOld = sample.copy(databaseVersion = BackupFormat.OLDEST_IMPORTABLE_VERSION - 1)
        assertTrue(BackupFormat.problems(tooOld, currentVersion = 6, columns = emptyMap()).single().contains("older version"))
        assertTrue(BackupFormat.problems(sample, currentVersion = 4, columns = emptyMap()).single().contains("newer version"))
    }

    @Test
    fun `an export from an older version that only lacks new tables and columns is accepted`() {
        val columns = mapOf(
            "habits" to setOf("id", "name", "reminderMinutes", "isHardest", "archived", "weeklyTarget", "dailyTarget", "unit"),
            "completions" to setOf("id", "completedAtMillis", "points"),
            "wishes" to setOf("id", "text"),
            "focus" to setOf("ratio"),
            "day_notes" to setOf("epochDay", "text"),
        )
        assertEquals(5, sample.databaseVersion)
        assertEquals(emptyList<String>(), BackupFormat.problems(sample, currentVersion = 6, columns = columns))
    }

    @Test
    fun `unknown tables and columns are refused`() {
        val columns = mapOf("habits" to setOf("id", "name"), "completions" to setOf("id", "completedAtMillis", "points"))
        val problems = BackupFormat.problems(sample, currentVersion = 5, columns = columns)
        assertTrue(problems.any { "reminderMinutes" in it })
        assertTrue(problems.any { "\"wishes\"" in it })
    }
}
