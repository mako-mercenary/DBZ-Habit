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

    /**
     * Matches whole words only, so "air" does not match "airbrakes" by halves.
     *
     * A keyword may hold choices, separated by a vertical bar, and any one of
     * them counts: "sign|signal" matches either word.
     */
    fun contains(normalizedBlurt: String, keyword: String): Boolean {
        val padded = " $normalizedBlurt "
        return keyword.split('|').any { choice ->
            val needle = normalize(choice)
            needle.isNotEmpty() && plurals(needle).any { padded.contains(" $it ") }
        }
    }

    /**
     * The same phrase with and without a plural s on its last word.
     *
     * You write "ten years" and the keyword says "10 year". One missing letter
     * must not turn a fact you knew into a fact you missed.
     */
    private fun plurals(needle: String): List<String> =
        if (needle.endsWith("s")) listOf(needle, needle.dropLast(1))
        else listOf(needle, needle + "s")

    /**
     * A number written as a word is the same number.
     *
     * You write "three seconds" and the keyword says "3 seconds". Both become
     * "3 seconds" here, so the suggestion is right either way. Numbers carry
     * the weight in this material, and a spelled-out number must not be missed.
     */
    private val NUMBER_WORDS = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
        "ten" to "10", "eleven" to "11", "twelve" to "12", "fifteen" to "15",
        "twenty" to "20", "thirty" to "30", "forty" to "40", "fifty" to "50",
        "sixty" to "60", "ninety" to "90", "hundred" to "100",
    )

    /**
     * Lower case, punctuation to spaces, one space between words.
     *
     * Three things happen to numbers first, because numbers are the facts here:
     *
     * - a comma between two digits goes away, so "26,001" matches "26001";
     * - a full stop between two digits stays, so "0.04" stays one number
     *   instead of becoming "0 04";
     * - a number written as a word becomes its digits, so "three" matches "3".
     */
    fun normalize(text: String): String {
        val cleaned = text.lowercase()
            .replace(Regex("(?<=\\d),(?=\\d)"), "")
            .replace(Regex("(?<=\\d)\\.(?=\\d)"), "\u0001")
            // A per cent sign is a word of its own, so the keyword "0.04"
            // still finds "0.04%" in your recall.
            .map { if (it.isLetterOrDigit() || it == '\u0001') it else if (it == '%') '\u0002' else ' ' }
            .joinToString("")
            .replace("\u0001", ".")
            .replace("\u0002", " % ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return cleaned
        return cleaned.split(' ').joinToString(" ") { word -> NUMBER_WORDS[word] ?: word }
    }
}
