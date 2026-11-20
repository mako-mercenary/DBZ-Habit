package com.cdlstudy.domain

/**
 * Anki export. The format is the one the study plan asks for: one Basic card
 * per line, question then a tab then the answer. No header row, no numbering,
 * no extra columns.
 *
 * Anki reads one card per line, so a tab or a line break inside a question
 * would break the file. Each one becomes a space.
 */
object Tsv {

    fun of(cards: List<Card>): String =
        cards.joinToString("\n") { "${clean(it.question)}\t${clean(it.answer)}" }

    fun clean(text: String): String =
        text.replace('\t', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex(" +"), " ")
            .trim()

    /** A file name with the section and the date, such as cdl-5-air-brakes-2026-11-20.tsv */
    fun fileName(label: String, isoDate: String): String {
        val slug = label.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
        return "cdl-$slug-$isoDate.tsv"
    }
}
