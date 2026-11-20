package com.cdlstudy.domain

/**
 * The study content: the manual turned into data.
 *
 * One file per section, in assets/content. The facts are the centre of it.
 * A fact is the checklist item you grade your recall against, the source of a
 * flashcard, and the tag that an exam question points back to. That tag is how
 * the app finds a weak area.
 */

/** The six assessment tracks from the study plan. Each one is a separate exam. */
enum class Track(val id: String, val label: String) {
    GENERAL_KNOWLEDGE("general_knowledge", "General Knowledge"),
    AIR_BRAKES("air_brakes", "Air Brakes"),
    COMBINATION("combination", "Combination Vehicles"),
    INSPECTION("inspection", "Vehicle Inspection"),
    BASIC_CONTROL("basic_control", "Basic Vehicle Control"),
    ON_ROAD("on_road", "On-Road Driving");

    companion object {
        fun of(id: String): Track =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Unknown track '$id'")
    }
}

/**
 * One item of manual information you must be able to say from memory.
 *
 * @param critical true for a must-memorize rule, number or procedure. A missed
 *   critical fact always becomes a flashcard.
 * @param keywords words or numbers that show the fact is present in your recall.
 *   The app uses them only to suggest a mark. You confirm every mark yourself.
 */
data class Fact(
    val id: String,
    val text: String,
    val critical: Boolean = false,
    val page: String? = null,
    val keywords: List<String> = emptyList(),
)

/** A flashcard for Anki: one question, one short answer, one fact. */
data class Card(
    val question: String,
    val answer: String,
    val factId: String? = null,
)

/**
 * A practice exam question. The questions are original, written from the manual.
 * They are not copies of the confidential state test.
 */
data class ExamItem(
    val id: String,
    val question: String,
    val choices: List<String>,
    val answerIndex: Int,
    val why: String? = null,
    val factId: String? = null,
    val page: String? = null,
) {
    val answerText: String get() = choices[answerIndex]
}

/** A subsection, such as 1.1. It is one turn of the study loop. */
data class Subsection(
    val id: String,
    val title: String,
    val manualPages: String? = null,
    val facts: List<Fact> = emptyList(),
    val cards: List<Card> = emptyList(),
    val exam: List<ExamItem> = emptyList(),
) {
    val criticalFacts: List<Fact> get() = facts.filter { it.critical }
    fun fact(id: String): Fact? = facts.firstOrNull { it.id == id }
}

/** A section of the manual, such as Section 5, Air Brakes. */
data class Section(
    val number: Int,
    val title: String,
    val track: Track,
    val readingPages: Int = 0,
    val subsections: List<Subsection> = emptyList(),
    /** Questions that test the whole section, not one subsection. */
    val exam: List<ExamItem> = emptyList(),
    /** True while the content is a sample. The app shows a warning. */
    val placeholder: Boolean = false,
) {
    val factCount: Int get() = subsections.sumOf { it.facts.size }
    val cardCount: Int get() = subsections.sumOf { it.cards.size }
    val examCount: Int get() = exam.size + subsections.sumOf { it.exam.size }
}

/** Every section the app has loaded. */
class Content(val sections: List<Section>) {

    val isPlaceholderOnly: Boolean get() = sections.all { it.placeholder }

    fun section(number: Int): Section? = sections.firstOrNull { it.number == number }

    fun subsection(id: String): Subsection? =
        sections.firstNotNullOfOrNull { s -> s.subsections.firstOrNull { it.id == id } }

    fun sectionOf(subsectionId: String): Section? =
        sections.firstOrNull { s -> s.subsections.any { it.id == subsectionId } }

    /** Every exam question for one track, section questions first. */
    fun examPool(track: Track): List<ExamItem> =
        sections.filter { it.track == track }
            .flatMap { it.exam + it.subsections.flatMap { sub -> sub.exam } }

    fun fact(factId: String): Fact? =
        sections.firstNotNullOfOrNull { s ->
            s.subsections.firstNotNullOfOrNull { it.fact(factId) }
        }
}

