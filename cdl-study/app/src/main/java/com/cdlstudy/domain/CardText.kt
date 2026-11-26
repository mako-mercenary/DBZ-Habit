package com.cdlstudy.domain

/**
 * Tidies the text of a card before it is stored.
 *
 * A question or an answer is one line. A tab or a line break in the middle of
 * one makes a mess of every list that shows it, and nothing in this material
 * needs two lines, so each becomes a space.
 *
 * This was once part of an Anki export, which is gone: the app keeps its own
 * schedule now. The tidying is still worth doing.
 */
object CardText {

    fun clean(text: String): String =
        text.replace('\t', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex(" +"), " ")
            .trim()
}
