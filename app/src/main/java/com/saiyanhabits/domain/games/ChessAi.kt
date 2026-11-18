package com.saiyanhabits.domain.games

import kotlin.math.abs
import kotlin.random.Random

/** How hard Roshi tries. */
enum class ChessLevel(val title: String, val caption: String) {
    NAPPING("Napping", "half asleep"),
    CASUAL("Casual", "a friendly game"),
    FOCUSED("Focused", "paying attention"),
    SERIOUS("Serious", "the real master"),
}

/**
 * Roshi's chess: alpha-beta search over material and piece placement, with
 * a search of captures at the end of each line on the harder levels.
 * Easier levels search less deeply and sometimes play a looser move.
 */
object ChessAi {
    private val Values = intArrayOf(0, 100, 320, 330, 500, 900, 0)
    private const val MATE = 100_000

    // Piece-square tables from white's point of view, a1 first. Black mirrors them.
    private val PawnTable = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, -20, -20, 10, 10, 5,
        5, -5, -10, 0, 0, -10, -5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, 5, 10, 25, 25, 10, 5, 5,
        10, 10, 20, 30, 30, 20, 10, 10,
        50, 50, 50, 50, 50, 50, 50, 50,
        0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val KnightTable = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50,
    )
    private val BishopTable = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -20, -10, -10, -10, -10, -10, -10, -20,
    )
    private val RookTable = intArrayOf(
        0, 0, 0, 5, 5, 0, 0, 0,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        5, 10, 10, 10, 10, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val QueenTable = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -10, 5, 5, 5, 5, 5, 0, -10,
        0, 0, 5, 5, 5, 5, 0, -5,
        -5, 0, 5, 5, 5, 5, 0, -5,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20,
    )
    private val KingTable = intArrayOf(
        20, 30, 10, 0, 0, 10, 30, 20,
        20, 20, 0, 0, 0, 0, 20, 20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
    )
    private val Tables = arrayOf(IntArray(64), PawnTable, KnightTable, BishopTable, RookTable, QueenTable, KingTable)

    /** The position's score for the side to move, in centipawns. */
    fun evaluate(p: Position): Int {
        var score = 0
        for (sq in 0 until 64) {
            val piece = p[sq]
            if (piece == 0) continue
            val kind = abs(piece)
            val table = if (piece > 0) Tables[kind][sq] else Tables[kind][(7 - rankOf(sq)) * 8 + fileOf(sq)]
            val v = Values[kind] + table
            score += if (piece > 0) v else -v
        }
        return if (p.whiteToMove) score else -score
    }

    private class OutOfTime : RuntimeException(null, null, false, false)

    private class Search(val deadline: Long, val quiescence: Boolean) {
        var nodes = 0

        fun negamax(p: Position, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
            if (++nodes and 1023 == 0 && System.nanoTime() > deadline) throw OutOfTime()
            if (depth <= 0) return if (quiescence) quiesce(p, alphaIn, beta, 0) else evaluate(p)
            val moves = ordered(p, p.legalMoves())
            if (moves.isEmpty()) return if (p.inCheck()) -MATE + ply else 0
            if (p.halfmove >= 100) return 0
            var alpha = alphaIn
            for (m in moves) {
                val score = -negamax(p.play(m), depth - 1, -beta, -alpha, ply + 1)
                if (score >= beta) return score
                if (score > alpha) alpha = score
            }
            return alpha
        }

        fun quiesce(p: Position, alphaIn: Int, beta: Int, depth: Int): Int {
            if (++nodes and 1023 == 0 && System.nanoTime() > deadline) throw OutOfTime()
            val stand = evaluate(p)
            if (stand >= beta || depth > 6) return stand
            var alpha = maxOf(alphaIn, stand)
            for (m in ordered(p, p.legalCaptures())) {
                val score = -quiesce(p.play(m), -beta, -alpha, depth + 1)
                if (score >= beta) return score
                if (score > alpha) alpha = score
            }
            return alpha
        }
    }

    /** Captures of valuable pieces by cheap ones first, then promotions, then the rest. */
    private fun ordered(p: Position, moves: List<Move>): List<Move> = moves.sortedByDescending { m ->
        val victim = abs(p[m.to])
        val attacker = abs(p[m.from])
        (if (victim != 0) 10 * Values[victim] - Values[attacker] else 0) + if (m.promotion != 0) 800 else 0
    }

    /** Each legal move with its score for the side to move, searched to [depth]. */
    private fun scoreMoves(p: Position, depth: Int, search: Search): List<Pair<Move, Int>> {
        val out = ArrayList<Pair<Move, Int>>()
        var alpha = -MATE * 2
        for (m in ordered(p, p.legalMoves())) {
            val score = -search.negamax(p.play(m), depth - 1, -MATE * 2, -alpha + 1, 1)
            out += m to score
            if (score > alpha) alpha = score
        }
        return out.sortedByDescending { it.second }
    }

    /**
     * Roshi's move for [p] at [level], within [timeMillis]. Searches deeper
     * while there is time and keeps the best line from the deepest search
     * that finished. Null if there are no legal moves.
     */
    fun choose(p: Position, level: ChessLevel, random: Random = Random.Default, timeMillis: Long = 1500): Move? {
        val legal = p.legalMoves()
        if (legal.isEmpty()) return null
        val maxDepth = when (level) {
            ChessLevel.NAPPING -> 1
            ChessLevel.CASUAL -> 2
            ChessLevel.FOCUSED -> 3
            ChessLevel.SERIOUS -> 6
        }
        val search = Search(System.nanoTime() + timeMillis * 1_000_000, quiescence = level >= ChessLevel.FOCUSED)
        var best: List<Pair<Move, Int>> = legal.map { it to 0 }
        for (depth in 1..maxDepth) {
            try {
                best = scoreMoves(p, depth, search)
            } catch (_: OutOfTime) {
                break
            }
            if (best.first().second > MATE / 2) break // a forced mate: no need to look further
        }
        return when (level) {
            // Half asleep: a third of the time, any move at all.
            ChessLevel.NAPPING -> if (random.nextFloat() < 0.33f) legal.random(random) else pickNear(best, 120, random)
            ChessLevel.CASUAL -> pickNear(best, 60, random)
            ChessLevel.FOCUSED -> pickNear(best, 15, random)
            ChessLevel.SERIOUS -> best.first().first
        }
    }

    /** One of the moves within [margin] centipawns of the best, at random. */
    private fun pickNear(scored: List<Pair<Move, Int>>, margin: Int, random: Random): Move {
        val top = scored.first().second
        // Never trade a found mate for something looser.
        if (top > MATE / 2) return scored.first().first
        return scored.filter { it.second >= top - margin }.random(random).first
    }

    /** A hint: the move Roshi would play seriously, given a little time. */
    fun hint(p: Position): Move? = choose(p, ChessLevel.SERIOUS, timeMillis = 800)
}
