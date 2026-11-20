package com.cdlstudy.content

import android.content.Context
import com.cdlstudy.domain.Content
import com.cdlstudy.domain.ContentReader
import com.cdlstudy.domain.Section
import com.cdlstudy.domain.StudyPlan

/**
 * Reads every section file in assets/content.
 *
 * A file that will not parse is reported, not hidden. Bad content must be
 * visible, because a wrong fact in a study app is worse than a missing one.
 */
object ContentStore {

    data class Loaded(val content: Content, val errors: List<String>)

    @Volatile
    private var cached: Loaded? = null

    fun load(context: Context): Loaded =
        cached ?: synchronized(this) { cached ?: read(context).also { cached = it } }

    private fun read(context: Context): Loaded {
        val names = runCatching {
            context.assets.list("content")?.filter { it.endsWith(".json") }?.sorted() ?: emptyList()
        }.getOrElse { return Loaded(Content(emptyList()), listOf("Cannot read the content folder: ${it.message}")) }

        val sections = mutableListOf<Section>()
        val errors = mutableListOf<String>()
        for (name in names) {
            try {
                val text = context.assets.open("content/$name").bufferedReader().use { it.readText() }
                val section = ContentReader.readSection(text)
                // The plan covers the sections Utah requires for a Class A with
                // air brakes. An endorsement section is left out, even if a file
                // for it is here.
                if (StudyPlan.isRequired(section.number)) {
                    sections.add(section)
                } else {
                    errors.add(
                        "$name: section ${section.number} is not in the Class A plan, so it is left out"
                    )
                }
            } catch (e: Exception) {
                errors.add("$name: ${e.message}")
            }
        }
        return Loaded(Content(sections.sortedBy { it.number }), errors)
    }
}
