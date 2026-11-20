package com.cdlstudy.domain

/**
 * Suggests a mark for each fact from the words in your recall.
 *
 * This is a help, not a grade. It only tells you where to look in your own
 * text. You confirm or change every mark. The app always shows the suggestion
 * as a suggestion, because a keyword can appear in a wrong sentence.
 */
object BlurtMatcher {

    /** What the words in the recall show about one fact. */
    data class Suggestion(
        val factId: String,
        val mark: Mark,
        val found: List<String>,
        val missing: List<String>,
    ) {
        val hasKeywords: Boolean get() = found.isNotEmpty() || missing.isNotEmpty()
    }

    fun suggest(facts: List<Fact>, blurt: String): Map<String, Suggestion> {
        val text = normalize(blurt)
        return facts.associate { fact -> fact.id to suggestOne(fact, text) }
    }

    private fun suggestOne(fact: Fact, normalizedBlurt: String): Suggestion {
        val found = fact.keywords.filter { contains(normalizedBlurt, it) }
        val missing = fact.keywords.filterNot { contains(normalizedBlurt, it) }
        val mark = when {
            fact.keywords.isEmpty() -> Mark.MISS
            missing.isEmpty() -> Mark.HIT
            found.isEmpty() -> Mark.MISS
            else -> Mark.PARTIAL
        }
        return Suggestion(fact.id, mark, found, missing)
    }

    /** Matches whole words only, so "air" does not match "airbrakes" by halves. */
    fun contains(normalizedBlurt: String, keyword: String): Boolean {
        val needle = normalize(keyword)
        if (needle.isEmpty()) return false
        val padded = " $normalizedBlurt "
        return padded.contains(" $needle ")
    }

    /**
     * Lower case, punctuation to spaces, one space between words.
     *
     * A comma between two digits goes away first, so that "26,001" in your
     * recall matches the keyword "26001". Numbers carry the weight in this
     * material, and a comma must not hide one.
     */
    fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("(?<=\\d),(?=\\d)"), "")
            .map { if (it.isLetterOrDigit() || it == '%') it else ' ' }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
}
