package com.cdlstudy.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cdlstudy.domain.FocusText

/**
 * Your reading settings, held in one place for the whole app.
 *
 * They are Compose state as well as stored settings, so a change on the reading
 * screen shows at once in a lesson that is already open. Holding a copy per
 * screen made the settings drift apart.
 */
class ReadingSettings(context: Context) {

    private val prefs = context.getSharedPreferences("reading", Context.MODE_PRIVATE)

    var style by mutableStateOf(load())
        private set

    /** Show the pages of your own manual instead of the lesson. */
    var showManualPages by mutableStateOf(prefs.getBoolean(KEY_PAGES, false))
        private set

    private fun load() = ReadStyle(
        focus = prefs.getBoolean(KEY_FOCUS, true),
        strength = runCatching {
            FocusText.Strength.valueOf(prefs.getString(KEY_STRENGTH, null) ?: "MEDIUM")
        }.getOrDefault(FocusText.Strength.MEDIUM),
        scale = prefs.getFloat(KEY_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE),
        wideSpacing = prefs.getBoolean(KEY_SPACING, true),
    )

    fun toggleFocus() {
        style = style.copy(focus = !style.focus)
        prefs.edit().putBoolean(KEY_FOCUS, style.focus).apply()
    }

    fun chooseStrength(strength: FocusText.Strength) {
        style = style.copy(strength = strength)
        prefs.edit().putString(KEY_STRENGTH, strength.name).apply()
    }

    fun bigger() = setScale(style.scale + SCALE_STEP)

    fun smaller() = setScale(style.scale - SCALE_STEP)

    private fun setScale(value: Float) {
        style = style.copy(scale = value.coerceIn(MIN_SCALE, MAX_SCALE))
        prefs.edit().putFloat(KEY_SCALE, style.scale).apply()
    }

    fun toggleSpacing() {
        style = style.copy(wideSpacing = !style.wideSpacing)
        prefs.edit().putBoolean(KEY_SPACING, style.wideSpacing).apply()
    }

    fun toggleManualPages() {
        showManualPages = !showManualPages
        prefs.edit().putBoolean(KEY_PAGES, showManualPages).apply()
    }

    companion object {
        const val MIN_SCALE = 0.85f
        const val MAX_SCALE = 1.6f
        const val SCALE_STEP = 0.15f
        private const val KEY_FOCUS = "focus"
        private const val KEY_STRENGTH = "strength"
        private const val KEY_SCALE = "scale"
        private const val KEY_SPACING = "spacing"
        private const val KEY_PAGES = "manualPages"
    }
}
