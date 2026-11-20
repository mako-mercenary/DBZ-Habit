package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TsvTest {

    @Test
    fun `one card per line, question tab answer, no header`() {
        val text = Tsv.of(listOf(Card("What is ABS?", "Antilock braking system."), Card("Q2", "A2")))
        assertEquals("What is ABS?\tAntilock braking system.\nQ2\tA2", text)
    }

    @Test
    fun `a tab or a line break inside a card becomes a space`() {
        val text = Tsv.of(listOf(Card("Two\tparts", "Line one\nline two")))
        assertEquals("Two parts\tLine one line two", text)
    }

    @Test
    fun `no card gives an empty file, not a blank line`() {
        assertEquals("", Tsv.of(emptyList()))
    }

    @Test
    fun `the file name carries the label and the date`() {
        assertEquals("cdl-air-brakes-2026-11-20.tsv", Tsv.fileName("Air Brakes", "2026-11-20"))
        assertEquals("cdl-section-5-2026-11-20.tsv", Tsv.fileName("Section 5", "2026-11-20"))
    }
}
