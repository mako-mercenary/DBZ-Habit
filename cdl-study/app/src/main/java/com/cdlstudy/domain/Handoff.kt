package com.cdlstudy.domain

/**
 * Builds the progress summary that the study plan asks for: a short text you
 * can carry to any chat or any new account, with what you finished, what you
 * are weak at, and your scores.
 */
object Handoff {

    data class SubsectionLine(
        val subsectionId: String,
        val title: String,
        val status: String,
        val bestCritical: Int,
        val timesRecalled: Int,
    )

    data class ExamLine(val track: Track, val attempts: Int, val best: Int)

    data class WeakLine(val factId: String, val text: String, val weight: Int)

    fun build(
        isoDate: String,
        subsections: List<SubsectionLine>,
        exams: List<ExamLine>,
        weak: List<WeakLine>,
        cardsMade: Int,
        cardsPending: Int,
    ): String = buildString {
        appendLine("UTAH CLASS A CDL - PROGRESS SUMMARY")
        appendLine("Date: $isoDate")
        appendLine()
        appendLine("Method: read, closed-book recall, self-grade against the manual facts,")
        appendLine("Anki cards from the misses, then a separate exam for each of the six tracks.")
        appendLine()
        appendLine("CARDS")
        appendLine("Made: $cardsMade. Waiting for export: $cardsPending.")
        appendLine()
        appendLine("SUBSECTIONS")
        if (subsections.isEmpty()) {
            appendLine("None recalled yet.")
        } else {
            subsections.forEach { line ->
                appendLine(
                    "${line.subsectionId} ${line.title} - ${line.status}, " +
                        "best ${line.bestCritical}% on must-memorize facts, " +
                        "${line.timesRecalled} recalls"
                )
            }
        }
        appendLine()
        appendLine("EXAMS")
        if (exams.isEmpty()) {
            appendLine("No exam taken yet.")
        } else {
            exams.forEach { line ->
                appendLine("${line.track.label} - ${line.attempts} attempts, best ${line.best}%")
            }
        }
        appendLine()
        appendLine("WEAK FACTS")
        if (weak.isEmpty()) {
            appendLine("None recorded yet.")
        } else {
            weak.forEach { line -> appendLine("- ${line.text} (missed weight ${line.weight})") }
        }
    }
}