/** Reads a section file. The format is documented in docs/CONTENT_FORMAT.md. */
object ContentReader {

    fun readSection(json: String): Section {
        val root = MiniJson.parse(json) as? Map<*, *>
            ?: throw IllegalArgumentException("A section file must hold a JSON object")
        return Section(
            number = root.int("section"),
            title = root.str("title"),
            track = Track.of(root.str("track")),
            readingPages = root.intOrNull("readingPages") ?: 0,
            subsections = root.list("subsections").map { readSubsection(it) },
            exam = root.list("exam").mapIndexed { i, item ->
                readExamItem(item, "s${root.int("section")}.e${i + 1}")
            },
            placeholder = root.boolOrNull("placeholder") ?: false,
        )
    }

    fun readAll(files: List<String>): Content =
        Content(files.map { readSection(it) }.sortedBy { it.number })

    private fun readSubsection(raw: Any?): Subsection {
        val map = raw as? Map<*, *>
            ?: throw IllegalArgumentException("A subsection must be a JSON object")
        val id = map.str("id")
        return Subsection(
            id = id,
            title = map.str("title"),
            manualPages = map.strOrNull("manualPages"),
            facts = map.list("facts").mapIndexed { i, f -> readFact(f, "$id.${i + 1}") },
            cards = map.list("cards").map { readCard(it) },
            exam = map.list("exam").mapIndexed { i, e -> readExamItem(e, "$id.e${i + 1}") },
        )
    }

    private fun readFact(raw: Any?, fallbackId: String): Fact {
        val map = raw as? Map<*, *>
            ?: throw IllegalArgumentException("A fact must be a JSON object")
        return Fact(
            id = map.strOrNull("id") ?: fallbackId,
            text = map.str("text"),
            critical = map.boolOrNull("critical") ?: false,
            page = map.strOrNull("page"),
            keywords = map.list("keywords").map { it.toString() },
        )
    }

    private fun readCard(raw: Any?): Card {
        val map = raw as? Map<*, *>
            ?: throw IllegalArgumentException("A card must be a JSON object")
        return Card(
            question = map.str("q"),
            answer = map.str("a"),
            factId = map.strOrNull("factId"),
        )
    }

    private fun readExamItem(raw: Any?, fallbackId: String): ExamItem {
        val map = raw as? Map<*, *>
            ?: throw IllegalArgumentException("An exam question must be a JSON object")
        val choices = map.list("choices").map { it.toString() }
        val answer = map.int("answer")
        if (choices.size < 2) {
            throw IllegalArgumentException("Question ${map.strOrNull("id") ?: fallbackId} needs two choices or more")
        }
        if (answer !in choices.indices) {
            throw IllegalArgumentException("Question ${map.strOrNull("id") ?: fallbackId} has an answer outside its choices")
        }
        return ExamItem(
            id = map.strOrNull("id") ?: fallbackId,
            question = map.str("q"),
            choices = choices,
            answerIndex = answer,
            why = map.strOrNull("why"),
            factId = map.strOrNull("factId"),
            page = map.strOrNull("page"),
        )
    }

    private fun Map<*, *>.str(key: String): String =
        strOrNull(key) ?: throw IllegalArgumentException("Missing text for '$key'")

    private fun Map<*, *>.strOrNull(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

    private fun Map<*, *>.int(key: String): Int =
        intOrNull(key) ?: throw IllegalArgumentException("Missing number for '$key'")

    private fun Map<*, *>.intOrNull(key: String): Int? = when (val v = this[key]) {
        is Long -> v.toInt()
        is Double -> v.toInt()
        is Int -> v
        else -> null
    }

    private fun Map<*, *>.boolOrNull(key: String): Boolean? = this[key] as? Boolean

    private fun Map<*, *>.list(key: String): List<Any?> = (this[key] as? List<*>) ?: emptyList()
}
