package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurtMatcherTest {

    private val fact = Fact(
        "f1",
        "A Class A licence needs a GCWR of 26,001 pounds or more.",
        critical = true,
        keywords = listOf("class a", "26001"),
    )

    @Test
    fun `all keywords present suggests a hit`() {
        val suggestion = BlurtMatcher.suggest(
            listOf(fact),
            "You need a class A for 26,001 pounds or more.",
        )["f1"]!!
        assertEquals(Mark.HIT, suggestion.mark)
        assertTrue(suggestion.missing.isEmpty())
    }

    @Test
    fun `some keywords present suggests a part said`() {
        val suggestion = BlurtMatcher.suggest(listOf(fact), "A class A is the biggest one.")["f1"]!!
        assertEquals(Mark.PARTIAL, suggestion.mark)
        assertEquals(listOf("26001"), suggestion.missing)
    }

    @Test
    fun `no keyword present suggests a miss`() {
        val suggestion = BlurtMatcher.suggest(listOf(fact), "I remember nothing useful.")["f1"]!!
        assertEquals(Mark.MISS, suggestion.mark)
    }

    @Test
    fun `a fact with no keywords always suggests a miss, so you read it yourself`() {
        val bare = Fact("f2", "No keywords here")
        val suggestion = BlurtMatcher.suggest(listOf(bare), "anything at all")["f2"]!!
        assertEquals(Mark.MISS, suggestion.mark)
        assertFalse(suggestion.hasKeywords)
    }

    @Test
    fun `punctuation in a number does not stop the match`() {
        assertTrue(BlurtMatcher.contains(BlurtMatcher.normalize("over 26,001 lb"), "26001"))
    }

    @Test
    fun `a keyword matches a whole word only`() {
        val text = BlurtMatcher.normalize("the airbrakes were fine")
        assertFalse(BlurtMatcher.contains(text, "air"))
        assertTrue(BlurtMatcher.contains(BlurtMatcher.normalize("the air brake test"), "air brake"))
    }
}
