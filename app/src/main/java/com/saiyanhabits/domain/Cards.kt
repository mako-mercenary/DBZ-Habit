package com.saiyanhabits.domain

import kotlin.random.Random

/** A card's element. Charged squares on the board favour one element. */
enum class CardElement(val key: String, val label: String) {
    KI("K", "Ki"),
    STRENGTH("S", "Strength"),
    SPIRIT("P", "Spirit"),
    TECH("T", "Tech"),
    ;

    companion object {
        fun of(key: String): CardElement = entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown element \"$key\"")
    }
}

enum class CardRarity(val key: String, val label: String, val rank: Int) {
    COMMON("common", "Common", 0),
    RARE("rare", "Rare", 1),
    EPIC("epic", "Epic", 2),
    LEGENDARY("legendary", "Legendary", 3),
    SIGNATURE("signature", "Signature", 4),
    ;

    companion object {
        fun of(key: String): CardRarity = entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown rarity \"$key\"")
    }
}

/** Where a card comes from. */
enum class CardSource { PACK, SIGNATURE, BOND }

/** One card. Sides run 1 to 10; 10 is shown as A. */
data class CardDef(
    val id: String,
    val name: String,
    val rarity: CardRarity,
    val element: CardElement,
    val source: CardSource,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val left: Int,
    /** For bond cards: the mentor whose bond unlocks it ("ALL" for all of them) and the level. */
    val mentor: String? = null,
    val bondLevel: Int = 0,
) {
    val total: Int get() = top + right + bottom + left

    /** Stronger first: rarity, then total. */
    val strength: Int get() = rarity.rank * 100 + total
}

enum class Difficulty(val key: String, val label: String) {
    EASY("easy", "Easy"),
    MEDIUM("medium", "Medium"),
    HARD("hard", "Hard"),
    VERY_HARD("very_hard", "Very hard"),
    ;

    companion object {
        fun of(key: String): Difficulty = entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown difficulty \"$key\"")
    }
}

data class Challenger(
    val id: String,
    val name: String,
    val difficulty: Difficulty,
    val signature: String,
    /** The mentor this challenger is, if any: they join at bond 9 with them. */
    val mentor: Mentor?,
    val intro: List<String>,
    /** What they say when you beat them. */
    val win: List<String>,
    /** What they say when they beat you. */
    val lose: List<String>,
)

/** The rules in play for a match. */
data class CardRules(val same: Boolean, val plus: Boolean, val combo: Boolean, val elemental: Boolean) {
    companion object {
        /** Harder challengers play with more rules. */
        fun forDifficulty(d: Difficulty): CardRules = when (d) {
            Difficulty.EASY -> CardRules(same = false, plus = false, combo = false, elemental = true)
            Difficulty.MEDIUM -> CardRules(same = true, plus = false, combo = true, elemental = true)
            Difficulty.HARD, Difficulty.VERY_HARD -> CardRules(same = true, plus = true, combo = true, elemental = true)
        }
    }
}

/** Everything in assets/cards/cards.json. */
data class CardBook(val cards: List<CardDef>, val challengers: List<Challenger>) {
    private val byId = cards.associateBy { it.id }
    fun card(id: String): CardDef? = byId[id]
    val packCards: List<CardDef> get() = cards.filter { it.source == CardSource.PACK }
}

object CardParser {
    fun parse(json: String): CardBook {
        val root = MiniJson.parse(json).asObject("the file")
        val cards = root["cards"].asArray("\"cards\"").map { c ->
            val o = c.asObject("a card")
            val id = o["id"].asString("card id")
            CardDef(
                id = id,
                name = o["name"].asString("name of $id"),
                rarity = CardRarity.of(o["rarity"].asString("rarity of $id")),
                element = CardElement.of(o["element"].asString("element of $id")),
                source = CardSource.valueOf(o["source"].asString("source of $id").uppercase()),
                top = o["top"].asInt("top of $id"),
                right = o["right"].asInt("right of $id"),
                bottom = o["bottom"].asInt("bottom of $id"),
                left = o["left"].asInt("left of $id"),
                mentor = o["mentor"] as? String,
                bondLevel = (o["bondLevel"] as? Number)?.toInt() ?: 0,
            )
        }
        val challengers = root["challengers"].asArray("\"challengers\"").map { c ->
            val o = c.asObject("a challenger")
            val id = o["id"].asString("challenger id")
            Challenger(
                id = id,
                name = o["name"].asString("name of $id"),
                difficulty = Difficulty.of(o["difficulty"].asString("difficulty of $id")),
                signature = o["signature"].asString("signature of $id"),
                mentor = (o["mentor"] as? String)?.let { n -> Mentor.entries.firstOrNull { it.name == n } },
                intro = o["intro"].asArray("intro of $id").map { it.asString("an intro line") },
                win = o["win"].asArray("win of $id").map { it.asString("a win line") },
                lose = o["lose"].asArray("lose of $id").map { it.asString("a lose line") },
            )
        }
        return CardBook(cards, challengers)
    }
}

