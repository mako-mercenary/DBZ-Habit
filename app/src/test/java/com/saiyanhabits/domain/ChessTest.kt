package com.saiyanhabits.domain

import com.saiyanhabits.domain.games.ChessAi
import com.saiyanhabits.domain.games.ChessGame
import com.saiyanhabits.domain.games.ChessLevel
import com.saiyanhabits.domain.games.ChessResult
import com.saiyanhabits.domain.games.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChessTest {

    private fun perft(p: Position, depth: Int): Long =
        if (depth == 0) 1 else p.legalMoves().sumOf { perft(p.play(it), depth - 1) }

    // Standard move-generation counts (perft). Matching them means castling,
    // en passant, promotion and pins are all handled right.

    @Test
    fun `perft from the start`() {
        val p = Position.start()
        assertEquals(20, perft(p, 1))
        assertEquals(400, perft(p, 2))
        assertEquals(8902, perft(p, 3))
    }

    @Test
    fun `perft kiwipete`() {
        val p = Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48, perft(p, 1))
        assertEquals(2039, perft(p, 2))
        assertEquals(97862, perft(p, 3))
    }

    @Test
    fun `perft position 3 (en passant and pins)`() {
        val p = Position.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14, perft(p, 1))
        assertEquals(191, perft(p, 2))
        assertEquals(2812, perft(p, 3))
        assertEquals(43238, perft(p, 4))
    }

    @Test
    fun `perft position 4 (promotions and castling)`() {
        val p = Position.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(6, perft(p, 1))
        assertEquals(264, perft(p, 2))
        assertEquals(9467, perft(p, 3))
    }

    @Test
    fun `perft position 5`() {
        val p = Position.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
        assertEquals(44, perft(p, 1))
        assertEquals(1486, perft(p, 2))
        assertEquals(62379, perft(p, 3))
    }

    @Test
    fun `FEN round trips`() {
        for (fen in listOf(
            Position.START,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/8/8/3pP3/8/8/8/K6k w - d6 0 5",
        )) assertEquals(fen, Position.fromFen(fen).fen())
    }

    @Test
    fun `mate, stalemate and draws are recognised`() {
        // Fool's mate.
        var g = ChessGame.new()
        for (uci in listOf("f2f3", "e7e5", "g2g4", "d8h4")) g = g.play(g.position.legalMoves().first { it.uci == uci })
        assertEquals(ChessResult.BLACK_WINS, g.result)
        val stalemate = ChessGame.load("")
        assertEquals(ChessResult.ONGOING, stalemate.result)
        assertTrue(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").legalMoves().isEmpty())
        assertTrue(Position.fromFen("8/8/8/4k3/8/8/8/4K3 w - - 0 1").insufficientMaterial())
        // Knights back and forth three times.
        var r = ChessGame.new()
        repeat(2) {
            for (uci in listOf("g1f3", "g8f6", "f3g1", "f6g8")) r = r.play(r.position.legalMoves().first { it.uci == uci })
        }
        assertEquals(ChessResult.REPETITION, r.result)
    }

    @Test
    fun `saving, loading and taking back`() {
        var g = ChessGame.new()
        for (uci in listOf("e2e4", "e7e5", "g1f3")) g = g.play(g.position.legalMoves().first { it.uci == uci })
        val loaded = ChessGame.load(g.save())
        assertEquals(g.position.fen(), loaded.position.fen())
        assertEquals(Position.START, g.takeBack(3).position.fen())
        assertEquals(1, g.takeBack(2).moves.size)
    }

    @Test
    fun `the AI finds mate in one and takes a free queen`() {
        // White to move: Qh5-f7 is mate (scholar's mate).
        val scholar = Position.fromFen("r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4")
        assertEquals("h5f7", ChessAi.choose(scholar, ChessLevel.FOCUSED, Random(1))?.uci)
        assertEquals("h5f7", ChessAi.choose(scholar, ChessLevel.SERIOUS, Random(1))?.uci)
        // The white king can take an undefended queen next to it.
        val freeQueen = Position.fromFen("4k3/8/8/8/8/8/3q4/4K3 w - - 0 1")
        assertEquals("e1d2", ChessAi.choose(freeQueen, ChessLevel.FOCUSED, Random(1))?.uci)
    }

    @Test
    fun `every level always returns a legal move`() {
        var g = ChessGame.new()
        val random = Random(9)
        repeat(12) {
            if (g.result != ChessResult.ONGOING) return
            val level = ChessLevel.entries[it % ChessLevel.entries.size]
            val m = ChessAi.choose(g.position, level, random, timeMillis = 200)!!
            assertTrue(m in g.position.legalMoves())
            g = g.play(m)
        }
    }
}
