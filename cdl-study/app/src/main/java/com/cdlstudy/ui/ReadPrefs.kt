package com.cdlstudy.ui

import android.content.Context
import com.cdlstudy.domain.FocusText

/** Your reading settings. They are kept, so you set them once. */
class ReadPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("reading", Context.MODE_PRIVATE)

    var focus: Boolean
        get() = prefs.getBoolean(KEY_FOCUS, true)
        set(value) = prefs.edit().putBoolean(KEY_FOCUS, value).apply()

    var strength: FocusText.Strength
        get() = runCatching {
            FocusText.Strength.valueOf(prefs.getString(KEY_STRENGTH, null) ?: "MEDIUM")
        }.getOrDefault(FocusText.Strength.MEDIUM)
        set(value) = prefs.edit().putString(KEY_STRENGTH, value.name).apply()

    var scale: Float
        get() = prefs.getFloat(KEY_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE)
        set(value) = prefs.edit().putFloat(KEY_SCALE, value.coerceIn(MIN_SCALE, MAX_SCALE)).apply()

    var wideSpacing: Boolean
        get() = prefs.getBoolean(KEY_SPACING, true)
        set(value) = prefs.edit().putBoolean(KEY_SPACING, value).apply()

    /** Show the pages of your own manual instead of the lesson. */
    var showManualPages: Boolean
        get() = prefs.getBoolean(KEY_PAGES, false)
        set(value) = prefs.edit().putBoolean(KEY_PAGES, value).apply()

    fun style(): ReadStyle = ReadStyle(focus, strength, scale, wideSpacing)

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
