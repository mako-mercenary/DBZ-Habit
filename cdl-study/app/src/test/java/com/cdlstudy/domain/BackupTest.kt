package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val record = Backup.Record(
        formatVersion = Backup.FORMAT,
        appVersion = "0.7.0",
        savedAt = 1_700_000_000_000,
        recalls = listOf(
            Backup.Recall(
                subsectionId = "5.1.2",
                gradedAt = 1_700_000_000_001,
                blurt = "Cut-out is 125 psi.\nCut-in is 100.\tI think.",
                overall = 75,
                critical = 80,
                factCount = 4,
                criticalCount = 2,
                marks = mapOf("5.1.2.1" to "HIT", "5.1.2.2" to "PARTIAL"),
            )
        ),
        cards = listOf(
            Backup.Card("5.1.2", "5.1.2.1", "Cut-out?", "About 125 psi.", 5, null, "recall"),
            Backup.Card("exam-5", null, "Quoted \"question\"", "A \\ backslash", 6, 7, "exam"),
        ),
        attempts = listOf(
            Backup.Attempt(
                track = "air_brakes",
                finishedAt = 1_700_000_000_002,
                total = 25,
                correct = 22,
                percent = 88,
                answers = listOf(Backup.Answer("5.1.2.e1", 2, true, "5.1.2.1")),
            )
        ),
        progress = listOf(
            Backup.Progress("5.1.2", "mastered", 95, 100, 3, 1_700_000_000_003)
        ),
    )

    @Test
    fun `a record survives being written and read back`() {
        val again = Backup.read(Backup.write(record))
        assertEquals(record, again)
    }

    @Test
    fun `an empty record survives the round trip`() {
        val empty = Backup.Record(savedAt = 1)
        assertEquals(empty, Backup.read(Backup.write(empty)))
    }

    @Test
    fun `quotes, backslashes, newlines and tabs in a recall come back unharmed`() {
        val again = Backup.read(Backup.write(record))
        assertEquals("Cut-out is 125 psi.\nCut-in is 100.\tI think.", again.recalls[0].blurt)
        assertEquals("Quoted \"question\"", again.cards[1].question)
        assertEquals("A \\ backslash", again.cards[1].answer)
    }

    @Test
    fun `the marks on every fact are kept`() {
        val again = Backup.read(Backup.write(record))
        assertEquals(mapOf("5.1.2.1" to "HIT", "5.1.2.2" to "PARTIAL"), again.recalls[0].marks)
    }

    @Test
    fun `a null stays null and is not turned into a string`() {
        val again = Backup.read(Backup.write(record))
        assertEquals(null, again.cards[0].exportedAt)
        assertEquals(null, again.cards[1].factId)
        assertEquals(7L, again.cards[1].exportedAt)
    }

    @Test
    fun `a file that is not a record is refused with a readable reason`() {
        listOf("", "not json at all", "{}", "[1,2,3]").forEach { text ->
            val error = runCatching { Backup.read(text) }.exceptionOrNull()
            assertTrue("'$text' should be refused", error is Backup.BadBackup)
        }
    }

    @Test
    fun `a record from a newer app is refused, instead of being half read`() {
        val future = Backup.write(record).replace("\"formatVersion\": 1", "\"formatVersion\": 99")
        val error = runCatching { Backup.read(future) }.exceptionOrNull()
        assertTrue(error is Backup.BadBackup)
        assertTrue(error!!.message!!.contains("newer version"))
    }

    @Test
    fun `the counts line says what is in the record`() {
        assertEquals("1 recalls, 2 cards, 1 exams, 1 parts", record.counts)
    }

    @Test
    fun `the file name carries the date`() {
        assertEquals("gateway-record-2026-11-22.json", Backup.fileName("2026-11-22"))
    }
}
