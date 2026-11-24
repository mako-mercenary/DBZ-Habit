package com.cdlstudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NumberDrillTest {

    @Test
    fun `a unit makes a number a quantity`() {
        assertEquals(
            listOf("125 psi"),
            NumberDrill.quantities("The governor stops the compressor at about 125 psi."),
        )
        assertEquals(
            listOf("26,001 pounds", "10,000 pounds"),
            NumberDrill.quantities("A GCWR of 26,001 pounds or more, towing over 10,000 pounds."),
        )
    }

    @Test
    fun `a list of numbers is not a quantity, so it is never blanked`() {
        val text = "For a Class A license the manual says to study sections 1, 2, 3, 6, 11, 12 and 13."
        assertEquals(emptyList<String>(), NumberDrill.quantities(text))
        assertEquals(text, NumberDrill.cloze(text))
    }

    @Test
    fun `fractions, decimals, percentages, money, years and bare counts all count`() {
        assertEquals(listOf("4/32"), NumberDrill.quantities("Tread must be 4/32 of an inch."))
        assertEquals(listOf("0.04%"), NumberDrill.quantities("A BAC of 0.04% or more is illegal."))
        assertEquals(listOf("1.75"), NumberDrill.quantities("Perception time averages 1.75 seconds."))
        assertEquals(listOf("\$5,000"), NumberDrill.quantities("A court can fine you up to \$5,000."))
        assertEquals(listOf("1997"), NumberDrill.quantities("Built on or after 1 March 1997."))
        assertEquals(listOf("16 or more"), NumberDrill.quantities("Designed to carry 16 or more passengers."))
    }

    @Test
    fun `a range is one quantity, not two`() {
        assertEquals(
            listOf("120 to 140 psi"),
            NumberDrill.quantities("Governor cutout is about 120 to 140 psi."),
        )
    }

    @Test
    fun `the blank takes the number and leaves the unit, so you know what is wanted`() {
        assertEquals(
            "The governor stops the compressor at about _____ psi.",
            NumberDrill.cloze("The governor stops the compressor at about 125 psi."),
        )
        assertEquals(
            "Tread must be _____ of an inch.",
            NumberDrill.cloze("Tread must be 4/32 of an inch."),
        )
    }

    @Test
    fun `digits strip the commas and the dollar sign`() {
        assertEquals(listOf("26001", "10000"), NumberDrill.digits("26,001 pounds and 10,000 pounds"))
        assertEquals(listOf("5000"), NumberDrill.digits("a fine of \$5,000"))
    }

    @Test
    fun `a number written as a word is the same number`() {
        assertEquals(listOf("3"), NumberDrill.digits("three seconds"))
        assertEquals(listOf("15"), NumberDrill.digits("fifteen"))
    }

    private val item = NumberDrill.Item(
        factId = "5.3.3.3",
        subsectionId = "5.3.3",
        prompt = "No more than _____ psi in one minute on a single vehicle, or _____ psi on a combination.",
        wanted = listOf("3", "4"),
        answer = "No more than 3 psi in one minute on a single vehicle, or 4 psi on a combination.",
        page = "5",
    )

    @Test
    fun `every number must be there for a hit`() {
        assertEquals(Mark.HIT, NumberDrill.check("3 and 4", item))
        assertEquals(Mark.HIT, NumberDrill.check("three, four", item))
    }

    @Test
    fun `one of two numbers is half the fact, so it is a partial`() {
        assertEquals(Mark.PARTIAL, NumberDrill.check("3", item))
        assertEquals(Mark.PARTIAL, NumberDrill.check("4 psi", item))
    }

    @Test
    fun `a near miss is a miss, because a number is not nearly right`() {
        assertEquals(Mark.MISS, NumberDrill.check("30 and 40", item))
        assertEquals(Mark.MISS, NumberDrill.check("I do not remember", item))
        assertEquals(Mark.MISS, NumberDrill.check("", item))
    }

    @Test
    fun `a fraction counts with a slash or a space`() {
        val tread = NumberDrill.Item("x", "y", "_____ inch", listOf("4/32"), "4/32 inch", null)
        assertEquals(Mark.HIT, NumberDrill.check("4/32", tread))
        assertEquals(Mark.HIT, NumberDrill.check("4 32", tread))
        assertEquals(Mark.MISS, NumberDrill.check("2/32", tread))
    }

    @Test
    fun `only must-memorize facts join the drill`() {
        val sub = Subsection(
            id = "x.1",
            title = "Mixed",
            facts = listOf(
                Fact("a", "Cut-out is 125 psi.", critical = true),
                Fact("b", "Cut-in is 100 psi.", critical = false),
                Fact("c", "The governor controls the compressor.", critical = true),
            ),
        )
        val items = NumberDrill.itemsOf(sub)
        assertEquals(listOf("a"), items.map { it.factId })
    }

    @Test
    fun `a round holds no repeats, however the weighting falls`() {
        val sub = Subsection(
            id = "x.1",
            title = "Numbers",
            facts = (1..6).map { Fact("f$it", "A limit of $it psi.", critical = true) },
        )
        val content = Content(listOf(Section(1, "Test", Track.GENERAL_KNOWLEDGE, subsections = listOf(sub))))
        val round = NumberDrill.build(content, Random(7), length = 4, weakFactIds = setOf("f1"))
        assertEquals(4, round.size)
        assertEquals(4, round.map { it.factId }.distinct().size)
    }

    @Test
    fun `a round cannot be longer than the pool`() {
        val sub = Subsection(
            id = "x.1",
            title = "Numbers",
            facts = listOf(Fact("f1", "A limit of 5 psi.", critical = true)),
        )
        val content = Content(listOf(Section(1, "Test", Track.GENERAL_KNOWLEDGE, subsections = listOf(sub))))
        assertEquals(1, NumberDrill.build(content, Random(1), length = 20).size)
    }

    @Test
    fun `every item in the real pool has a blank and at least one number`() {
        val content = RealContent.load()
        val pool = NumberDrill.pool(content)
        assertTrue("the pool is thin: ${pool.size}", pool.size >= 100)
        pool.forEach { item ->
            assertTrue("${item.factId} has no blank: ${item.prompt}", item.prompt.contains(NumberDrill.BLANK))
            assertTrue("${item.factId} wants nothing", item.wanted.isNotEmpty())
            assertEquals(
                "${item.factId}: typing the real numbers must score a hit",
                Mark.HIT,
                NumberDrill.check(item.wanted.joinToString(" "), item),
            )
        }
    }
}
