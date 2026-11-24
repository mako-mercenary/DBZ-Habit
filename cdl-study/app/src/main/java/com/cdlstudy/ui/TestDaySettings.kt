package com.cdlstudy.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which test-day items you have ticked off. Kept on this phone. */
class TestDaySettings(context: Context) {

    private val prefs = context.getSharedPreferences("testday", Context.MODE_PRIVATE)

    var ticked by mutableStateOf(prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet())
        private set

    fun toggle(id: String) {
        ticked = if (id in ticked) ticked - id else ticked + id
        prefs.edit().putStringSet(KEY, ticked).apply()
    }

    fun clear() {
        ticked = emptySet()
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "ticked"
    }
}
