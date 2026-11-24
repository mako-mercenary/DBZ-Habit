package com.cdlstudy.domain

import kotlin.random.Random

/**
 * A procedure you are scored on performing in order.
 *
 * The air brake check and the pre-trip inspection are not graded on whether
 * you know the parts. They are graded on your doing them, in order, leaving
 * none out. A fact list cannot train that: it has no order, and a checklist
 * you read in order teaches you the order of the page, not of the work.
 *
 * So the drill shuffles the steps and you put them back. There is no partial
 * credit. The examiner does not give you nine tenths of the air brake check.
 */
data class Step(
    val text: String,
    /** The fact this step comes from, so a miss can become a card. */
    val factId: String? = null,
)

data class Procedure(
    val id: String,
    val title: String,
    /** Why the order matters, in one sentence. Shown before you start. */
    val why: String,
    val steps: List<Step>,
    /** The subsection to read when you get it wrong. */
    val subsectionId: String? = null,
) {
    val length: Int get() = steps.size
}

object StepOrder {

    /**
     * How an attempt did.
     *
     * [inPlace] is how many steps are in the right position. [passed] is only
     * true when every one is, which is the whole point.
     */
    data class Result(
        val given: List<Step>,
        val correct: List<Step>,
        val wrongPositions: Set<Int>,
    ) {
        val total: Int get() = correct.size
        val inPlace: Int get() = total - wrongPositions.size
        val passed: Boolean get() = wrongPositions.isEmpty()
        val percent: Int get() = if (total == 0) 0 else 100 * inPlace / total

        /** The steps that were out of place, for the cards to come. */
        val missedFactIds: List<String>
            get() = wrongPositions.mapNotNull { correct.getOrNull(it)?.factId }.distinct()
    }

    fun score(given: List<Step>, procedure: Procedure): Result {
        val correct = procedure.steps
        val wrong = correct.indices.filter { i -> given.getOrNull(i)?.text != correct[i].text }
        return Result(given, correct, wrong.toSet())
    }

    /**
     * The steps in an order that is not the right one.
     *
     * A shuffle that happens to come out correct would hand you the answer, so
     * it is reshuffled. A one-step procedure cannot be shuffled and is given
     * back as it is.
     */
    fun shuffle(procedure: Procedure, random: Random): List<Step> {
        if (procedure.length < 2) return procedure.steps
        repeat(20) {
            val tried = procedure.steps.shuffled(random)
            if (tried.map { it.text } != procedure.steps.map { it.text }) return tried
        }
        // Twenty shuffles all came out right, which needs a two-step procedure
        // and remarkable luck. Swap the first two and be done.
        return procedure.steps.toMutableList().also { it[0] = it[1].also { _ -> it[1] = it[0] } }
    }
}
