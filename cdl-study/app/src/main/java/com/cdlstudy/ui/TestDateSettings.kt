package com.cdlstudy.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The day you sit the test.
 *
 * It is the one setting that changes how the whole deck behaves: with a date
 * set, no card is ever scheduled past it. Without one, the app schedules for
 * long-term memory, which is the wrong target for an exam on a date.
 */
class TestDateSettings(context: Context) {

    private val prefs = context.getSharedPreferences("testdate", Context.MODE_PRIVATE)

    var date by mutableStateOf(read())
        private set

    private fun read(): LocalDate? {
        val epochDay = prefs.getLong(KEY, -1L)
        return if (epochDay < 0) null else LocalDate.ofEpochDay(epochDay)
    }

    fun choose(value: LocalDate?) {
        date = value
        prefs.edit().apply {
            if (value == null) remove(KEY) else putLong(KEY, value.toEpochDay())
        }.apply()
    }

    /** Days from today, or null when no date is set. Negative once it has passed. */
    fun daysLeft(today: LocalDate = LocalDate.now()): Long? =
        date?.let { ChronoUnit.DAYS.between(today, it) }

    fun label(today: LocalDate = LocalDate.now()): String {
        val days = daysLeft(today) ?: return "Not set"
        return when {
            days < 0 -> "Was ${-days} day(s) ago"
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            else -> "In $days days"
        }
    }

    companion object {
        private const val KEY = "epochDay"
    }
}