// ---------------------------------------------------------------- the board

enum class Side { PLAYER, OPPONENT;
    val other: Side get() = if (this == PLAYER) OPPONENT else PLAYER
}

/** A card on the board, and who owns it now. */
data class Placed(val card: CardDef, val owner: Side)

/** Why cards flipped on a move, for the banner. */
enum class CaptureKind { BASIC, SAME, PLUS, COMBO }

/** One move's outcome: the new board and which squares flipped, and how. */
data class MoveResult(val state: MatchState, val flips: List<Pair<Int, CaptureKind>>)

/**
 * A match in progress: the nine squares (row by row), each side's hand, whose
 * turn it is, the rules, and which squares are charged with an element.
 */
data class MatchState(
    val board: List<Placed?>,
    val playerHand: List<CardDef>,
    val opponentHand: List<CardDef>,
    val turn: Side,
    val rules: CardRules,
    val elements: List<CardElement?>,
) {
    val isOver: Boolean get() = board.all { it != null }

    /** Cards each side owns on the board, plus what's left in their hand. */
    fun score(side: Side): Int =
        board.count { it?.owner == side } + if (side == Side.PLAYER) playerHand.size else opponentHand.size

    fun hand(side: Side): List<CardDef> = if (side == Side.PLAYER) playerHand else opponentHand

    val empties: List<Int> get() = board.indices.filter { board[it] == null }

    companion object {
        fun start(player: List<CardDef>, opponent: List<CardDef>, rules: CardRules, first: Side, random: Random): MatchState {
            val elements = MutableList<CardElement?>(9) { null }
            if (rules.elemental) {
                val charged = random.nextInt(0, 3)
                (0 until 9).shuffled(random).take(charged).forEach { elements[it] = CardElement.entries.random(random) }
            }
            return MatchState(List(9) { null }, player, opponent, first, rules, elements)
        }
    }
}

/** Board rules. Pure functions, no Android. */
object CardRulesEngine {

    /** A card's side on a square: +1 on a matching charged square, -1 on a different one, kept within 1 to 10. */
    fun value(card: CardDef, side: Int, element: CardElement?): Int {
        val base = when (side) {
            0 -> card.top
            1 -> card.right
            2 -> card.bottom
            else -> card.left
        }
        val shift = when {
            element == null -> 0
            element == card.element -> 1
            else -> -1
        }
        return (base + shift).coerceIn(1, 10)
    }

    /** Neighbours of square [i] as (their square, my side facing them, their side facing me). */
    fun neighbours(i: Int): List<Triple<Int, Int, Int>> = buildList {
        if (i >= 3) add(Triple(i - 3, 0, 2))
        if (i % 3 != 2) add(Triple(i + 1, 1, 3))
        if (i < 6) add(Triple(i + 3, 2, 0))
        if (i % 3 != 0) add(Triple(i - 1, 3, 1))
    }

