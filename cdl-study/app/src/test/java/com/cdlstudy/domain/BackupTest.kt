package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        // Written against the current format, not a hardcoded 1, so bumping
        // the format does not quietly turn this test into a no-op.
        val future = Backup.write(record)
            .replace("\"formatVersion\": ${Backup.FORMAT}", "\"formatVersion\": ${Backup.FORMAT + 1}")
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

/**
 * Format 2 added two fields to an exam attempt. A backup written before that
 * must still read, and the fields it does not have must come back as what they
 * were: a practice attempt with no recorded time.
 */
class BackupFormat2Test {

    private val attempt = Backup.Attempt(
        track = "general",
        finishedAt = 1_700_000_000_000,
        total = 50,
        correct = 44,
        percent = 88,
        answers = listOf(Backup.Answer("q1", 0, true, "1.0.1")),
        realConditions = true,
        seconds = 1_432,
    )

    @Test
    fun `the new fields survive a round trip`() {
        val record = Backup.Record(
            formatVersion = Backup.FORMAT,
            appVersion = "0.9.0",
            savedAt = 1_700_000_000_000,
            attempts = listOf(attempt),
        )
        val back = Backup.read(Backup.write(record))
        assertEquals(1, back.attempts.size)
        assertEquals(true, back.attempts[0].realConditions)
        assertEquals(1_432, back.attempts[0].seconds)
        assertEquals(88, back.attempts[0].percent)
    }

    @Test
    fun `a practice attempt round trips as a practice attempt`() {
        val record = Backup.Record(
            formatVersion = Backup.FORMAT,
            appVersion = "0.9.0",
            savedAt = 1,
            attempts = listOf(attempt.copy(realConditions = false, seconds = null)),
        )
        val back = Backup.read(Backup.write(record))
        assertEquals(false, back.attempts[0].realConditions)
        assertNull(back.attempts[0].seconds)
    }

    @Test
    fun `a format 1 file still reads, and its attempts are practice attempts`() {
        val old = """
            {
              "formatVersion": 1,
              "appVersion": "0.7.0",
              "savedAt": 1700000000000,
              "recalls": [],
              "cards": [],
              "attempts": [
                {
                  "track": "general",
                  "finishedAt": 1700000000000,
                  "total": 50,
                  "correct": 44,
                  "percent": 88,
                  "answers": [
                    { "itemId": "q1", "chosen": 0, "wasCorrect": true, "factId": "1.0.1" }
                  ]
                }
              ],
              "progress": []
            }
        """.trimIndent()
        val back = Backup.read(old)
        assertEquals(1, back.attempts.size)
        assertEquals(88, back.attempts[0].percent)
        assertEquals(false, back.attempts[0].realConditions)
        assertNull(back.attempts[0].seconds)
    }

    @Test
    fun `a file from a newer format is still refused rather than half read`() {
        val future = """
            { "formatVersion": ${Backup.FORMAT + 1}, "appVersion": "9.9.9", "savedAt": 1,
              "recalls": [], "cards": [], "attempts": [], "progress": [] }
        """.trimIndent()
        assertTrue(runCatching { Backup.read(future) }.exceptionOrNull() is Backup.BadBackup)
    }
}

/**
 * Format 3 gave every card a schedule. A restore that dropped it would reset
 * every interval you had earned, which is the one thing a backup must not do.
 */
class BackupFormat3Test {

    private val card = Backup.Card(
        subsectionId = "5.1.2",
        factId = "5.1.2.1",
        question = "At what pressure does the governor cut out?",
        answer = "About 125 psi.",
        createdAt = 1_700_000_000_000,
        exportedAt = null,
        source = "recall",
        stage = "REVIEW",
        intervalMinutes = 8_640,
        ease = 2.35,
        reps = 4,
        lapses = 1,
        dueAt = 1_700_600_000_000,
        lastReviewedAt = 1_700_100_000_000,
    )

    @Test
    fun `the schedule survives a round trip`() {
        val record = Backup.Record(
            formatVersion = Backup.FORMAT,
            appVersion = "0.10.0",
            savedAt = 1,
            cards = listOf(card),
        )
        val back = Backup.read(Backup.write(record)).cards.single()
        assertEquals("REVIEW", back.stage)
        assertEquals(8_640L, back.intervalMinutes)
        assertEquals(2.35, back.ease, 0.0001)
        assertEquals(4, back.reps)
        assertEquals(1, back.lapses)
        assertEquals(1_700_600_000_000, back.dueAt)
        assertEquals(1_700_100_000_000L, back.lastReviewedAt)
    }

    @Test
    fun `a card from an older file comes back never seen and due now`() {
        val old = """
            {
              "formatVersion": 2, "appVersion": "0.9.0", "savedAt": 1,
              "recalls": [],
              "cards": [
                { "subsectionId": "5.1.2", "factId": "5.1.2.1",
                  "question": "Cut-out?", "answer": "125 psi.",
                  "createdAt": 1, "exportedAt": null, "source": "recall" }
              ],
              "attempts": [], "progress": []
            }
        """.trimIndent()
        val back = Backup.read(old).cards.single()
        assertEquals("NEW", back.stage)
        assertEquals(0L, back.intervalMinutes)
        assertEquals(2.5, back.ease, 0.0001)
        assertEquals(0L, back.dueAt)
        assertNull(back.lastReviewedAt)
    }
}
