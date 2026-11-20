package com.cdlstudy.domain

/**
 * Self-grading of a closed-book recall.
 *
 * The app shows no fact until you send your recall. Then you mark each fact
 * yourself. Nothing here reads your recall to decide a mark for you.
 * [BlurtMatcher] can suggest a mark, but you confirm every one.
 */

enum class Mark {
    /** You said the fact correctly. */
    HIT,

    /** You said part of it, or you said it with a wrong number or detail. */
    PARTIAL,

    /** You did not say it. */
    MISS,
    ;

    val weight: Double get() = when (this) {
        HIT -> 1.0
        PARTIAL -> 0.5
        MISS -> 0.0
    }
}

/**
 * The result of one graded recall.
 *
 * @param overall the percentage across all facts.
 * @param critical the percentage across the must-memorize facts only. This is
 *   the number to trust. The study plan grades on the major facts.
 */
data class RecallScore(
    val factCount: Int,
    val hits: Int,
    val partials: Int,
    val misses: Int,
    val overall: Int,
    val criticalCount: Int,
    val criticalMisses: Int,
    val critical: Int,
) {
    /** True when you are at the mastery level of the study plan. */
    val mastered: Boolean get() = critical >= MASTERY_PERCENT && overall >= PASS_PERCENT

    companion object {
        const val PASS_PERCENT = 80
        const val MASTERY_PERCENT = 90
    }
}

object Grading {

    /** An unmarked fact counts as a miss. You did not say it, so you did not know it. */
    fun markOf(marks: Map<String, Mark>, factId: String): Mark = marks[factId] ?: Mark.MISS

    fun score(facts: List<Fact>, marks: Map<String, Mark>): RecallScore {
        val all = facts.map { markOf(marks, it.id) }
        val criticalFacts = facts.filter { it.critical }
        val criticalMarks = criticalFacts.map { markOf(marks, it.id) }
        return RecallScore(
            factCount = facts.size,
            hits = all.count { it == Mark.HIT },
            partials = all.count { it == Mark.PARTIAL },
            misses = all.count { it == Mark.MISS },
            overall = percent(all),
            criticalCount = criticalFacts.size,
            criticalMisses = criticalMarks.count { it == Mark.MISS },
            critical = percent(criticalMarks),
        )
    }

    /** Zero facts means nothing to score. The app reports 0 and says so. */
    private fun percent(marks: List<Mark>): Int {
        if (marks.isEmpty()) return 0
        val earned = marks.sumOf { it.weight }
        return Math.round(100.0 * earned / marks.size).toInt()
    }

    /**
     * The cards to add after a recall: every card for a fact you missed or part
     * missed, and every card for a must-memorize fact. Duplicate questions are
     * removed, because the same card can serve more than one fact.
     */
    fun cardsToAdd(subsection: Subsection, marks: Map<String, Mark>): List<Card> {
        val wanted = subsection.facts.filter { fact ->
            fact.critical || markOf(marks, fact.id) != Mark.HIT
        }.map { it.id }.toSet()
        val chosen = subsection.cards.filter { it.factId == null || it.factId in wanted }
        return chosen.distinctBy { normalizeQuestion(it.question) }
    }

    /** Facts you must read again: a missed or part missed must-memorize fact. */
    fun factsToRelearn(subsection: Subsection, marks: Map<String, Mark>): List<Fact> =
        subsection.facts.filter { it.critical && markOf(marks, it.id) != Mark.HIT }

    fun normalizeQuestion(question: String): String =
        question.trim().lowercase().replace(Regex("\\s+"), " ")
}
