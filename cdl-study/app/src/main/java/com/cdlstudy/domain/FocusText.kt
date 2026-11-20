package com.cdlstudy.domain

/**
 * Focus text: the first letters of each word in bold, so the eye lands on the
 * start of the word and fills in the rest. This is the bionic-style effect.
 *
 * Research on whether it makes you read faster is mixed, so it is a preference,
 * not a feature that is on for everybody. The app lets you switch it off and
 * set how strong it is.
 *
 * This object only works out WHERE the bold goes. The screen draws it.
 */
object FocusText {

    /** How much of each word goes bold. */
    enum class Strength(val label: String, val ratio: Double) {
        LIGHT("Light", 0.33),
        MEDIUM("Medium", 0.5),
        STRONG("Strong", 0.66),
    }

    /** One piece of a word: the bold start, then the rest. */
    data class Span(val start: Int, val end: Int)

    /**
     * The ranges to draw in bold, for one line of text.
     *
     * A word that holds a digit goes bold whole. In this material a number is
     * the fact, so half of "26,001" in bold would hide the part that matters.
     */
    fun boldRanges(text: String, strength: Strength): List<Span> {
        val spans = mutableListOf<Span>()
        var index = 0
        while (index < text.length) {
            if (!startsWord(text, index)) {
                index++
                continue
            }
            var end = index + 1
            while (end < text.length && continuesWord(text, end)) end++
            val word = text.substring(index, end)
            val bold = boldLength(word, strength)
            if (bold > 0) spans.add(Span(index, index + bold))
            index = end
        }
        return spans
    }

    /** How many letters of this word go bold. */
    fun boldLength(word: String, strength: Strength): Int {
        if (word.isEmpty()) return 0
        if (word.any { it.isDigit() }) return word.length
        if (word.length <= 3) return if (strength == Strength.STRONG) 2.coerceAtMost(word.length) else 1
        var bold = Math.round(word.length * strength.ratio).toInt().coerceIn(1, word.length - 1)
        // The bold run must not end on a hyphen or an apostrophe, so
        // "out-of-service" bolds "out-of" and not "out-of-".
        while (bold > 1 && !word[bold - 1].isLetterOrDigit()) bold--
        return bold
    }

    private fun startsWord(text: String, at: Int): Boolean = text[at].isLetterOrDigit()

    /**
     * True for a character that belongs to the word being read.
     *
     * A letter or a digit always does. An apostrophe or a hyphen does when a
     * letter or a digit follows it, so "driver's" and "out-of-service" stay
     * whole. A comma or a full stop does only between two digits, so "26,001"
     * and "0.04" stay whole while a sentence still ends.
     */
    private fun continuesWord(text: String, at: Int): Boolean {
        val c = text[at]
        if (c.isLetterOrDigit()) return true
        val next = text.getOrNull(at + 1) ?: return false
        return when (c) {
            '\'', '\u2019', '-' -> next.isLetterOrDigit()
            ',', '.' -> text[at - 1].isDigit() && next.isDigit()
            else -> false
        }
    }
}
