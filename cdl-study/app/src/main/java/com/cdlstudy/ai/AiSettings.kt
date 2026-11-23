package com.cdlstudy.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Your own API key, and which model grades with it.
 *
 * The key is kept in this app's private settings file. No other app on the
 * phone can read it, and it goes nowhere but to the API.
 */
class AiSettings(context: Context) {

    private val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)

    var key by mutableStateOf(prefs.getString(KEY_KEY, "").orEmpty())
        private set

    var model by mutableStateOf(prefs.getString(KEY_MODEL, Models.DEFAULT).orEmpty())
        private set

    /** AI grading is off until there is a key. There is no other switch. */
    val ready: Boolean get() = key.isNotBlank()

    /** The last four characters, to show you the key went in. */
    val keyTail: String get() = if (key.length < 8) "" else "…" + key.takeLast(4)

    fun chooseKey(text: String) {
        key = text.trim()
        prefs.edit().putString(KEY_KEY, key).apply()
    }

    fun forgetKey() = chooseKey("")

    fun chooseModel(id: String) {
        model = if (id in Models.ALL) id else Models.DEFAULT
        prefs.edit().putString(KEY_MODEL, model).apply()
    }

    object Models {
        const val DEFAULT = "claude-opus-5-5"
        val ALL = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5")

        fun label(id: String): String = when (id) {
            "claude-opus-5-5" -> "Opus 5.5"
            "claude-sonnet-5-5" -> "Sonnet 5.5"
            "claude-haiku-5-5" -> "Haiku 5.5"
            else -> id
        }

        fun note(id: String): String = when (id) {
            "claude-opus-5-5" -> "The most careful with numbers. Costs the most."
            "claude-sonnet-5-5" -> "Quick, and good enough for most recalls."
            "claude-haiku-5-5" -> "The cheapest. It can miss a wrong number."
            else -> ""
        }
    }

    companion object {
        private const val KEY_KEY = "apiKey"
        private const val KEY_MODEL = "model"
    }
}
