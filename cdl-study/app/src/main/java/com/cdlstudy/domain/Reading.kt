package com.cdlstudy.domain

/**
 * The lesson you read in the app.
 *
 * This is NOT the handbook's text. The handbook is AAMVA material, and
 * permission to reproduce it is given to state driver license agencies only.
 * Each lesson here is a rewrite in plain words, with every number, limit and
 * procedure kept exactly as the manual gives it.
 *
 * Short blocks, short sentences and short lines are easier to read than the
 * manual's two columns of small print, and they suit the focus text in the app.
 */
sealed interface ReadBlock {

    /** A heading inside the lesson. */
    data class Heading(val text: String) : ReadBlock

    /** One short paragraph. */
    data class Paragraph(val text: String) : ReadBlock

    /** A list. One point per line. */
    data class Bullets(val items: List<String>) : ReadBlock

    /** A rule, a limit or a warning you must not miss. */
    data class Note(val text: String) : ReadBlock

    /** A number to memorize, with what it is for. */
    data class Numbers(val rows: List<Row>) : ReadBlock {
        data class Row(val value: String, val what: String)
    }
}

object Reading {

    /** Reads the "reading" array of a subsection. */
    fun parse(raw: List<Any?>): List<ReadBlock> = raw.mapNotNull { block(it) }

    private fun block(raw: Any?): ReadBlock? {
        val map = raw as? Map<*, *> ?: return null
        (map["h"] as? String)?.let { return ReadBlock.Heading(it) }
        (map["p"] as? String)?.let { return ReadBlock.Paragraph(it) }
        (map["note"] as? String)?.let { return ReadBlock.Note(it) }
        (map["ul"] as? List<*>)?.let { items ->
            val lines = items.mapNotNull { it as? String }.filter { it.isNotBlank() }
            return if (lines.isEmpty()) null else ReadBlock.Bullets(lines)
        }
        (map["numbers"] as? List<*>)?.let { rows ->
            val parsed = rows.mapNotNull { row ->
                val entry = row as? Map<*, *> ?: return@mapNotNull null
                val value = entry["value"] as? String ?: return@mapNotNull null
                val what = entry["what"] as? String ?: return@mapNotNull null
                ReadBlock.Numbers.Row(value, what)
            }
            return if (parsed.isEmpty()) null else ReadBlock.Numbers(parsed)
        }
        return null
    }

    /**
     * A lesson for a subsection whose prose is not written yet: its facts, in
     * order, with the must-memorize ones first. It says so, so you know you are
     * reading a fact list and not a lesson.
     */
    fun fromFacts(subsection: Subsection): List<ReadBlock> {
        if (subsection.facts.isEmpty()) return emptyList()
        val critical = subsection.criticalFacts
        val rest = subsection.facts.filterNot { it.critical }
        return buildList {
            add(ReadBlock.Note("A lesson is not written for this subsection yet. These are its facts."))
            if (critical.isNotEmpty()) {
                add(ReadBlock.Heading("Must memorize"))
                add(ReadBlock.Bullets(critical.map { it.text }))
            }
            if (rest.isNotEmpty()) {
                add(ReadBlock.Heading("Also know"))
                add(ReadBlock.Bullets(rest.map { it.text }))
            }
        }
    }

    /** How many words the lesson holds, for the reading time. */
    fun wordCount(blocks: List<ReadBlock>): Int = blocks.sumOf { block ->
        when (block) {
            is ReadBlock.Heading -> words(block.text)
            is ReadBlock.Paragraph -> words(block.text)
            is ReadBlock.Note -> words(block.text)
            is ReadBlock.Bullets -> block.items.sumOf { words(it) }
            is ReadBlock.Numbers -> block.rows.sumOf { words(it.value) + words(it.what) }
        }
    }

    private fun words(text: String): Int =
        text.split(' ', '\n', '\t').count { it.isNotBlank() }

    /** Minutes to read, at 200 words a minute, and never less than one. */
    fun minutes(blocks: List<ReadBlock>): Int =
        (wordCount(blocks) / 200.0).let { if (it < 1) 1 else Math.round(it).toInt().coerceAtLeast(1) }
}