    /** Plays [card] from the current side's hand onto empty square [square]. */
    fun play(state: MatchState, card: CardDef, square: Int): MoveResult {
        require(state.board[square] == null) { "Square $square is taken" }
        val me = state.turn
        val hand = state.hand(me)
        val index = hand.indexOfFirst { it === card }.takeIf { it >= 0 } ?: hand.indexOf(card)
        require(index >= 0) { "${card.name} isn't in the hand" }
        val board = state.board.toMutableList()
        board[square] = Placed(card, me)
        val flips = ArrayList<Pair<Int, CaptureKind>>()
        val els = state.elements

        fun side(i: Int, s: Int) = value(board[i]!!.card, s, els[i])

        val around = neighbours(square).filter { board[it.first] != null }
        val special = HashSet<Int>()
        if (state.rules.same) {
            val equal = around.filter { (n, mySide, theirSide) -> side(square, mySide) == side(n, theirSide) }
            if (equal.size >= 2) equal.forEach { (n, _, _) -> if (board[n]!!.owner != me) special += n.also { flips += it to CaptureKind.SAME } }
        }
        if (state.rules.plus) {
            val sums = around.groupBy { (n, mySide, theirSide) -> side(square, mySide) + side(n, theirSide) }
            sums.values.filter { it.size >= 2 }.flatten().forEach { (n, _, _) ->
                if (board[n]!!.owner != me && n !in special) special += n.also { flips += it to CaptureKind.PLUS }
            }
        }
        special.forEach { board[it] = board[it]!!.copy(owner = me) }
        // Basic captures by the placed card.
        for ((n, mySide, theirSide) in around) {
            val other = board[n]!!
            if (other.owner != me && side(square, mySide) > side(n, theirSide)) {
                board[n] = other.copy(owner = me)
                flips += n to CaptureKind.BASIC
            }
        }
        // Combo: cards taken by Same or Plus capture in turn, by the basic rule.
        if (state.rules.combo) {
            val queue = ArrayDeque(special)
            while (queue.isNotEmpty()) {
                val from = queue.removeFirst()
                for ((n, mySide, theirSide) in neighbours(from)) {
                    val other = board[n] ?: continue
                    if (other.owner != me && side(from, mySide) > side(n, theirSide)) {
                        board[n] = other.copy(owner = me)
                        flips += n to CaptureKind.COMBO
                        queue.addLast(n)
                    }
                }
            }
        }
        val newHand = hand.toMutableList().apply { removeAt(index) }
        val next = if (me == Side.PLAYER) state.copy(board = board, playerHand = newHand) else state.copy(board = board, opponentHand = newHand)
        return MoveResult(next.copy(turn = me.other), flips)
    }
}

/** The computer's moves. */
object CardAi {

    /** The move [state]'s current side should make, at [difficulty]. */
    fun choose(state: MatchState, difficulty: Difficulty, random: Random): Pair<CardDef, Int> {
        val me = state.turn
        val moves = moves(state)
        require(moves.isNotEmpty()) { "No moves left" }
        val randomChance = when (difficulty) {
            Difficulty.EASY -> 0.45
            Difficulty.MEDIUM -> 0.12
            else -> 0.0
        }
        if (random.nextDouble() < randomChance) return moves.random(random)
        val depth = when (difficulty) {
            Difficulty.EASY, Difficulty.MEDIUM -> 1
            Difficulty.HARD -> if (state.empties.size <= 5) 5 else 3
            Difficulty.VERY_HARD -> if (state.empties.size <= 6) 9 else 4
        }
        var best = Int.MIN_VALUE
        val bestMoves = ArrayList<Pair<CardDef, Int>>()
        for (move in ordered(state, moves)) {
            val after = CardRulesEngine.play(state, move.first, move.second).state
            val v = -negamax(after, depth - 1, -1000, 1000, me.other)
            if (v > best) {
                best = v
                bestMoves.clear()
                bestMoves += move
            } else if (v == best) {
                bestMoves += move
            }
        }
        return bestMoves.random(random)
    }

    /** Every (card, square) the current side can play; identical cards are tried once. */
    fun moves(state: MatchState): List<Pair<CardDef, Int>> {
        val cards = state.hand(state.turn).distinctBy { it.id }
        return cards.flatMap { c -> state.empties.map { c to it } }
    }

    private fun ordered(state: MatchState, moves: List<Pair<CardDef, Int>>): List<Pair<CardDef, Int>> =
        moves.sortedByDescending { (c, sq) -> CardRulesEngine.play(state, c, sq).flips.size * 10 + c.total }

    /** Score for [side] to move: their cards minus the other side's. */
    private fun negamax(state: MatchState, depth: Int, alphaIn: Int, beta: Int, side: Side): Int {
        if (state.isOver || depth <= 0) return state.score(side) - state.score(side.other)
        val ms = moves(state)
        if (ms.isEmpty()) return state.score(side) - state.score(side.other)
        var alpha = alphaIn
        var best = Int.MIN_VALUE
        val list = if (depth >= 2) ordered(state, ms) else ms
        for ((c, sq) in list) {
            val v = -negamax(CardRulesEngine.play(state, c, sq).state, depth - 1, -beta, -alpha, side.other)
            if (v > best) best = v
            if (v > alpha) alpha = v
            if (alpha >= beta) break
        }
        return best
    }
}

// ---------------------------------------------------------------- collecting

/** Packs, challengers and the stakes. Pure functions, no Android. */
object CardCollecting {

    const val PACK_SIZE = 5
    const val HAND_SIZE = 5
    const val DECK_EXTRAS = 9

