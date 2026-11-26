package com.cdlstudy.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * When a card comes back.
 *
 * The intervals are SM-2: get a card right and the gap to the next sighting
 * grows by an ease factor, get it wrong and the card starts again. That part
 * is ordinary and well proven.
 *
 * The part that is not ordinary is the cap. You are not learning this for
 * life. You are learning it for one exam on one day, and a card scheduled for
 * three weeks after that day is a card you will never see again before it
 * matters. So no interval may put a card past the test date, and as the date
 * closes in the intervals compress until, in the last days, everything you are
 * shaky on comes back daily.
 *
 * Set no test date and the cap does nothing: it is plain SM-2.
 */
object Review {

    /** What you say about a card after you see the answer. */
    enum class Grade {
        /** You did not know it. The card starts again. */
        AGAIN,

        /** You got there, but it was work. */
        HARD,

        /** You knew it. */
        GOOD,

        /** You knew it at once. */
        EASY,
        ;

        val label: String get() = when (this) {
            AGAIN -> "Again"
            HARD -> "Hard"
            GOOD -> "Good"
            EASY -> "Easy"
        }
    }

    /** Where a card is in its life. */
    enum class Stage {
        /** Never answered. */
        NEW,

        /** Being learned, or re-learned after a lapse. Minutes, not days. */
        LEARNING,

        /** Learned. Days. */
        REVIEW,
        ;

        companion object {
            fun of(name: String?): Stage =
                entries.firstOrNull { it.name == name } ?: NEW
        }
    }

    /** Everything the scheduler knows about one card. */
    data class State(
        val stage: Stage = Stage.NEW,
        /** Minutes until it is next due, from the moment it was answered. */
        val intervalMinutes: Long = 0,
        /** SM-2's ease factor. Starts at 2.5, never falls below 1.3. */
        val ease: Double = START_EASE,
        /** How many times it has been answered. */
        val reps: Int = 0,
        /** How many times it has been forgotten after being learned. */
        val lapses: Int = 0,
        /** Epoch millis when it is next due. */
        val dueAt: Long = 0,
    )

    const val START_EASE = 2.5
    const val MIN_EASE = 1.3

    private const val MINUTE = 60_000L
    private const val DAY = 24 * 60L

    /**
     * A card you just missed comes back inside this sitting.
     *
     * Again and Hard must not land on the same minute. On a card you have
     * never seen they once both gave ten minutes, which made the Hard button
     * a lie: it claimed to mean something and did nothing. Five and ten are
     * far enough apart to be worth choosing between.
     */
    const val AGAIN_MINUTES = 5L
    const val HARD_MINUTES = 10L

    /** The first gap after you learn a card, and the second. */
    const val FIRST_DAYS = 1L
    const val SECOND_DAYS = 6L

    /**
     * Answers one card.
     *
     * [testDate] may be null, and then nothing is capped.
     */
    fun answer(
        state: State,
        grade: Grade,
        now: Long,
        testDate: LocalDate? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): State {
        val next = plain(state, grade)
        val capped = cap(next.intervalMinutes, now, testDate, zone)
        return next.copy(
            intervalMinutes = capped,
            dueAt = now + capped * MINUTE,
            reps = state.reps + 1,
        )
    }

