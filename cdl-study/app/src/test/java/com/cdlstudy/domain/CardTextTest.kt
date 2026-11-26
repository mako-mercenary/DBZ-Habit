package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CardTextTest {

    @Test
    fun `a tab or a line break becomes a space`() {
        assertEquals("Two parts", CardText.clean("Two\tparts"))
        assertEquals("Line one line two", CardText.clean("Line one\nline two"))
        assertEquals("a b", CardText.clean("a\r\nb"))
    }

    @Test
    fun `runs of spaces collapse and the ends are trimmed`() {
        assertEquals("one two", CardText.clean("  one    two  "))
    }

    @Test
    fun `ordinary text is left alone`() {
        assertEquals(
            "At what pressure does the governor cut out?",
            CardText.clean("At what pressure does the governor cut out?"),
        )
    }
}
