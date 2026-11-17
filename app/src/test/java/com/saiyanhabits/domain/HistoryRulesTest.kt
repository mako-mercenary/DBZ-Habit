package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import com.saiyanhabits.domain.DayOutcome.BEAN_COVERED
import com.saiyanhabits.domain.DayOutcome.BROKEN
import com.saiyanhabits.domain.DayOutcome.MISSED
import com.saiyanhabits.domain.DayOutcome.PERFECT
import com.saiyanhabits.domain.DayOutcome.REST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HistoryRulesTest {

    // Monday 16 Nov 2026.
    private val monday = LocalDate.of(2026, 11, 16).toEpochDay()

    private fun day(offset: Long, outcome: DayOutcome, scheduled: Int = 2, completed: Int = if (outcome == PERFECT) scheduled else 0) =
        PastDay(monday + offset, if (outcome == REST) 0 else scheduled, if (outcome == REST) 0 else completed, outcome)

    // ---- Streaks over the past ----

    @Test
    fun `perfect days add, rest and bean days carry, missed days end the streak`() {
        assertEquals(
            listOf(1, 2, 2, 2, 3, 0, 1, 0, 0, 1),
            HistoryRules.streakAfterEach(listOf(PERFECT, PERFECT, REST, BEAN_COVERED, PERFECT, BROKEN, PERFECT, MISSED, REST, PERFECT)),
        )
    }

    @Test
    fun `the streak agrees with the home screen's count`() {
        val outcomes = listOf(PERFECT, BROKEN, PERFECT, REST, PERFECT, BEAN_COVERED, PERFECT)
        assertEquals(StreakRules.streak(outcomes.reversed(), todayPerfect = false), HistoryRules.streakAfterEach(outcomes).last())
    }

    @Test
    fun `best streak is the longest run, or the current one if longer`() {
        val days = listOf(day(0, PERFECT), day(1, PERFECT), day(2, PERFECT), day(3, BROKEN), day(4, PERFECT))
        assertEquals(3, HistoryRules.stats(days, currentStreak = 2).bestStreak)
        // Today done makes the current streak longer than anything on record.
        assertEquals(4, HistoryRules.stats(days, currentStreak = 4).bestStreak)
        assertEquals(0, HistoryRules.stats(emptyList(), currentStreak = 0).bestStreak)
    }

    @Test
    fun `stats count perfect days, days tracked and the share of habits done`() {
        val days = listOf(
            day(0, PERFECT, scheduled = 3),
            day(1, REST),
            day(2, BROKEN, scheduled = 4, completed = 1),
            day(3, BEAN_COVERED, scheduled = 1, completed = 0),
        )
        val stats = HistoryRules.stats(days, currentStreak = 0)
        assertEquals(1, stats.perfectDays)
        assertEquals(4, stats.daysTracked)
        // 4 of 8 scheduled habits done.
        assertEquals(50, stats.completionPercent)
    }

    @Test
    fun `completion share is null before anything was scheduled`() {
        assertNull(HistoryRules.completionPercent(emptyList()))
        assertNull(HistoryRules.completionPercent(listOf(day(0, REST))))
    }

    @Test
    fun `completion share rounds and never goes over 100`() {
        assertEquals(67, HistoryRules.completionPercent(listOf(day(0, BROKEN, scheduled = 3, completed = 2))))
        assertEquals(100, HistoryRules.completionPercent(listOf(PastDay(monday, 2, 5, PERFECT))))
    }

    // ---- The calendar ----

    @Test
    fun `month cells start on Monday and fill whole weeks`() {
        // November 2026 starts on a Sunday and has 30 days.
        val cells = HistoryRules.monthCells(2026, 11)
        assertEquals(0, cells.size % 7)
        assertEquals(List(6) { null }, cells.take(6))
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), cells[6])
        assertEquals(30, cells.filterNotNull().size)
        assertEquals(LocalDate.of(2026, 11, 30).toEpochDay(), cells.filterNotNull().last())
        // Every date sits under its own weekday.
        cells.forEachIndexed { i, day ->
            if (day != null) assertEquals(i % 7 + 1, LocalDate.ofEpochDay(day).dayOfWeek.value)
        }
    }

    @Test
    fun `a month that starts on Monday has no leading blanks`() {
        // June 2026 starts on a Monday.
        val cells = HistoryRules.monthCells(2026, 6)
        assertEquals(LocalDate.of(2026, 6, 1).toEpochDay(), cells.first())
        assertEquals(35, cells.size)
    }

    @Test
    fun `every month of several years lays out correctly`() {
        for (year in 2025..2028) for (month in 1..12) {
            val cells = HistoryRules.monthCells(year, month)
            assertEquals(0, cells.size % 7)
            assertEquals(LocalDate.of(year, month, 1).lengthOfMonth(), cells.filterNotNull().size)
            assertTrue(cells.filterNotNull().zipWithNext().all { (a, b) -> b == a + 1 })
        }
    }

    // ---- The power chart ----

    @Test
    fun `power series adds each day's points to everything before`() {
        val points = mapOf(monday - 5 to 100, monday to 20, monday + 2 to 30, monday + 9 to 999)
        val series = HistoryRules.powerSeries(points, monday, monday + 3)
        val start = GameConfig.STARTING_POWER_LEVEL + 100L
        assertEquals(listOf(start + 20, start + 20, start + 50, start + 50), series)
    }

    @Test
    fun `power series is empty for a backwards range and flat with no points`() {
        assertEquals(emptyList<Long>(), HistoryRules.powerSeries(emptyMap(), monday, monday - 1))
        assertEquals(List(3) { GameConfig.STARTING_POWER_LEVEL.toLong() }, HistoryRules.powerSeries(emptyMap(), monday, monday + 2))
    }

    // ---- The weekly recap ----

    private fun recap(
        days: List<DayOutcome?> = List(7) { PERFECT },
        powerGained: Int = 500,
        rivalPower: Int = 400,
        completed: Int = 14,
        scheduled: Int = 14,
    ) = WeekRecap(
        weekStart = monday,
        days = days,
        scheduled = scheduled,
        completed = completed,
        powerGained = powerGained,
        rivalPower = rivalPower,
        streakAtEnd = 7,
        topHabit = null,
        bondXp = mapOf(Mentor.GOKU to 70, Mentor.ROSHI to 30),
        milestonesHit = 0,
        bossesDefeated = 0,
        bombsThrown = 0,
        wishOrbs = 1,
    )

    @Test
    fun `the recap is due once for last week, and only if anything happened`() {
        val wednesday = monday + 2
        val lastWeek = monday - 7
        assertEquals(lastWeek, RecapRules.dueWeek(wednesday, lastShownWeek = null, lastWeekHadActivity = true))
        assertEquals(lastWeek, RecapRules.dueWeek(monday, lastShownWeek = lastWeek - 7, lastWeekHadActivity = true))
        assertNull(RecapRules.dueWeek(monday, lastShownWeek = lastWeek, lastWeekHadActivity = true))
        assertNull(RecapRules.dueWeek(monday + 6, lastShownWeek = lastWeek, lastWeekHadActivity = true))
        assertNull(RecapRules.dueWeek(monday, lastShownWeek = null, lastWeekHadActivity = false))
        // The next Monday brings this week's recap.
        assertEquals(monday, RecapRules.dueWeek(monday + 7, lastShownWeek = lastWeek, lastWeekHadActivity = true))
    }

    @Test
    fun `a perfect week earns the headline and an orb`() {
        val week = recap()
        assertTrue(week.perfectWeek)
        assertEquals(7, week.perfectDays)
        assertEquals("Perfect week!", RecapRules.headline(week))
    }

    @Test
    fun `a week with days before you started is not a perfect week`() {
        val week = recap(days = listOf(null, null) + List(5) { PERFECT })
        assertFalse(week.perfectWeek)
        assertEquals("Strong week", RecapRules.headline(week.copy(rivalPower = 900)))
    }

    @Test
    fun `beating the rival needs a rival and more power than it`() {
        val missed = listOf(PERFECT, BROKEN, PERFECT, PERFECT, REST, PERFECT, PERFECT)
        assertTrue(recap(days = missed, powerGained = 500, rivalPower = 400).beatRival)
        assertEquals("Rival beaten!", RecapRules.headline(recap(days = missed, powerGained = 500, rivalPower = 400)))
        assertFalse(recap(days = missed, powerGained = 400, rivalPower = 400).beatRival)
        assertFalse(recap(days = missed, powerGained = 500, rivalPower = 0).beatRival)
        assertFalse(recap(days = missed, rivalPower = 0).hasRival)
    }

    @Test
    fun `headlines for the other kinds of week`() {
        val few = listOf(PERFECT, BROKEN, BROKEN, PERFECT, REST, BROKEN, BROKEN)
        assertEquals("Week done", RecapRules.headline(recap(days = few, powerGained = 100, rivalPower = 400, completed = 6)))
        val none = List(7) { BROKEN }
        assertEquals("A quiet week", RecapRules.headline(recap(days = none, powerGained = 0, rivalPower = 400, completed = 0)))
        assertEquals(
            GameConfig.RECAP_STRONG_WEEK_PERFECT_DAYS,
            recap(days = List(GameConfig.RECAP_STRONG_WEEK_PERFECT_DAYS) { PERFECT } + List(7 - GameConfig.RECAP_STRONG_WEEK_PERFECT_DAYS) { BROKEN }).perfectDays,
        )
    }

    @Test
    fun `recap picks the mentor who gained most and rounds the completion share`() {
        val week = recap(completed = 10, scheduled = 14)
        assertEquals(Mentor.GOKU to 70, week.topMentor)
        assertEquals(71, week.completionPercent)
        assertNull(recap(completed = 0, scheduled = 0).completionPercent)
    }

    @Test
    fun `streak at a day only looks at days up to it`() {
        val days = listOf(day(0, PERFECT), day(1, PERFECT), day(2, BROKEN), day(3, PERFECT))
        assertEquals(2, RecapRules.streakAt(monday + 1, days))
        assertEquals(0, RecapRules.streakAt(monday + 2, days))
        assertEquals(1, RecapRules.streakAt(monday + 10, days))
        assertEquals(0, RecapRules.streakAt(monday - 1, days))
    }
}
