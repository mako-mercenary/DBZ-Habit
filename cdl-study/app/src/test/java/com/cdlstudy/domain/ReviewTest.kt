package com.cdlstudy.domain

import com.cdlstudy.domain.Review.Grade
import com.cdlstudy.domain.Review.Stage
import com.cdlstudy.domain.Review.State
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTest {

    private val zone: ZoneId = ZoneId.of("America/Denver")
    private val today = LocalDate.of(2026, 12, 1)
    private val now = ZonedDateTime.of(today, java.time.LocalTime.of(19, 0), zone)
        .toInstant().toEpochMilli()

    private val DAY = 24 * 60L

    private fun answer(state: State, grade: Grade, test: LocalDate? = null) =
        Review.answer(state, grade, now, test, zone)

    // --- the ordinary SM-2 part -------------------------------------------

    @Test
    fun `a new card answered Good comes back tomorrow`() {
        val next = answer(State(), Grade.GOOD)
        assertEquals(Stage.REVIEW, next.stage)
        assertEquals(DAY, next.intervalMinutes)
        assertEquals(now + DAY * 60_000, next.dueAt)
        assertEquals(1, next.reps)
    }

    @Test
    fun `the second Good is six days, and after that the ease decides`() {
        val first = answer(State(), Grade.GOOD)
        val second = answer(first, Grade.GOOD)
        assertEquals(6 * DAY, second.intervalMinutes)
        val third = answer(second, Grade.GOOD)
        assertEquals((6 * DAY * Review.START_EASE).toLong(), third.intervalMinutes)
    }

    @Test
    fun `Again sends a card back to ten minutes and costs ease`() {
        val learned = answer(answer(State(), Grade.GOOD), Grade.GOOD)
        val missed = answer(learned, Grade.AGAIN)
        assertEquals(Stage.LEARNING, missed.stage)
        assertEquals(Review.AGAIN_MINUTES, missed.intervalMinutes)
        assertEquals(5L, missed.intervalMinutes)
        assertEquals(Review.START_EASE - 0.20, missed.ease, 0.0001)
        assertEquals(1, missed.lapses)
    }

    @Test
    fun `missing a card you never knew is not a lapse`() {
        // A new card you cannot answer is not a card you forgot.
        val missed = answer(State(), Grade.AGAIN)
        assertEquals(0, missed.lapses)
        val again = answer(missed, Grade.AGAIN)
        assertEquals(0, again.lapses)
    }

    @Test
    fun `ease never falls below the floor, however often you miss it`() {
        var state = answer(answer(State(), Grade.GOOD), Grade.GOOD)
        repeat(30) { state = answer(state, Grade.AGAIN) }
        assertEquals(Review.MIN_EASE, state.ease, 0.0001)
    }

    @Test
    fun `Easy grows faster than Good, and Hard slower`() {
        val learned = answer(answer(State(), Grade.GOOD), Grade.GOOD)
        val hard = answer(learned, Grade.HARD).intervalMinutes
        val good = answer(learned, Grade.GOOD).intervalMinutes
        val easy = answer(learned, Grade.EASY).intervalMinutes
        assertTrue("hard $hard < good $good", hard < good)
        assertTrue("good $good < easy $easy", good < easy)
    }

    @Test
    fun `a grown interval is never under a day`() {
        val learned = answer(answer(State(), Grade.GOOD), Grade.GOOD)
        val stubborn = learned.copy(ease = Review.MIN_EASE, intervalMinutes = DAY)
        assertTrue(answer(stubborn, Grade.HARD).intervalMinutes >= DAY)
    }

    // --- the test date cap -------------------------------------------------

    @Test
    fun `with no test date nothing is capped`() {
        var state = State()
        repeat(6) { state = answer(state, Grade.EASY) }
        assertTrue("it should have run into months: ${state.intervalMinutes}", state.intervalMinutes > 90 * DAY)
    }

    @Test
    fun `no card is ever scheduled past the test`() {
        val test = today.plusDays(30)
        var state = State()
        repeat(10) {
            state = answer(state, Grade.EASY, test)
            val daysOut = state.intervalMinutes / DAY
            assertTrue("scheduled $daysOut days out with 30 to go", daysOut <= 30)
        }
    }

    @Test
    fun `the cap is half the time left, so a card is seen again with time to fix it`() {
        val test = today.plusDays(20)
        val learned = State(stage = Stage.REVIEW, intervalMinutes = 60 * DAY)
        assertEquals(10 * DAY, answer(learned, Grade.GOOD, test).intervalMinutes)
    }

    @Test
    fun `in the last days everything comes back daily`() {
        val learned = State(stage = Stage.REVIEW, intervalMinutes = 60 * DAY)
        listOf(3L, 2L, 1L).forEach { daysLeft ->
            val test = today.plusDays(daysLeft)
            assertEquals(
                "with $daysLeft days left",
                DAY,
                answer(learned, Grade.GOOD, test).intervalMinutes,
            )
        }
    }

    @Test
    fun `a short interval is left alone, because the cap only shortens`() {
        val test = today.plusDays(30)
        val next = answer(State(), Grade.GOOD, test)
        assertEquals("one day is already inside the cap", DAY, next.intervalMinutes)
    }

    @Test
    fun `a test date that has passed caps nothing`() {
        val gone = today.minusDays(1)
        val learned = State(stage = Stage.REVIEW, intervalMinutes = 60 * DAY)
        assertEquals(
            answer(learned, Grade.GOOD).intervalMinutes,
            answer(learned, Grade.GOOD, gone).intervalMinutes,
        )
    }

    @Test
    fun `the cap never lengthens an interval`() {
        listOf(10L, DAY, 6 * DAY, 100 * DAY).forEach { minutes ->
            listOf(1L, 5L, 40L, 400L).forEach { daysLeft ->
                val capped = Review.cap(minutes, now, today.plusDays(daysLeft), zone)
                assertTrue("$minutes grew to $capped", capped <= minutes)
            }
        }
    }

    @Test
    fun `the test day itself does not break the cap`() {
        val capped = Review.cap(100 * DAY, now, today, zone)
        assertEquals("the test is today: do not cap, just let it be", 100 * DAY, capped)
    }

    // --- what the buttons say ----------------------------------------------

    @Test
    fun `the preview tells you what each button does, in short words`() {
        val preview = Review.preview(State(), now)
        assertEquals(Grade.entries.toSet(), preview.keys)
        assertEquals("5 min", preview[Grade.AGAIN])
        assertEquals("10 min", preview[Grade.HARD])
        assertEquals("1 d", preview[Grade.GOOD])
        assertEquals("6 d", preview[Grade.EASY])
    }

    /** Every state a card can realistically be in, for the sweeps below. */
    private fun everyState(): List<State> = buildList {
        add(State())
        add(State(stage = Stage.LEARNING, intervalMinutes = 5))
        add(State(stage = Stage.LEARNING, intervalMinutes = 10))
        listOf(1L, 2L, 3L, 6L, 10L, 15L, 21L, 30L, 45L, 60L, 90L, 200L).forEach { days ->
            listOf(Review.MIN_EASE, 1.8, 2.0, 2.3, Review.START_EASE, 2.8, 3.1).forEach { ease ->
                add(State(stage = Stage.REVIEW, intervalMinutes = days * DAY, ease = ease))
            }
        }
    }

    @Test
    fun `no two buttons on a card ever read the same`() {
        // A button that reads like the one next to it is a button with no
        // purpose. Two separate faults did this: Again and Hard both gave ten
        // minutes on a new card, and at 15 days Good and Easy both rounded to
        // "1 mo" though one was 35 days and the other 45.
        everyState().forEach { state ->
            val labels = Review.preview(state, now).values.toList()
            assertEquals(
                "two buttons read the same on $state: $labels",
                labels.size,
                labels.distinct().size,
            )
        }
    }

    @Test
    fun `Again and Hard differ on a card you have never seen`() {
        val again = answer(State(), Grade.AGAIN)
        val hard = answer(State(), Grade.HARD)
        assertTrue(
            "again ${again.intervalMinutes} must be sooner than hard ${hard.intervalMinutes}",
            again.intervalMinutes < hard.intervalMinutes,
        )
    }

    @Test
    fun `the four buttons always run shortest to longest, in every state`() {
        // Again under Hard under Good under Easy, always. A better answer
        // must never send a card away for less time than a worse one. Easy
        // once gave 3 days where Good gave 6, on a card still on its first
        // day, because Easy was worked from the raw interval and Good was not.
        everyState().forEach { state ->
            val gaps = Grade.entries.map { answer(state, it).intervalMinutes }
            assertEquals("out of order for $state: $gaps", gaps.sorted(), gaps)
        }
    }

    @Test
    fun `the order holds with a test date squeezing everything`() {
        // The cap shortens the long intervals most, so it is the likeliest
        // thing to collapse the order. Ties are allowed once capped, because
        // two buttons that both hit the ceiling really do do the same thing.
        listOf(2L, 5L, 10L, 30L, 90L).forEach { daysLeft ->
            val test = today.plusDays(daysLeft)
            listOf(1L, 6L, 30L).forEach { days ->
                val state = State(stage = Stage.REVIEW, intervalMinutes = days * DAY)
                val gaps = Grade.entries.map { answer(state, it, test).intervalMinutes }
                assertEquals("out of order $daysLeft days out: $gaps", gaps.sorted(), gaps)
            }
        }
    }

    @Test
    fun `a gap reads in the biggest unit that fits`() {
        assertEquals("10 min", Review.gap(10))
        assertEquals("2 hr", Review.gap(120))
        assertEquals("1 d", Review.gap(DAY))
        assertEquals("20 d", Review.gap(20 * DAY))
        assertEquals("60 d", Review.gap(60 * DAY))
        assertEquals("120 d", Review.gap(120 * DAY))
        assertEquals("730 d", Review.gap(730 * DAY))

    }

    @Test
    fun `the preview matches what answering actually does`() {
        val state = State(stage = Stage.REVIEW, intervalMinutes = 9 * DAY, ease = 2.3)
        val test = today.plusDays(45)
        Review.preview(state, now, test, zone).forEach { (grade, shown) ->
            assertEquals(
                "the $grade button lies",
                shown,
                Review.gap(answer(state, grade, test).intervalMinutes),
            )
        }
    }
}