    /** The SM-2 part, before the test date has a say. */
    private fun plain(state: State, grade: Grade): State = when (grade) {
        Grade.AGAIN -> state.copy(
            stage = Stage.LEARNING,
            intervalMinutes = AGAIN_MINUTES,
            ease = max(MIN_EASE, state.ease - 0.20),
            // A card only lapses if it had been learned. Missing a card you
            // have never got right is not forgetting, it is not knowing yet.
            lapses = state.lapses + if (state.stage == Stage.REVIEW) 1 else 0,
        )

        Grade.HARD -> when (state.stage) {
            Stage.NEW, Stage.LEARNING -> state.copy(
                stage = Stage.LEARNING,
                intervalMinutes = HARD_MINUTES,
                ease = max(MIN_EASE, state.ease - 0.15),
            )

            Stage.REVIEW -> state.copy(
                intervalMinutes = hardInterval(state),
                ease = max(MIN_EASE, state.ease - 0.15),
            )
        }

        Grade.GOOD -> when (state.stage) {
            Stage.NEW -> state.copy(stage = Stage.REVIEW, intervalMinutes = FIRST_DAYS * DAY)
            Stage.LEARNING -> state.copy(
                stage = Stage.REVIEW,
                intervalMinutes = max(FIRST_DAYS * DAY, state.intervalMinutes),
            )

            Stage.REVIEW -> state.copy(intervalMinutes = goodInterval(state))
        }

        Grade.EASY -> when (state.stage) {
            Stage.NEW, Stage.LEARNING -> state.copy(
                stage = Stage.REVIEW,
                intervalMinutes = SECOND_DAYS * DAY,
                ease = state.ease + 0.15,
            )

            // Built on what Good would give, never on the raw interval.
            // Worked from the raw interval, a card still on its first day got
            // 3 days for Easy and 6 for Good, so the better answer sent it
            // away for less time. Easy must never be worse than Good.
            Stage.REVIEW -> state.copy(
                intervalMinutes = easyInterval(state),
                ease = state.ease + 0.15,
            )
        }
    }

    /**
     * The three intervals on a learned card, each at least a day clear of the
     * one below it.
     *
     * The gap is not decoration. A card at two days with the lowest ease gave
     * 2.4 days for Hard and 2.6 for Good, which both show as "2 d", so the two
     * buttons read alike and choosing between them did nothing. A better
     * answer has to buy a longer gap, and it has to be long enough to see.
     */
    private fun hardInterval(state: State): Long = grow(state.intervalMinutes, 1.2)

    private fun goodInterval(state: State): Long {
        val plain = if (state.intervalMinutes <= FIRST_DAYS * DAY) {
            SECOND_DAYS * DAY
        } else {
            grow(state.intervalMinutes, state.ease)
        }
        return max(plain, hardInterval(state) + DAY)
    }

    private fun easyInterval(state: State): Long {
        val good = goodInterval(state)
        return max(grow(good, 1.3), good + DAY)
    }

    private fun grow(minutes: Long, factor: Double): Long =
        max(DAY, (minutes * factor).roundToLong())

    /**
     * Keeps an interval on the near side of the test.
     *
     * The rule: a card must be seen again before the test, with time left to
     * fix it. So the longest allowed gap is half the time remaining, and never
     * more than the days left. One day out, everything is due tomorrow, which
     * is the morning of the test, so everything is due now.
     *
     * A date in the past caps nothing. The test has been and gone, and the app
     * has no business deciding what that means.
     */
    fun cap(
        minutes: Long,
        now: Long,
        testDate: LocalDate?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        if (testDate == null) return minutes
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, testDate)
        if (daysLeft <= 0) return minutes
        val half = max(1L, daysLeft / 2)
        return minOf(minutes, half * DAY)
    }

    /**
     * How long until [dueAt], written the way a button has room for.
     *
     * Days, all the way up, and never months or years.
     *
     * Rounding to a bigger unit threw away the thing the button is for. A card
     * at 15 days read "1 mo" for both Good and Easy, though one was 35 days
     * and the other 45; at 90 days with a low ease, Hard and Good both read
     * "4 mo". The scheduler keeps the grades a day apart, and a day apart is
     * invisible once you round to months.
     *
     * So "117 d" rather than "4 mo". Less pretty at the far end, never wrong,
     * and with a test date set a gap that long cannot happen anyway.
     */
    fun gap(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        minutes < DAY -> "${minutes / 60} hr"
        else -> "${minutes / DAY} d"
    }

    /** What each button would do to this card, for the labels on them. */
    fun preview(
        state: State,
        now: Long,
        testDate: LocalDate? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<Grade, String> =
        Grade.entries.associateWith { grade ->
            gap(answer(state, grade, now, testDate, zone).intervalMinutes)
        }
}
