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
    fun `a decimal number survives, so 0_04 is one number and not two`() {
        val text = BlurtMatcher.normalize("the limit is 0.04% in a CMV")
        assertTrue(BlurtMatcher.contains(text, "0.04"))
        assertTrue(BlurtMatcher.contains(text, "0.04%"))
        assertEquals("the limit is 0.04 % in a cmv", text)
    }

    @Test
    fun `a number written as a word matches the digits`() {
        val text = BlurtMatcher.normalize("hold it about three seconds, then release")
        assertTrue(BlurtMatcher.contains(text, "3 seconds"))
        assertTrue(BlurtMatcher.contains(BlurtMatcher.normalize("2 business days"), "two business days"))
    }

    @Test
    fun `a plural s does not lose a fact you knew`() {
        assertTrue(BlurtMatcher.contains(BlurtMatcher.normalize("ten years"), "10 year"))
        assertTrue(BlurtMatcher.contains(BlurtMatcher.normalize("one year"), "1 years"))
        assertTrue(BlurtMatcher.contains(BlurtMatcher.normalize("the air brakes"), "air brake"))
    }

    @Test
    fun `a keyword can offer choices, and any one of them counts`() {
        val text = BlurtMatcher.normalize("I would use the headache rack")
        assertTrue(BlurtMatcher.contains(text, "header board|headache rack"))
        assertFalse(BlurtMatcher.contains(text, "header board|bulkhead"))
    }

    @Test
    fun `a keyword matches a whole word only`() {
        val text = BlurtMatcher.normalize("the airbrakes were fine")
        assertFalse(BlurtMatcher.contains(text, "air"))
        assertTrue(BlurtMatcher.contains(BlurtMatcher.normalize("the air brake test"), "air brake"))
    }
}
