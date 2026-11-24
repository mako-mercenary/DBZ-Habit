package com.cdlstudy.domain

import kotlin.random.Random

/**
 * The numbers drill.
 *
 * Numbers are what you lose first and what the test asks for most directly.
 * 26,001 pounds. 4/32 of an inch. 4 psi in one minute. 15 feet, no closer.
 *
 * A flashcard shows you the number and asks whether you knew it, which is
 * recognition. This blanks the number out of the fact and makes you type it,
 * which is recall. The rest of the sentence stays, so you still know which
 * number is wanted.
 */
object NumberDrill {

    const val BLANK = "_____"

    data class Item(
        val factId: String,
        val subsectionId: String,
        /** The fact with every quantity blanked out. */
        val prompt: String,
        /** The numbers you must type, in the order they appear. */
        val wanted: List<String>,
        /** The whole fact, shown after you answer. */
        val answer: String,
        val page: String?,
    ) {
        val blanks: Int get() = wanted.size
    }

    private const val UNIT =
        "(?:psi|lbs?|pounds?|ft|foot|feet|inch(?:es)?|mph|miles?|seconds?|minutes?|hours?|" +
            "days?|weeks?|months?|years?|gallons?|degrees?|percent|rpm|times|millimeters?|mm|" +
            "dollars?|axles?|placards?|tie-?downs?|passengers?|triangles?|flares?|fuses?|" +
            "parts?|exercises?|looks?|pull-?ups?|points?|tracks?|endorsements?|steps?)"

    /**
     * Matches a quantity, and nothing else that holds a digit.
     *
     * A number on its own is not a quantity: "study sections 1, 2, 3 and 5" is
     * a list, and blanking it out teaches nothing. A number earns a blank when
     * a unit follows it, when it is a fraction, a decimal or a percentage, when
     * it is a year, or when "or more" follows it.
     */
    private val QUANTITY = Regex(
        "(?<![\\w/.])(" +
            "\\$[\\d,]+" +
            "|\\d+/\\d+" +
            "|\\d[\\d,]*\\.\\d+\\s*%" +
            "|\\d[\\d,]*\\s*%" +
            "|\\d[\\d,]*\\.\\d+" +
            "|\\d[\\d,]*\\s*(?:to|-)\\s*\\d[\\d,]*\\s*$UNIT" +
            "|\\d[\\d,]*\\s*$UNIT" +
            "|(?:19|20)\\d\\d" +
            "|\\d[\\d,]*\\s+or\\s+(?:more|less|fewer)" +
            ")",
        RegexOption.IGNORE_CASE,
    )

    /** The quantity phrases in [text], in the order they appear. */
    fun quantities(text: String): List<String> =
        QUANTITY.findAll(text).map { it.groupValues[1].trim() }.toList()

    /** The same text with every quantity replaced by a blank. */
    fun cloze(text: String): String = QUANTITY.replace(text) { match ->
        // The unit stays outside the blank, so you type the number, not the
        // unit: "no more than _____ psi in one minute".
        val whole = match.groupValues[1]
        val number = NUMBER.find(whole)?.value ?: return@replace BLANK
        whole.replaceFirst(number, BLANK)
    }

    // A comma only belongs between digits. Without that, "3, 4" gives the
    // token "3," and nothing matches it.
    private val NUMBER = Regex("\\$?\\d+(?:,\\d{3})*(?:[./]\\d+)?")

    /**
     * The number tokens in a piece of text, with the commas and the dollar
     * sign taken off, and a number written as a word turned into its digits.
     */
    fun digits(text: String): List<String> {
        val words = NUMBER_WORDS.entries.fold(text.lowercase()) { acc, (word, digit) ->
            acc.replace(Regex("\\b$word\\b"), digit)
        }
        return NUMBER.findAll(words.replace(Regex("(?<=\\d),(?=\\d)"), ""))
            .map { it.value.removePrefix("$") }
            .toList()
    }

    private val NUMBER_WORDS = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
        "eleven" to "11", "twelve" to "12", "fifteen" to "15", "sixteen" to "16",
        "twenty" to "20", "thirty" to "30", "forty" to "40", "fifty" to "50",
        "sixty" to "60", "ninety" to "90", "hundred" to "100", "half" to "1/2",
    )

    /** Every drillable fact in one subsection. */
    fun itemsOf(subsection: Subsection): List<Item> =
        subsection.facts.filter { it.critical }.mapNotNull { fact ->
            val wanted = quantities(fact.text).flatMap { digits(it) }.distinct()
            if (wanted.isEmpty()) return@mapNotNull null
            Item(
                factId = fact.id,
                subsectionId = subsection.id,
                prompt = cloze(fact.text),
                wanted = wanted,
                answer = fact.text,
                page = fact.page,
            )
        }

    /** The whole pool, every must-memorize fact that holds a number. */
    fun pool(content: Content): List<Item> =
        content.sections.flatMap { section ->
            section.subsections.flatMap { itemsOf(it) }
        }

    /**
     * One round. A fact you have missed before comes up twice as often, the
     * same weighting the exams use.
     */
    fun build(
        content: Content,
        random: Random,
        length: Int = DEFAULT_LENGTH,
        weakFactIds: Set<String> = emptySet(),
    ): List<Item> {
        val all = pool(content)
        if (all.isEmpty()) return emptyList()
        val weighted = all + all.filter { it.factId in weakFactIds }
        val picked = LinkedHashSet<Item>()
        val bag = weighted.toMutableList()
        while (picked.size < length && bag.isNotEmpty()) {
            picked.add(bag.removeAt(random.nextInt(bag.size)))
        }
        return picked.toList()
    }

    const val DEFAULT_LENGTH = 15

    /**
     * Marks what you typed.
     *
     * Every number must be there for a hit. A number is a number: 4 psi and
     * 40 psi are not nearly the same answer, so there is no partial credit for
     * being close. There is partial credit for getting one of two numbers,
     * because that is one fact half known.
     */
    fun check(typed: String, item: Item): Mark {
        val said = digits(typed)
        val got = item.wanted.count { want -> matches(want, said) }
        return when {
            got == item.wanted.size -> Mark.HIT
            got == 0 -> Mark.MISS
            else -> Mark.PARTIAL
        }
    }

    /**
     * A fraction counts however you type it. "4/32" and "4 32" are the same
     * answer, and nobody should lose a mark to a slash.
     */
    private fun matches(wanted: String, said: List<String>): Boolean {
        if (wanted in said) return true
        if ('/' !in wanted) return false
        val parts = wanted.split('/')
        val at = said.indexOf(parts[0])
        return at >= 0 && at + 1 < said.size && said[at + 1] == parts[1]
    }
}
