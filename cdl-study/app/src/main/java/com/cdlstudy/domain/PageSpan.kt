package com.cdlstudy.domain

/**
 * A run of pages in the manual PDF, counted as the PDF counts them, from 1.
 *
 * The manual prints its own page labels, such as 5-3. Those labels do not match
 * the PDF page numbers, because the covers and the blank pages come first. The
 * app needs the PDF numbers to show the pages, and the printed labels to tell
 * you where you are. The content files hold both.
 */
data class PageSpan(val first: Int, val last: Int) {

    init {
        require(first >= 1) { "A page number starts at 1, not $first" }
        require(last >= first) { "The last page $last is before the first page $first" }
    }

    val pages: List<Int> get() = (first..last).toList()
    val count: Int get() = last - first + 1

    override fun toString(): String = if (first == last) "$first" else "$first-$last"

    companion object {
        /** Reads "12" or "12-15". Returns null for nothing, and throws on rubbish. */
        fun parse(text: String?): PageSpan? {
            val trimmed = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val parts = trimmed.split("-", "–")
            val numbers = parts.map { part ->
                part.trim().toIntOrNull()
                    ?: throw IllegalArgumentException("'$trimmed' is not a page or a page range")
            }
            return when (numbers.size) {
                1 -> PageSpan(numbers[0], numbers[0])
                2 -> PageSpan(numbers[0], numbers[1])
                else -> throw IllegalArgumentException("'$trimmed' is not a page or a page range")
            }
        }
    }
}
