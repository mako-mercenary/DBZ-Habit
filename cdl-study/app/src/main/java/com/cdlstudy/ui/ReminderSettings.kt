package com.cdlstudy.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalTime

/** Whether the daily reminder is on, and at what time. */
class ReminderSettings(context: Context) {

    private val prefs = context.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    var enabled by mutableStateOf(prefs.getBoolean(KEY_ON, false))
        private set

    /** Minutes after midnight. */
    var minuteOfDay by mutableStateOf(prefs.getInt(KEY_AT, DEFAULT_MINUTE))
        private set

    val time: LocalTime get() = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

    val label: String get() = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    fun switchOn(on: Boolean) {
        enabled = on
        prefs.edit().putBoolean(KEY_ON, on).apply()
    }

    fun chooseTime(minutes: Int) {
        minuteOfDay = minutes.coerceIn(0, 24 * 60 - 1)
        prefs.edit().putInt(KEY_AT, minuteOfDay).apply()
    }

    companion object {
        /** 7 in the evening: after the driving day, before it is too late to study. */
        const val DEFAULT_MINUTE = 19 * 60
        private const val KEY_ON = "enabled"
        private const val KEY_AT = "minuteOfDay"
    }
}
