package com.cdlstudy.domain

/**
 * A second opinion on a closed-book recall, from Claude.
 *
 * The checklist you mark yourself cannot catch one thing: something you said
 * that is WRONG. A keyword can be present in a sentence that has the number
 * backwards. This asks a model to read your words against the same fact list
 * and say what it sees.
 *
 * It is off until you put in your own API key. What leaves the phone is your
 * recall and this app's own fact text. The manual never leaves: the app does
 * not hold it.
 *
 * Everything here is pure, so it is tested without a network.
 */
object AiGrading {

    /** What the model is asked to return for one fact. */
    data class Judgement(val factId: String, val mark: Mark, val why: String)

    data class Opinion(
        val marks: List<Judgement>,
        /** Things you said that are wrong, in the model's words. */
        val errors: List<String>,
        val note: String,
    ) {
        fun markOf(factId: String): Mark? = marks.firstOrNull { it.factId == factId }?.mark
        fun whyOf(factId: String): String? = marks.firstOrNull { it.factId == factId }?.why

        /** Facts where the model disagrees with the marks you gave. */
        fun disagreements(yours: Map<String, Mark>): List<Judgement> =
            marks.filter { it.mark != (yours[it.factId] ?: Mark.MISS) }
    }

    class BadReply(message: String) : Exception(message)

    const val SYSTEM = """
You grade a closed-book recall for a Utah Class A CDL student.

You are given the facts of one subsection of the driver handbook, and what the
student wrote from memory. For each fact, decide:

HIT      the student said this fact correctly.
PARTIAL  the student said part of it, or said it with a wrong number or detail.
MISS     the student did not say it.

Be strict about numbers, limits and procedures. A number that is wrong is not a
HIT, it is a PARTIAL at best, and you must say so. A fact the student did not
mention at all is a MISS, not a PARTIAL.

Also list, separately, anything the student stated that is WRONG, even if it is
not one of the facts. That is the thing a checklist cannot catch, and it is the
most useful part of your answer.

Write every "why" and every error in one short sentence, plainly, as you would
say it to the student. Do not praise. Do not pad.
"""

    /** The user turn: the facts, then the recall. */
    fun prompt(subsection: Subsection, blurt: String): String = buildString {
        append("Subsection ${subsection.id}: ${subsection.title}\n\n")
        append("THE FACTS\n")
        subsection.facts.forEach { fact ->
            append("- id=${fact.id}")
            if (fact.critical) append(" [must memorize]")
            append(": ${fact.text}\n")
        }
        append("\nWHAT THE STUDENT WROTE\n")
        append(blurt.trim().ifEmpty { "(nothing)" })
        append("\n")
    }

    /** The JSON schema the reply must match. */
    fun schema(): String = """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["marks", "errors", "note"],
          "properties": {
            "marks": {
              "type": "array",
              "items": {
                "type": "object",
                "additionalProperties": false,
                "required": ["factId", "mark", "why"],
                "properties": {
                  "factId": { "type": "string" },
                  "mark": { "type": "string", "enum": ["HIT", "PARTIAL", "MISS"] },
                  "why": { "type": "string" }
                }
              }
            },
            "errors": { "type": "array", "items": { "type": "string" } },
            "note": { "type": "string" }
          }
        }
    """.trimIndent()

    /**
     * Reads the reply.
     *
     * A fact the model did not mention is left out, not guessed at. A mark it
     * invents for a fact that is not in the subsection is dropped: it cannot
     * grade something that was never asked.
     */
    fun parse(text: String, subsection: Subsection): Opinion {
        val root = runCatching { MiniJson.parse(text) as? Map<*, *> }.getOrNull()
            ?: throw BadReply("The reply was not JSON. Try again.")
        val known = subsection.facts.map { it.id }.toSet()

        val marks = (root["marks"] as? List<*>).orEmpty().mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val factId = map["factId"] as? String ?: return@mapNotNull null
            if (factId !in known) return@mapNotNull null
            val mark = runCatching { Mark.valueOf((map["mark"] as? String).orEmpty()) }.getOrNull()
                ?: return@mapNotNull null
            Judgement(factId, mark, (map["why"] as? String).orEmpty().trim())
        }.distinctBy { it.factId }

        if (marks.isEmpty()) throw BadReply("The reply graded none of the facts. Try again.")

        return Opinion(
            marks = marks,
            errors = (root["errors"] as? List<*>).orEmpty()
                .mapNotNull { (it as? String)?.trim() }.filter { it.isNotEmpty() },
            note = (root["note"] as? String).orEmpty().trim(),
        )
    }
}
