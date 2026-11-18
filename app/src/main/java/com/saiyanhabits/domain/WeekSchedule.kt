package com.saiyanhabits.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Which days of the week a habit is scheduled on, packed into one integer:
 * Monday is bit 0, Sunday is bit 6.
 */
object WeekSchedule {
    const val NONE = 0
    const val EVERY_DAY = 0b1111111
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000

    private val shortNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun of(vararg days: DayOfWeek): Int = days.fold(NONE) { mask, day -> mask or bit(day) }

    fun isScheduled(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    fun isScheduled(mask: Int, date: LocalDate): Boolean = isScheduled(mask, date.dayOfWeek)

    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bit(day)

    /**
     * How often a habit is done, e.g. "Every day", "3 times a week" or
     * "Weekdays · 8 glasses each day".
     */
    fun describeHabit(mask: Int, weeklyTarget: Int?, dailyTarget: Int, unit: String): String {
        val often = when (weeklyTarget) {
            null -> describe(mask)
            1 -> "Once a week"
            2 -> "Twice a week"
            else -> "$weeklyTarget times a week"
        }
        if (dailyTarget <= 1) return often
        val what = unit.trim().ifBlank { "times" }
        return "$often · $dailyTarget $what each time"
    }

    /** Short text for a schedule, e.g. "Every day" or "Mon, Wed, Fri". */
    fun describe(mask: Int): String = when (mask and EVERY_DAY) {
        NONE -> "No days"
        EVERY_DAY -> "Every day"
        WEEKDAYS -> "Weekdays"
        WEEKEND -> "Weekends"
        else -> DayOfWeek.entries
            .filter { isScheduled(mask, it) }
            .joinToString(", ") { shortNames[it.value - 1] }
    }
}
