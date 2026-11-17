package com.saiyanhabits.data

import android.content.Context
import android.util.Log
import com.saiyanhabits.domain.DialogueParser
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.MentorDialogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The mentors' lines, read from assets/dialogue/<mentor>.json the first time
 * they are needed. A file that is missing or broken gives that mentor no lines
 * rather than crashing the app; the unit tests check the files that ship.
 */
class DialogueRepository(private val context: Context) {
    private val lock = Mutex()
    private var cached: Map<Mentor, MentorDialogue>? = null

    suspend fun all(): Map<Mentor, MentorDialogue> = lock.withLock {
        cached ?: withContext(Dispatchers.IO) { Mentor.entries.associateWith(::load) }.also { cached = it }
    }

    private fun load(mentor: Mentor): MentorDialogue =
        try {
            val text = context.assets.open(fileName(mentor)).bufferedReader().use { it.readText() }
            DialogueParser.parse(text)
        } catch (e: Exception) {
            Log.e("Dialogue", "Could not read ${fileName(mentor)}: ${e.message}")
            MentorDialogue(mentor, emptyMap(), emptyList())
        }

    companion object {
        fun fileName(mentor: Mentor): String = "dialogue/${mentor.name.lowercase()}.json"
    }
}
