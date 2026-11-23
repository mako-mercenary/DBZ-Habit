package com.cdlstudy.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 23)

    /** Midday on the day that is [daysAgo] before today. */
    private fun day(daysAgo: Long): Long =
        today.minusDays(daysAgo).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()

    private fun streak(vararg daysAgo: Long) = Streak.of(daysAgo.map { day(it) }, today, zone)

    @Test
    fun `no recalls means no streak`() {
        val result = Streak.of(emptyList(), today, zone)
        assertEquals(0, result.current)
        assertEquals(0, result.longest)
        assertFalse(result.studiedToday)
        assertFalse(result.atRisk)
    }

    @Test
    fun `one recall today is a streak of one`() {
        val result = streak(0)
        assertEquals(1, result.current)
        assertTrue(result.studiedToday)
        assertFalse(result.atRisk)
    }

    @Test
    fun `several recalls on one day still count as one day`() {
        assertEquals(1, streak(0, 0, 0).current)
    }

    @Test
    fun `consecutive days add up`() {
        assertEquals(4, streak(0, 1, 2, 3).current)
    }

    @Test
    fun `today is not over, so yesterday's streak still stands`() {
        val result = streak(1, 2, 3)
        assertEquals(3, result.current)
        assertFalse(result.studiedToday)
        assertTrue("the streak is at risk until you study today", result.atRisk)
    }

    @Test
    fun `a missed day breaks the streak`() {
        // Studied two days ago and before, nothing yesterday or today.
        val result = streak(2, 3, 4)
        assertEquals(0, result.current)
        assertEquals(3, result.longest)
    }

    @Test
    fun `the longest streak is remembered after the current one breaks`() {
        // A run of five, a gap, then two days ending today.
        val result = streak(0, 1, 10, 11, 12, 13, 14)
        assertEquals(2, result.current)
        assertEquals(5, result.longest)
    }

    @Test
    fun `a gap in the middle does not join two runs`() {
        assertEquals(1, streak(0, 2, 3).current)
    }

    @Test
    fun `the days are listed newest first and without repeats`() {
        val result = streak(2, 0, 1, 0)
        assertEquals(
            listOf(today, today.minusDays(1), today.minusDays(2)),
            result.days,
        )
    }

    @Test
    fun `a recall late at night counts for that day, not the next`() {
        val lateLastNight = today.minusDays(1).atStartOfDay(zone).plusHours(23).toInstant().toEpochMilli()
        val result = Streak.of(listOf(lateLastNight), today, zone)
        assertEquals(1, result.current)
        assertFalse(result.studiedToday)
    }
}
