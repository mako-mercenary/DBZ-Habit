package com.cdlstudy.domain

import java.time.LocalDate
import java.time.ZoneId

/**
 * A run of days on which you did at least one recall.
 *
 * Today does not break a streak until it is over, so a streak earned yesterday
 * still stands this morning. It breaks when a whole day passes with no recall.
 */
object Streak {

    data class Result(
        val current: Int,
        val longest: Int,
        val studiedToday: Boolean,
        /** The days you studied, newest first. */
        val days: List<LocalDate>,
    ) {
        /** True when the streak stands only because today is not over. */
        val atRisk: Boolean get() = current > 0 && !studiedToday
    }

    /** @param times when each recall was graded, in milliseconds. */
    fun of(times: List<Long>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Result {
        val days = times
            .map { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .distinct()
            .sortedDescending()
        if (days.isEmpty()) return Result(0, 0, false, emptyList())

        val studiedToday = days.first() == today

        // The streak may end today or yesterday. Any older and it is broken.
        var current = 0
        var expected = if (studiedToday) today else today.minusDays(1)
        for (day in days) {
            if (day > expected) continue
            if (day == expected) {
                current++
                expected = expected.minusDays(1)
            } else {
                break
            }
        }

        var longest = 1
        var run = 1
        for (index in 1 until days.size) {
            if (days[index] == days[index - 1].minusDays(1)) {
                run++
            } else {
                run = 1
            }
            if (run > longest) longest = run
        }
        if (current > longest) longest = current

        return Result(current, longest, studiedToday, days)
    }
}
