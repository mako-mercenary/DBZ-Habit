package com.saiyanhabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class CardsTest {

    private fun card(id: String, t: Int, r: Int, b: Int, l: Int, el: CardElement = CardElement.KI, rarity: CardRarity = CardRarity.COMMON) =
        CardDef(id, id, rarity, el, CardSource.PACK, t, r, b, l)

    private val none = CardRules(same = false, plus = false, combo = false, elemental = false)
    private val all = CardRules(same = true, plus = true, combo = true, elemental = false)

    /** A board with cards already placed: square to (card, owner). */
    private fun board(rules: CardRules, placed: Map<Int, Pair<CardDef, Side>>, hand: List<CardDef>, elements: List<CardElement?> = List(9) { null }) =
        MatchState(List(9) { placed[it]?.let { (c, o) -> Placed(c, o) } }, hand, listOf(card("x", 1, 1, 1, 1)), Side.PLAYER, rules, elements)

    @Test
    fun `a higher facing number captures`() {
        val mine = card("mine", 5, 5, 5, 5)
        val s = board(none, mapOf(1 to (card("weak", 1, 1, 4, 1) to Side.OPPONENT), 3 to (card("strong", 1, 9, 1, 1) to Side.OPPONENT)), listOf(mine))
        val r = CardRulesEngine.play(s, mine, 4)
        // Above: their bottom 4 < my top 5: captured. Left: their right 9 > my left 5: kept.
        assertEquals(Side.PLAYER, r.state.board[1]!!.owner)
        assertEquals(Side.OPPONENT, r.state.board[3]!!.owner)
        assertEquals(listOf(1 to CaptureKind.BASIC), r.flips)
        assertEquals(Side.OPPONENT, r.state.turn)
    }

    @Test
    fun `equal numbers don't capture without Same`() {
        val mine = card("mine", 4, 4, 4, 4)
        val s = board(none, mapOf(1 to (card("a", 1, 1, 4, 1) to Side.OPPONENT), 5 to (card("b", 1, 1, 1, 4) to Side.OPPONENT)), listOf(mine))
        assertTrue(CardRulesEngine.play(s, mine, 4).flips.isEmpty())
    }

    @Test
    fun `Same captures when two sides match`() {
        val mine = card("mine", 4, 4, 4, 4)
        val s = board(all.copy(plus = false), mapOf(1 to (card("a", 1, 1, 4, 1) to Side.OPPONENT), 5 to (card("b", 1, 1, 1, 4) to Side.OPPONENT)), listOf(mine))
        val r = CardRulesEngine.play(s, mine, 4)
        assertEquals(setOf(1, 5), r.flips.filter { it.second == CaptureKind.SAME }.map { it.first }.toSet())
    }

    @Test
    fun `Same counts your own cards but only flips theirs`() {
        val mine = card("mine", 4, 4, 4, 4)
        val s = board(all.copy(plus = false), mapOf(1 to (card("a", 1, 1, 4, 1) to Side.PLAYER), 5 to (card("b", 1, 1, 1, 4) to Side.OPPONENT)), listOf(mine))
        val r = CardRulesEngine.play(s, mine, 4)
        assertEquals(listOf(5 to CaptureKind.SAME), r.flips)
    }

    @Test
    fun `Plus captures when two sums match`() {
        val mine = card("mine", 3, 6, 1, 1)
        // Above: 3 + 7 = 10. Right: 6 + 4 = 10.
        val s = board(all.copy(same = false), mapOf(1 to (card("a", 1, 1, 7, 1) to Side.OPPONENT), 5 to (card("b", 1, 1, 1, 4) to Side.OPPONENT)), listOf(mine))
        val r = CardRulesEngine.play(s, mine, 4)
        assertEquals(setOf(1, 5), r.flips.map { it.first }.toSet())
        assertTrue(r.flips.any { it.second == CaptureKind.PLUS })
    }

    @Test
    fun `Combo chains from cards taken by Same`() {
        val mine = card("mine", 4, 4, 4, 4)
        // Square 1 and 5 taken by Same; square 1's left (9) then beats square 0's right (2).
        val s = board(
            all.copy(plus = false),
            mapOf(
                1 to (card("a", 1, 1, 4, 9) to Side.OPPONENT),
                5 to (card("b", 1, 1, 1, 4) to Side.OPPONENT),
                0 to (card("c", 1, 2, 1, 1) to Side.OPPONENT),
            ),
            listOf(mine),
        )
        val r = CardRulesEngine.play(s, mine, 4)
        assertEquals(Side.PLAYER, r.state.board[0]!!.owner)
        assertTrue(0 to CaptureKind.COMBO in r.flips)
    }

    @Test
    fun `charged squares add one for a match and take one otherwise`() {
        val k = card("k", 5, 5, 5, 5, CardElement.KI)
        assertEquals(6, CardRulesEngine.value(k, 0, CardElement.KI))
        assertEquals(4, CardRulesEngine.value(k, 0, CardElement.TECH))
        assertEquals(5, CardRulesEngine.value(k, 0, null))
        assertEquals(10, CardRulesEngine.value(card("a", 10, 1, 1, 1), 0, CardElement.KI))
        assertEquals(1, CardRulesEngine.value(card("b", 1, 1, 1, 1), 0, CardElement.TECH))
        // A 5 on a mismatched square no longer beats a 4.
        val elements = MutableList<CardElement?>(9) { null }.apply { this[4] = CardElement.TECH }
        val s = board(none.copy(elemental = true), mapOf(1 to (card("a", 1, 1, 4, 1) to Side.OPPONENT)), listOf(k), elements)
        assertTrue(CardRulesEngine.play(s, k, 4).flips.isEmpty())
    }

    @Test
    fun `the score counts board cards and the hand`() {
        val p = List(5) { card("p$it", 5, 5, 5, 5) }
        val o = List(5) { card("o$it", 5, 5, 5, 5) }
        val s = MatchState.start(p, o, none, Side.PLAYER, Random(1))
        assertEquals(5, s.score(Side.PLAYER))
        assertEquals(5, s.score(Side.OPPONENT))
        assertFalse(s.isOver)
    }

    @Test
    fun `a full game ends with ten cards between the two sides`() {
        val random = Random(4)
        val p = List(5) { card("p$it", random.nextInt(1, 10), random.nextInt(1, 10), random.nextInt(1, 10), random.nextInt(1, 10)) }
        val o = List(5) { card("o$it", random.nextInt(1, 10), random.nextInt(1, 10), random.nextInt(1, 10), random.nextInt(1, 10)) }
        var s = MatchState.start(p, o, all, Side.PLAYER, random)
        while (!s.isOver) {
            val (c, sq) = CardAi.choose(s, Difficulty.MEDIUM, random)
            s = CardRulesEngine.play(s, c, sq).state
        }
        assertEquals(10, s.score(Side.PLAYER) + s.score(Side.OPPONENT))
        assertEquals(1, s.playerHand.size + s.opponentHand.size)
    }

    @Test
    fun `the computer takes an obvious capture`() {
        val big = card("big", 9, 9, 9, 9)
        val s = MatchState(
            List(9) { if (it == 1) Placed(card("weak", 1, 1, 1, 1), Side.PLAYER) else null },
            listOf(card("p", 1, 1, 1, 1)),
            listOf(big),
            Side.OPPONENT,
            none,
            List(9) { null },
        )
        repeat(5) {
            val (_, sq) = CardAi.choose(s, Difficulty.HARD, Random(it))
            assertTrue("square $sq doesn't touch the weak card", sq in setOf(0, 2, 4))
        }
    }

    @Test
    fun `the hardest computer thinks fast enough`() {
        val book = shipped()
        val random = Random(9)
        val p = List(5) { book.packCards.random(random) }
        val o = List(5) { book.packCards.random(random) }
        var s = MatchState.start(p, o, CardRules.forDifficulty(Difficulty.VERY_HARD), Side.OPPONENT, random)
        val started = System.nanoTime()
        while (!s.isOver) {
            val (c, sq) = CardAi.choose(s, if (s.turn == Side.OPPONENT) Difficulty.VERY_HARD else Difficulty.EASY, random)
            s = CardRulesEngine.play(s, c, sq).state
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("a whole game took $ms ms", ms < 8_000)
    }

    private fun shipped() = CardParser.parse(File("src/main/assets/cards/cards.json").readText())

    @Test
    fun `the card file has every card and challenger`() {
        val book = shipped()
        assertEquals(161, book.cards.size)
        assertEquals(135, book.packCards.size)
        assertEquals(15, book.cards.count { it.source == CardSource.SIGNATURE })
        assertEquals(11, book.cards.count { it.source == CardSource.BOND })
        assertEquals(15, book.challengers.size)
        assertEquals(10, book.challengers.count { it.mentor == null })
        assertEquals(Mentor.entries.toSet(), book.challengers.mapNotNull { it.mentor }.toSet())
        for (c in book.challengers) {
            assertTrue("${c.id}'s signature is missing", book.card(c.signature)?.source == CardSource.SIGNATURE)
            assertTrue(c.intro.isNotEmpty() && c.win.isNotEmpty() && c.lose.isNotEmpty())
        }
        for (c in book.cards) {
            for (v in listOf(c.top, c.right, c.bottom, c.left)) assertTrue("${c.id} has $v", v in 1..10)
        }
        // Rarer is stronger, on average.
        val avg = CardRarity.entries.take(4).map { r -> book.packCards.filter { it.rarity == r }.map { it.total }.average() }
        assertEquals(avg.sorted(), avg)
    }

    @Test
    fun `challengers carry your cards, then their signature`() {
        val book = shipped()
        val tien = book.challengers.first { it.id == "tien" }
        val deck = CardCollecting.challengerDeck(book, tien, Random(2))
        assertEquals(10, deck.size)
        assertEquals(tien.signature, deck.first().id)
        val mine = book.card("kid_goku")!!
        val hand = CardCollecting.challengerHand(deck, listOf(mine), tien.signature)
        assertEquals(5, hand.size)
        assertTrue(mine in hand)
        assertTrue(hand.any { it.id == tien.signature })
    }

    @Test
    fun `the challenger takes your best card`() {
        val a = card("a", 1, 1, 1, 1)
        val b = card("b", 9, 9, 9, 1, rarity = CardRarity.RARE)
        assertEquals(b, CardCollecting.cardTaken(listOf(a, b)))
    }

    @Test
    fun `one challenger all day, a new one most days, and mentors join at bond 9`() {
        val book = shipped()
        val pool = CardCollecting.pool(book, emptyMap())
        assertEquals(10, pool.size)
        val today = CardCollecting.challengerOfTheDay(pool, 500)!!
        assertEquals(today, CardCollecting.challengerOfTheDay(pool, 500))
        assertEquals(today, CardCollecting.challengerOfTheDay(pool.reversed(), 500))
        val week = (500L until 530L).map { CardCollecting.challengerOfTheDay(pool, it)!!.id }.toSet()
        assertTrue("only ${week.size} different challengers in 30 days", week.size >= 6)
        val withGoku = CardCollecting.pool(book, mapOf(Mentor.GOKU to 9))
        assertTrue(withGoku.any { it.mentor == Mentor.GOKU })
    }

    @Test
    fun `bond cards unlock at 5 and 10, Vegito with every bond maxed`() {
        val book = shipped()
        assertTrue(CardCollecting.bondCardsUnlocked(book, emptyMap()).isEmpty())
        val five = CardCollecting.bondCardsUnlocked(book, mapOf(Mentor.ROSHI to 5)).map { it.id }
        assertEquals(listOf("jackie_chun"), five)
        val ten = CardCollecting.bondCardsUnlocked(book, Mentor.entries.associateWith { 10 }).map { it.id }
        assertEquals(11, ten.size)
        assertTrue("vegito" in ten)
    }

    @Test
    fun `once a card changes hands, rematches that day are friendly, and nobody drops below five`() {
        assertTrue(CardCollecting.stakesToday(lostCardToday = false, wonCardToday = false))
        assertFalse(CardCollecting.stakesToday(lostCardToday = true, wonCardToday = false))
        assertFalse(CardCollecting.stakesToday(lostCardToday = false, wonCardToday = true))
        assertTrue(CardCollecting.loses(6, true))
        assertFalse(CardCollecting.loses(5, true))
        assertFalse(CardCollecting.loses(20, false))
    }

    @Test
    fun `packs hold five pack cards`() {
        val book = shipped()
        val pack = CardCollecting.openPack(book, Random(3))
        assertEquals(5, pack.size)
        assertTrue(pack.all { it.source == CardSource.PACK })
        assertEquals(10, CardCollecting.starterDeck(book, Random(3)).size)
    }
}
