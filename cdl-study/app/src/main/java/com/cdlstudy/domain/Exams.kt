package com.cdlstudy.domain

import kotlin.random.Random

/**
 * The six exams. Each track is graded on its own, and the Vehicle Inspection
 * exam is never mixed with Basic Vehicle Control or On-Road Driving.
 */

/**
 * The default length of a full practice exam.
 *
 * WARNING: these three numbers (50, 25, 20) come from the study plan as common
 * practice targets. They are NOT verified against the Utah Driver License
 * Division. Check the current official numbers before you treat them as the
 * requirement. You can set any length in the app.
 */
object ExamStandards {
    const val PASS_PERCENT = 80
    const val MASTERY_PERCENT = 90

    fun defaultLength(track: Track): Int = when (track) {
        Track.GENERAL_KNOWLEDGE -> 50
        Track.AIR_BRAKES -> 25
        Track.COMBINATION -> 20
        Track.INSPECTION -> 25
        Track.BASIC_CONTROL -> 20
        Track.ON_ROAD -> 20
    }

    /** True for a track that also needs practice behind the wheel. */
    fun needsRoadPractice(track: Track): Boolean =
        track == Track.BASIC_CONTROL || track == Track.ON_ROAD
}

data class ExamResult(
    val track: Track,
    val total: Int,
    val correct: Int,
    val percent: Int,
    val missed: List<ExamItem>,
) {
    val passed: Boolean get() = percent >= ExamStandards.PASS_PERCENT
    val mastered: Boolean get() = percent >= ExamStandards.MASTERY_PERCENT

    /** The facts behind the missed questions. These are the weak areas. */
    val weakFactIds: List<String> get() = missed.mapNotNull { it.factId }.distinct()
}

object Exams {

    /**
     * Picks the questions for one attempt.
     *
     * A question on a weak fact is twice as likely to appear. The order is
     * random, so a repeat test is not the same test. The seed makes the choice
     * repeatable in a test.
     */
    fun build(
        pool: List<ExamItem>,
        length: Int,
        weakFactIds: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): List<ExamItem> {
        if (pool.isEmpty() || length <= 0) return emptyList()
        val weighted = pool.flatMap { item ->
            if (item.factId != null && item.factId in weakFactIds) listOf(item, item) else listOf(item)
        }
        val chosen = LinkedHashSet<ExamItem>()
        val bag = weighted.shuffled(random)
        for (item in bag) {
            if (chosen.size >= length) break
            chosen.add(item)
        }
        return chosen.toList()
    }

    fun grade(track: Track, items: List<ExamItem>, answers: Map<String, Int>): ExamResult {
        val missed = items.filter { answers[it.id] != it.answerIndex }
        val correct = items.size - missed.size
        val percent =
            if (items.isEmpty()) 0 else Math.round(100.0 * correct / items.size).toInt()
        return ExamResult(track, items.size, correct, percent, missed)
    }

    /** The cards to add after an exam: one per missed question that has a card. */
    fun cardsForMissed(content: Content, result: ExamResult): List<Card> {
        val cards = result.missed.mapNotNull { item ->
            val factId = item.factId ?: return@mapNotNull null
            val fact = content.fact(factId) ?: return@mapNotNull null
            content.sections
                .flatMap { it.subsections }
                .flatMap { it.cards }
                .firstOrNull { it.factId == fact.id }
                ?: Card(question = item.question, answer = item.answerText, factId = fact.id)
        }
        return cards.distinctBy { Grading.normalizeQuestion(it.question) }
    }
}
