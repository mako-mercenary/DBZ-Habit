package com.saiyanhabits.domain

import com.saiyanhabits.domain.DayOutcome.BEAN_COVERED
import com.saiyanhabits.domain.DayOutcome.BROKEN
import com.saiyanhabits.domain.DayOutcome.MISSED
import com.saiyanhabits.domain.DayOutcome.PERFECT
import com.saiyanhabits.domain.DayOutcome.REST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionRulesTest {

    private fun context(
        past: List<DayOutcome> = listOf(PERFECT, PERFECT),
        completedToday: Int = 0,
        todayPerfect: Boolean = false,
        streak: Int = 2,
        bossDefeatedToday: Boolean = false,
        mentors: List<Mentor> = listOf(Mentor.ROSHI, Mentor.GOKU),
        day: Long = 100,
    ) = ReactionContext(day, past, completedToday, todayPerfect, streak, bossDefeatedToday, mentors)

    @Test
    fun `an ordinary day gets an everyday line`() {
        assertEquals(ReactionTrigger.IDLE, ReactionRules.trigger(context()))
        assertEquals(ReactionTrigger.IDLE, ReactionRules.trigger(context(past = emptyList())))
    }

    @Test
    fun `a missed yesterday is called out until something is done today`() {
        assertEquals(ReactionTrigger.MISSED_DAY, ReactionRules.trigger(context(past = listOf(BROKEN, PERFECT))))
        assertEquals(ReactionTrigger.MISSED_DAY, ReactionRules.trigger(context(past = listOf(MISSED, PERFECT))))
    }

    @Test
    fun `the first check-off after a miss is a comeback`() {
        val back = context(past = listOf(BROKEN, PERFECT), completedToday = 1)
        assertEquals(ReactionTrigger.COMEBACK, ReactionRules.trigger(back))
    }

    @Test
    fun `rest days in between do not hide a miss`() {
        assertTrue(ReactionRules.lastTrainingDayWasMissed(listOf(REST, REST, BROKEN, PERFECT)))
        assertFalse(ReactionRules.lastTrainingDayWasMissed(listOf(REST, PERFECT, BROKEN)))
    }

    @Test
    fun `a day saved by a bean is not scolded`() {
        assertFalse(ReactionRules.lastTrainingDayWasMissed(listOf(BEAN_COVERED, PERFECT)))
        assertEquals(ReactionTrigger.IDLE, ReactionRules.trigger(context(past = listOf(BEAN_COVERED, PERFECT))))
    }

    @Test
    fun `a streak milestone is marked once today is perfect`() {
        assertEquals(
            ReactionTrigger.STREAK_MILESTONE,
            ReactionRules.trigger(context(todayPerfect = true, completedToday = 2, streak = 7)),
        )
        assertEquals(ReactionTrigger.IDLE, ReactionRules.trigger(context(todayPerfect = false, streak = 7)))
        assertEquals(
            ReactionTrigger.IDLE,
            ReactionRules.trigger(context(todayPerfect = true, completedToday = 2, streak = 8)),
        )
    }

    @Test
    fun `a boss defeated today outranks everything else`() {
        val busyDay = context(
            past = listOf(BROKEN),
            completedToday = 1,
            todayPerfect = true,
            streak = 7,
            bossDefeatedToday = true,
        )
        assertEquals(ReactionTrigger.BOSS_DEFEATED, ReactionRules.trigger(busyDay))
    }

    @Test
    fun `mentors with habits take turns day by day`() {
        val mentors = listOf(Mentor.GOKU, Mentor.ROSHI, Mentor.BULMA)
        val speakers = (100L..105L).map { ReactionRules.mentor(context(mentors = mentors, day = it)) }
        assertEquals(mentors.toSet(), speakers.toSet())
        assertEquals(speakers.take(3), speakers.drop(3))
        assertTrue(speakers.zipWithNext().all { (a, b) -> a != b })
    }

    @Test
    fun `Roshi speaks when there are no habits yet`() {
        assertEquals(Mentor.ROSHI, ReactionRules.mentor(context(mentors = emptyList())))
    }

    @Test
    fun `only mentors who train a habit speak`() {
        val speakers = (0L..20L).map { ReactionRules.mentor(context(mentors = listOf(Mentor.PICCOLO), day = it)) }
        assertEquals(setOf(Mentor.PICCOLO), speakers.toSet())
    }

    @Test
    fun `a mentor's lines come round one per day without repeating early`() {
        val lines = 8
        val picked = (200L until 200L + lines).map { ReactionRules.lineIndex(it, Mentor.GOKU, lines) }
        assertEquals((0 until lines).toSet(), picked.toSet())
        assertEquals(0, ReactionRules.lineIndex(5, Mentor.GOKU, lineCount = 0))
    }

    @Test
    fun `line picking copes with days before 1970`() {
        val index = ReactionRules.lineIndex(-3, Mentor.ROSHI, 8)
        assertTrue(index in 0 until 8)
    }

    @Test
    fun `placeholders are filled in`() {
        assertEquals("7 days. Cell is down.", ReactionRules.fill("{streak} days. {boss} is down.", 7, "Cell"))
        assertEquals("the boss is down.", ReactionRules.fill("{boss} is down.", 3, null))
        assertEquals("No placeholders.", ReactionRules.fill("No placeholders.", 3, "Cell"))
    }
}
