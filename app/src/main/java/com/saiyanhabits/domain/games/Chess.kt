package com.saiyanhabits.domain.games

import kotlin.math.abs

/**
 * Chess rules: board, legal moves, check, mate, draws, and FEN. Squares are
 * 0..63, a1 = 0, h1 = 7, a8 = 56. Pieces are 1 pawn, 2 knight, 3 bishop,
 * 4 rook, 5 queen, 6 king; positive for white, negative for black.
 */
object Piece {
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6
}

/** A move. [promotion] is the piece type a pawn becomes, or 0. */
data class Move(val from: Int, val to: Int, val promotion: Int = 0) {
    /** Like "e2e4" or "e7e8q". */
    val uci: String
        get() = squareName(from) + squareName(to) + if (promotion != 0) "nbrq"[promotion - 2] else ""
}

fun squareName(sq: Int): String = "${'a' + sq % 8}${sq / 8 + 1}"
fun fileOf(sq: Int) = sq % 8
fun rankOf(sq: Int) = sq / 8

private const val WK = 1
private const val WQ = 2
private const val BK = 4
private const val BQ = 8

private val KnightJumps = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
private val KingSteps = listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
private val Straight = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
private val Diagonal = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

/** A position: whose move, castling rights, en passant square, and the move clocks. Immutable. */
class Position(
    private val board: IntArray,
    val whiteToMove: Boolean,
    val castling: Int,
    /** The square a pawn may capture onto en passant, or -1. */
    val ep: Int,
    val halfmove: Int,
    val fullmove: Int,
) {
    operator fun get(sq: Int): Int = board[sq]

    /** The board, for drawing. */
    fun squares(): IntArray = board.copyOf()

    fun kingSquare(white: Boolean): Int = board.indexOfFirst { it == if (white) Piece.KING else -Piece.KING }

    /** True when [sq] is attacked by the side [byWhite]. */
    fun attacked(sq: Int, byWhite: Boolean): Boolean {
        val f = fileOf(sq)
        val r = rankOf(sq)
        val sign = if (byWhite) 1 else -1
        // Pawns attack diagonally forward, so look one rank back from their point of view.
        val pr = r - sign
        if (pr in 0..7) {
            for (df in listOf(-1, 1)) {
                val pf = f + df
                if (pf in 0..7 && board[pr * 8 + pf] == sign * Piece.PAWN) return true
            }
        }
        for ((df, dr) in KnightJumps) {
            val nf = f + df
            val nr = r + dr
            if (nf in 0..7 && nr in 0..7 && board[nr * 8 + nf] == sign * Piece.KNIGHT) return true
        }
        for ((df, dr) in KingSteps) {
            val nf = f + df
            val nr = r + dr
            if (nf in 0..7 && nr in 0..7 && board[nr * 8 + nf] == sign * Piece.KING) return true
        }
        for ((df, dr) in Straight) if (slides(f, r, df, dr, sign, Piece.ROOK)) return true
        for ((df, dr) in Diagonal) if (slides(f, r, df, dr, sign, Piece.BISHOP)) return true
        return false
    }

    private fun slides(f: Int, r: Int, df: Int, dr: Int, sign: Int, slider: Int): Boolean {
        var nf = f + df
        var nr = r + dr
        while (nf in 0..7 && nr in 0..7) {
            val p = board[nr * 8 + nf]
            if (p != 0) return p == sign * slider || p == sign * Piece.QUEEN
            nf += df
            nr += dr
        }
        return false
    }

    fun inCheck(): Boolean = attacked(kingSquare(whiteToMove), !whiteToMove)

    /** Every legal move. */
    fun legalMoves(): List<Move> = pseudoMoves().filter { m ->
        val after = play(m)
        !after.attacked(after.kingSquare(whiteToMove), after.whiteToMove)
    }

    /** Legal captures and promotions only, for the quiet-position search. */
    fun legalCaptures(): List<Move> = pseudoMoves().filter { m ->
        (board[m.to] != 0 || m.promotion != 0 || (m.to == ep && abs(board[m.from]) == Piece.PAWN)) && run {
            val after = play(m)
            !after.attacked(after.kingSquare(whiteToMove), after.whiteToMove)
        }
    }

    private fun pseudoMoves(): List<Move> {
        val moves = ArrayList<Move>(48)
        val sign = if (whiteToMove) 1 else -1
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p == 0 || (p > 0) != whiteToMove) continue
            val f = fileOf(sq)
            val r = rankOf(sq)
            when (abs(p)) {
                Piece.PAWN -> {
                    val fwd = r + sign
                    val lastRank = if (whiteToMove) 7 else 0
                    fun add(to: Int) {
                        if (rankOf(to) == lastRank) {
                            for (promo in listOf(Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT)) moves += Move(sq, to, promo)
                        } else moves += Move(sq, to)
                    }
                    if (fwd in 0..7 && board[fwd * 8 + f] == 0) {
                        add(fwd * 8 + f)
                        val startRank = if (whiteToMove) 1 else 6
                        val two = r + 2 * sign
                        if (r == startRank && board[two * 8 + f] == 0) moves += Move(sq, two * 8 + f)
                    }
                    for (df in listOf(-1, 1)) {
                        val cf = f + df
                        if (cf !in 0..7 || fwd !in 0..7) continue
                        val to = fwd * 8 + cf
                        val target = board[to]
                        if ((target != 0 && (target > 0) != whiteToMove) || to == ep) add(to)
                    }
                }
                Piece.KNIGHT -> steps(sq, f, r, KnightJumps, moves)
                Piece.KING -> {
                    steps(sq, f, r, KingSteps, moves)
                    castles(sq, moves)
                }
                Piece.BISHOP -> rays(sq, f, r, Diagonal, moves)
                Piece.ROOK -> rays(sq, f, r, Straight, moves)
                Piece.QUEEN -> {
                    rays(sq, f, r, Straight, moves)
                    rays(sq, f, r, Diagonal, moves)
                }
            }
        }
        return moves
    }

    private fun steps(sq: Int, f: Int, r: Int, offsets: List<Pair<Int, Int>>, out: MutableList<Move>) {
        for ((df, dr) in offsets) {
            val nf = f + df
            val nr = r + dr
            if (nf !in 0..7 || nr !in 0..7) continue
            val to = nr * 8 + nf
            val target = board[to]
            if (target == 0 || (target > 0) != whiteToMove) out += Move(sq, to)
        }
    }

    private fun rays(sq: Int, f: Int, r: Int, dirs: List<Pair<Int, Int>>, out: MutableList<Move>) {
        for ((df, dr) in dirs) {
            var nf = f + df
            var nr = r + dr
            while (nf in 0..7 && nr in 0..7) {
                val to = nr * 8 + nf
                val target = board[to]
                if (target == 0) {
                    out += Move(sq, to)
                } else {
                    if ((target > 0) != whiteToMove) out += Move(sq, to)
                    break
                }
                nf += df
                nr += dr
            }
        }
    }

    private fun castles(sq: Int, out: MutableList<Move>) {
        val home = if (whiteToMove) 4 else 60
        if (sq != home) return
        val them = !whiteToMove
        if (attacked(sq, them)) return
        val (kingSide, queenSide) = if (whiteToMove) WK to WQ else BK to BQ
        if (castling and kingSide != 0 && board[sq + 1] == 0 && board[sq + 2] == 0 &&
            !attacked(sq + 1, them) && !attacked(sq + 2, them)
        ) out += Move(sq, sq + 2)
        if (castling and queenSide != 0 && board[sq - 1] == 0 && board[sq - 2] == 0 && board[sq - 3] == 0 &&
            !attacked(sq - 1, them) && !attacked(sq - 2, them)
        ) out += Move(sq, sq - 2)
    }

    /** The position after [move]. The move must be at least pseudo-legal. */
    fun play(move: Move): Position {
        val b = board.copyOf()
        val p = b[move.from]
        val captured = b[move.to]
        val kind = abs(p)
        b[move.to] = if (move.promotion != 0) (if (p > 0) move.promotion else -move.promotion) else p
        b[move.from] = 0
        var enPassantCapture = false
        if (kind == Piece.PAWN && move.to == ep && captured == 0 && fileOf(move.from) != fileOf(move.to)) {
            b[move.to - if (p > 0) 8 else -8] = 0
            enPassantCapture = true
        }
        if (kind == Piece.KING && abs(move.to - move.from) == 2) {
            if (move.to > move.from) {
                b[move.to - 1] = b[move.to + 1]; b[move.to + 1] = 0
            } else {
                b[move.to + 1] = b[move.to - 2]; b[move.to - 2] = 0
            }
        }
        var rights = castling
        fun touch(sq: Int) {
            rights = rights and when (sq) {
                0 -> WQ.inv(); 7 -> WK.inv(); 56 -> BQ.inv(); 63 -> BK.inv()
                4 -> (WK or WQ).inv(); 60 -> (BK or BQ).inv()
                else -> -1 // every bit: no change
            }
        }
        touch(move.from)
        touch(move.to)
        val newEp = if (kind == Piece.PAWN && abs(move.to - move.from) == 16) (move.from + move.to) / 2 else -1
        val reset = kind == Piece.PAWN || captured != 0 || enPassantCapture
        return Position(
            b,
            !whiteToMove,
            rights,
            newEp,
            if (reset) 0 else halfmove + 1,
            if (whiteToMove) fullmove else fullmove + 1,
        )
    }

    /** Neither side can possibly mate: bare kings, or a king and one minor piece against a king. */
    fun insufficientMaterial(): Boolean {
        val others = board.filter { it != 0 && abs(it) != Piece.KING }
        return others.isEmpty() || (others.size == 1 && abs(others[0]) in listOf(Piece.KNIGHT, Piece.BISHOP))
    }

    /** FEN without the move clocks, for spotting repeated positions. */
    val key: String get() = fen().split(" ").take(4).joinToString(" ")

    fun fen(): String {
        val rows = (7 downTo 0).joinToString("/") { r ->
            val sb = StringBuilder()
            var empty = 0
            for (f in 0..7) {
                val p = board[r * 8 + f]
                if (p == 0) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    val c = " pnbrqk"[abs(p)]
                    sb.append(if (p > 0) c.uppercaseChar() else c)
                }
            }
            if (empty > 0) sb.append(empty)
            sb.toString()
        }
        val rights = buildString {
            if (castling and WK != 0) append('K')
            if (castling and WQ != 0) append('Q')
            if (castling and BK != 0) append('k')
            if (castling and BQ != 0) append('q')
        }.ifEmpty { "-" }
        return "$rows ${if (whiteToMove) 'w' else 'b'} $rights ${if (ep >= 0) squareName(ep) else "-"} $halfmove $fullmove"
    }

    companion object {
        const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 4) { "Bad FEN: $fen" }
            val b = IntArray(64)
            val rows = parts[0].split("/")
            require(rows.size == 8) { "Bad FEN board: $fen" }
            rows.forEachIndexed { i, row ->
                val r = 7 - i
                var f = 0
                for (c in row) {
                    if (c.isDigit()) {
                        f += c - '0'
                    } else {
                        val kind = " pnbrqk".indexOf(c.lowercaseChar())
                        require(kind > 0) { "Bad piece '$c' in $fen" }
                        b[r * 8 + f] = if (c.isUpperCase()) kind else -kind
                        f++
                    }
                }
            }
            var rights = 0
            if ('K' in parts[2]) rights = rights or WK
            if ('Q' in parts[2]) rights = rights or WQ
            if ('k' in parts[2]) rights = rights or BK
            if ('q' in parts[2]) rights = rights or BQ
            val ep = if (parts[3] == "-") -1 else (parts[3][0] - 'a') + (parts[3][1] - '1') * 8
            return Position(
                b,
                parts[1] == "w",
                rights,
                ep,
                parts.getOrNull(4)?.toIntOrNull() ?: 0,
                parts.getOrNull(5)?.toIntOrNull() ?: 1,
            )
        }

        fun start(): Position = fromFen(START)
    }
}