    /** You always keep at least this many cards, so you can always play. */
    const val MIN_COLLECTION = 5

    /** Pack odds out of 100: common, rare, epic, legendary. */
    val PACK_ODDS = listOf(60, 28, 10, 2)

    private fun rollRarity(odds: List<Int>, random: Random): CardRarity {
        var r = random.nextInt(odds.sum())
        for ((i, w) in odds.withIndex()) {
            if (r < w) return CardRarity.entries[i]
            r -= w
        }
        return CardRarity.COMMON
    }

    private fun pick(book: CardBook, rarity: CardRarity, random: Random): CardDef {
        val pool = book.packCards.filter { it.rarity == rarity }.ifEmpty { book.packCards }
        return pool[random.nextInt(pool.size)]
    }

    /** A daily pack: five random pack cards, rarer ones less likely. */
    fun openPack(book: CardBook, random: Random): List<CardDef> = List(PACK_SIZE) { pick(book, rollRarity(PACK_ODDS, random), random) }

    /** The first cards you get: seven commons and three rares. */
    fun starterDeck(book: CardBook, random: Random): List<CardDef> =
        List(7) { pick(book, CardRarity.COMMON, random) } + List(3) { pick(book, CardRarity.RARE, random) }

    /** How likely each rarity is in a challenger's extra cards. */
    fun deckOdds(d: Difficulty): List<Int> = when (d) {
        Difficulty.EASY -> listOf(70, 27, 3, 0)
        Difficulty.MEDIUM -> listOf(35, 45, 18, 2)
        Difficulty.HARD -> listOf(10, 40, 40, 10)
        Difficulty.VERY_HARD -> listOf(0, 25, 50, 25)
    }

    /** A challenger's deck for one match: their signature card and nine random others. */
    fun challengerDeck(book: CardBook, c: Challenger, random: Random): List<CardDef> {
        val sig = book.card(c.signature) ?: error("No card ${c.signature}")
        return listOf(sig) + List(DECK_EXTRAS) { pick(book, rollRarity(deckOdds(c.difficulty), random), random) }
    }

    /**
     * The five a challenger plays: any of your cards they hold (best first,
     * so you can win them back), their signature, then their strongest others.
     */
    fun challengerHand(deck: List<CardDef>, held: List<CardDef>, signature: String): List<CardDef> {
        val heldBest = held.sortedByDescending { it.strength }.take(HAND_SIZE - 1)
        val sig = deck.firstOrNull { it.id == signature }
        val rest = deck.filter { it !== sig }.sortedByDescending { it.strength }
        return (heldBest + listOfNotNull(sig) + rest).take(HAND_SIZE)
    }

    /** The card a challenger takes when they win: your best one from the hand you played. */
    fun cardTaken(playedHand: List<CardDef>): CardDef = playedHand.maxBy { it.strength }

    /** The day's challenger: one from the pool, picked by the day, the same all day whatever happens. */
    fun challengerOfTheDay(pool: List<Challenger>, epochDay: Long): Challenger? =
        pool.sortedBy { it.id }.shuffled(Random(epochDay * 7919 + 17)).firstOrNull()

    /** Who you can be challenged by: the ten, plus mentors at bond 9 or more. */
    fun pool(book: CardBook, bondLevels: Map<Mentor, Int>): List<Challenger> =
        book.challengers.filter { it.mentor == null || (bondLevels[it.mentor] ?: 0) >= MENTOR_CHALLENGER_BOND }

    const val MENTOR_CHALLENGER_BOND = 9

    /** Bond cards your bonds have unlocked. */
    fun bondCardsUnlocked(book: CardBook, bondLevels: Map<Mentor, Int>): List<CardDef> =
        book.cards.filter { it.source == CardSource.BOND }.filter { card ->
            if (card.mentor == "ALL") Mentor.entries.all { (bondLevels[it] ?: 0) >= card.bondLevel }
            else (bondLevels[Mentor.entries.firstOrNull { it.name == card.mentor }] ?: 0) >= card.bondLevel
        }

    /**
     * True if a card can change hands in the next match today: not once
     * they've taken one from you today, nor once you've won one from them.
     * Rematches after that are friendly.
     */
    fun stakesToday(lostCardToday: Boolean, wonCardToday: Boolean): Boolean = !lostCardToday && !wonCardToday

    /** True if losing would take a card: only with stakes, and only if you'd still have enough to play. */
    fun loses(collectionSize: Int, stakes: Boolean): Boolean = stakes && collectionSize > MIN_COLLECTION
}
