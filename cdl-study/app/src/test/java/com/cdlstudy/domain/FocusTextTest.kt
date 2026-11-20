package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTextTest {

    private fun bolded(text: String, strength: FocusText.Strength = FocusText.Strength.MEDIUM): String =
        FocusText.boldRanges(text, strength)
            .joinToString(" ") { text.substring(it.start, it.end) }

    @Test
    fun `half of a word goes bold at medium strength`() {
        assertEquals(3, FocusText.boldLength("brakes", FocusText.Strength.MEDIUM))
        assertEquals(2, FocusText.boldLength("brakes", FocusText.Strength.LIGHT))
        assertEquals(4, FocusText.boldLength("brakes", FocusText.Strength.STRONG))
    }

    @Test
    fun `a short word keeps one bold letter, so it is not all bold`() {
        assertEquals(1, FocusText.boldLength("the", FocusText.Strength.MEDIUM))
        assertEquals(1, FocusText.boldLength("a", FocusText.Strength.MEDIUM))
        assertEquals(1, FocusText.boldLength("psi", FocusText.Strength.LIGHT))
    }

    @Test
    fun `at least one letter stays unbolded, so the word still has a shape`() {
        listOf("air", "brake", "governor", "disqualification").forEach { word ->
            FocusText.Strength.entries.forEach { strength ->
                val bold = FocusText.boldLength(word, strength)
                assertTrue("$word at $strength bolded all of it", bold < word.length)
            }
        }
    }

    @Test
    fun `a number goes bold whole, because the number is the fact`() {
        assertEquals("26,001", bolded("26,001"))
        assertEquals("0.04", bolded("0.04"))
        assertEquals("125", bolded("125"))
    }

    @Test
    fun `a number with a comma is one word, not two`() {
        assertEquals(2, FocusText.boldRanges("26,001 pounds", FocusText.Strength.MEDIUM).size)
        assertEquals("26,001 pou", bolded("26,001 pounds"))
    }

    @Test
    fun `a sentence ends, so a full stop does not join two words`() {
        // "Stop" bolds two letters, "Now" is short so it bolds one.
        assertEquals("St N", bolded("Stop. Now"))
    }

    @Test
    fun `a hyphen and an apostrophe hold a word together`() {
        assertEquals("out-of", bolded("out-of-service"))
        assertEquals("driv", bolded("driver's"))
    }

    @Test
    fun `the bold run never ends on a hyphen or an apostrophe`() {
        listOf("out-of-service", "driver's", "pre-trip", "o'clock").forEach { word ->
            FocusText.Strength.entries.forEach { strength ->
                val bold = FocusText.boldLength(word, strength)
                assertTrue(
                    "$word at $strength ends bold on '${word[bold - 1]}'",
                    word[bold - 1].isLetterOrDigit(),
                )
            }
        }
    }

    @Test
    fun `punctuation is never bolded by itself`() {
        FocusText.boldRanges("Stop! (now) \"yes\"", FocusText.Strength.STRONG).forEach { span ->
            val piece = "Stop! (now) \"yes\"".substring(span.start, span.end)
            assertTrue("'$piece' is punctuation", piece.any { it.isLetterOrDigit() })
        }
    }

    @Test
    fun `empty text gives nothing to bold`() {
        assertTrue(FocusText.boldRanges("", FocusText.Strength.MEDIUM).isEmpty())
        assertTrue(FocusText.boldRanges("   ...  ", FocusText.Strength.MEDIUM).isEmpty())
    }

    @Test
    fun `the ranges stay inside the text and never overlap`() {
        val text = "The governor cuts out at about 125 psi, and cuts in at 100 psi."
        val ranges = FocusText.boldRanges(text, FocusText.Strength.STRONG)
        var last = 0
        ranges.forEach { span ->
            assertTrue(span.start >= last)
            assertTrue(span.end <= text.length)
            assertTrue(span.end > span.start)
            last = span.end
        }
    }
}