/** How a game stands. */
enum class ChessResult { ONGOING, WHITE_WINS, BLACK_WINS, STALEMATE, FIFTY_MOVES, REPETITION, NO_MATERIAL }

/** A game: the positions so far, for take-backs, repetition, and saving. */
class ChessGame private constructor(private val positions: List<Position>, val moves: List<Move>) {
    val position: Position get() = positions.last()

    fun play(move: Move): ChessGame {
        require(move in position.legalMoves()) { "Illegal move ${move.uci}" }
        return ChessGame(positions + position.play(move), moves + move)
    }

    /** Takes back the last [count] moves. */
    fun takeBack(count: Int): ChessGame {
        val n = count.coerceAtMost(moves.size)
        return ChessGame(positions.dropLast(n), moves.dropLast(n))
    }

    val result: ChessResult
        get() {
            val p = position
            if (p.legalMoves().isEmpty()) {
                return if (p.inCheck()) (if (p.whiteToMove) ChessResult.BLACK_WINS else ChessResult.WHITE_WINS) else ChessResult.STALEMATE
            }
            if (p.halfmove >= 100) return ChessResult.FIFTY_MOVES
            if (p.insufficientMaterial()) return ChessResult.NO_MATERIAL
            if (positions.count { it.key == p.key } >= 3) return ChessResult.REPETITION
            return ChessResult.ONGOING
        }

    /** The moves as text, for saving: "e2e4 e7e5 ...". */
    fun save(): String = moves.joinToString(" ") { it.uci }

    companion object {
        fun new(): ChessGame = ChessGame(listOf(Position.start()), emptyList())

        /** Replays saved moves; stops at the first one that no longer fits. */
        fun load(saved: String): ChessGame {
            var game = new()
            for (text in saved.split(" ").filter { it.length >= 4 }) {
                val move = game.position.legalMoves().firstOrNull { it.uci == text } ?: break
                game = game.play(move)
            }
            return game
        }
    }
}
