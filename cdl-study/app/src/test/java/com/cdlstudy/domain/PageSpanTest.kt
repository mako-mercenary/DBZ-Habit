package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSpanTest {

    @Test
    fun `reads one page and a range`() {
        assertEquals(PageSpan(12, 12), PageSpan.parse("12"))
        assertEquals(PageSpan(12, 15), PageSpan.parse("12-15"))
        assertEquals(PageSpan(12, 15), PageSpan.parse(" 12 - 15 "))
    }

    @Test
    fun `an en dash is a range too, because the manual uses one`() {
        assertEquals(PageSpan(5, 9), PageSpan.parse("5–9"))
    }

    @Test
    fun `nothing gives null, so a subsection without pages is allowed`() {
        assertNull(PageSpan.parse(null))
        assertNull(PageSpan.parse("  "))
    }

    @Test
    fun `rubbish is refused instead of guessed`() {
        listOf("page 12", "12-", "1-2-3", "twelve").forEach { bad ->
            assertTrue("'$bad' should be refused", runCatching { PageSpan.parse(bad) }.isFailure)
        }
    }

    @Test
    fun `a backwards or zero range is refused`() {
        assertTrue(runCatching { PageSpan(15, 12) }.isFailure)
        assertTrue(runCatching { PageSpan(0, 3) }.isFailure)
    }

    @Test
    fun `the pages are listed in order`() {
        assertEquals(listOf(7, 8, 9), PageSpan(7, 9).pages)
        assertEquals(3, PageSpan(7, 9).count)
        assertEquals("7-9", PageSpan(7, 9).toString())
        assertEquals("7", PageSpan(7, 7).toString())
    }
}
