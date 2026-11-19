package com.saiyanhabits.data

import android.content.Context
import androidx.core.content.edit
import com.saiyanhabits.GameConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The app's own switches, kept on the phone. Not part of an export. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _eveningNudge = MutableStateFlow(prefs.getBoolean(KEY_NUDGE_ON, true))
    private val _eveningNudgeMinutes = MutableStateFlow(
        prefs.getInt(KEY_NUDGE_MINUTES, GameConfig.EVENING_NUDGE_DEFAULT_MINUTES),
    )
    private val _sound = MutableStateFlow(prefs.getBoolean(KEY_SOUND_ON, true))
    private val _callsRing = MutableStateFlow(prefs.getBoolean(KEY_CALLS_RING, true))

    /** True when a mentor rings once a day. */
    val callsRing: StateFlow<Boolean> = _callsRing

    fun setCallsRing(on: Boolean) {
        prefs.edit { putBoolean(KEY_CALLS_RING, on) }
        _callsRing.value = on
    }

    /** True when the evening nudge is on. */
    val eveningNudge: StateFlow<Boolean> = _eveningNudge

    /** When the evening nudge comes, in minutes after midnight. */
    val eveningNudgeMinutes: StateFlow<Int> = _eveningNudgeMinutes

    /** True when sound effects play. */
    val sound: StateFlow<Boolean> = _sound

    fun setEveningNudge(on: Boolean) {
        prefs.edit { putBoolean(KEY_NUDGE_ON, on) }
        _eveningNudge.value = on
    }

    fun setEveningNudgeMinutes(minutes: Int) {
        prefs.edit { putInt(KEY_NUDGE_MINUTES, minutes) }
        _eveningNudgeMinutes.value = minutes
    }

    fun setSound(on: Boolean) {
        prefs.edit { putBoolean(KEY_SOUND_ON, on) }
        _sound.value = on
    }

    private companion object {
        const val KEY_NUDGE_ON = "evening_nudge"
        const val KEY_NUDGE_MINUTES = "evening_nudge_minutes"
        const val KEY_SOUND_ON = "sound"
        const val KEY_CALLS_RING = "calls_ring"
    }
}
