package com.saiyanhabits.ui.games

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.data.PlayResult
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.MiniGame
import com.saiyanhabits.domain.games.ChessAi
import com.saiyanhabits.domain.games.ChessGame
import com.saiyanhabits.domain.games.ChessLevel
import com.saiyanhabits.domain.games.ChessResult
import com.saiyanhabits.domain.games.Move
import com.saiyanhabits.domain.games.Piece
import com.saiyanhabits.domain.games.squareName
import com.saiyanhabits.sound.LocalSoundPlayer
import com.saiyanhabits.sound.SoundKey
import com.saiyanhabits.ui.components.ChoiceTile
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.DbzPrimaryButton
import com.saiyanhabits.ui.components.DbzSecondaryButton
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.OutlinedText
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

private val LightSquare = Color(0xFFF1DFAE)
private val DarkSquare = Color(0xFF3D6CC4)

/** Filled chess glyphs, forced to text style so phones don't draw them as emoji. */
private fun glyph(piece: Int): String = "\u0000♟♞♝♜♛♚"[abs(piece)].toString() + "︎"

private fun prefs(context: Context) = context.getSharedPreferences("chess", Context.MODE_PRIVATE)

/** Roshi's game: chess. You play white. */
@Composable
fun ChessScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SaiyanApp
    val sounds = LocalSoundPlayer.current
    val scope = rememberCoroutineScope()
    val saved = remember { prefs(context) }
    var level by remember { mutableStateOf(saved.getString("level", null)?.let { n -> ChessLevel.entries.firstOrNull { it.name == n } }) }
    var game by remember { mutableStateOf(saved.getString("moves", null)?.let { ChessGame.load(it) } ?: ChessGame.new()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<Move?>(null) }
    var promoting by remember { mutableStateOf<List<Move>?>(null) }
    var say by remember { mutableStateOf<String?>(null) }
    var resigned by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var playResult by remember { mutableStateOf<PlayResult?>(null) }
    var wins by remember { mutableStateOf(0L) }
    var confirmResign by remember { mutableStateOf(false) }
    val dialogue by produceState<com.saiyanhabits.domain.MentorDialogue?>(null) { value = app.dialogue.all()[Mentor.ROSHI] }

    fun save() = saved.edit {
        putString("moves", game.save())
        putString("level", level?.name)
    }

    fun clearSave() = saved.edit { remove("moves"); remove("level") }

    val result = if (resigned) ChessResult.BLACK_WINS else game.result
    val over = result != ChessResult.ONGOING

    // Roshi replies on a background thread.
    LaunchedEffect(game, level) {
        val lvl = level ?: return@LaunchedEffect
        if (game.position.whiteToMove || game.result != ChessResult.ONGOING || resigned) return@LaunchedEffect
        thinking = true
        val move = withContext(Dispatchers.Default) { ChessAi.choose(game.position, lvl, Random(System.nanoTime())) }
        thinking = false
        if (move != null) {
            val capture = game.position[move.to] != 0
            game = game.play(move)
            save()
            if (capture) sounds?.play(SoundKey.BOSS_HIT, 0.5f)
            if (Random.nextFloat() < 0.25f) say = dialogue?.game?.drop(2)?.randomOrNull()
        }
    }

    // When a game ends: record it once and say something.
    LaunchedEffect(over) {
        if (!over || recorded || level == null) return@LaunchedEffect
        recorded = true
        val won = result == ChessResult.WHITE_WINS
        val drawn = result != ChessResult.WHITE_WINS && result != ChessResult.BLACK_WINS
        say = when {
            won -> dialogue?.gameDone?.randomOrNull()
            drawn -> "A draw! Ho ho, we're too evenly matched."
            else -> dialogue?.gameLost?.randomOrNull()
        }
        if (won) sounds?.play(SoundKey.VICTORY)
        clearSave()
        playResult = app.gameRepository.record(MiniGame.CHESS, if (won) 1 else 0, "${level!!.name}:${result.name}")
        wins = app.gameRepository.bests.first()[MiniGame.CHESS] ?: 0
    }

    fun newGame(chosen: ChessLevel) {
        level = chosen
        game = ChessGame.new()
        selected = null
        hint = null
        resigned = false
        recorded = false
        playResult = null
        say = dialogue?.game?.firstOrNull()
        save()
    }

    fun tap(sq: Int) {
        if (over || thinking || !game.position.whiteToMove) return
        hint = null
        val piece = game.position[sq]
        val from = selected
        if (from != null) {
            val moves = game.position.legalMoves().filter { it.from == from && it.to == sq }
            when {
                moves.size > 1 -> { promoting = moves; return }
                moves.size == 1 -> {
                    val capture = piece != 0
                    game = game.play(moves.single())
                    selected = null
                    save()
                    sounds?.play(if (capture) SoundKey.BOSS_HIT else SoundKey.CHECK_OFF, 0.4f)
                    return
                }
            }
        }
        selected = if (piece > 0) sq else null
    }

    SubScreen(title = "Chess with Roshi", onBack = onBack) {
        val chosen = level
        if (chosen == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MentorPortrait(Mentor.ROSHI, Modifier.size(72.dp))
                Text(dialogue?.game?.firstOrNull() ?: "", color = Color.White, modifier = Modifier.padding(start = 14.dp))
            }
            TabLabel("How hard should Roshi try?")
            ChessLevel.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { lvl -> ChoiceTile(false, { newGame(lvl) }, lvl.title, lvl.caption, Modifier.weight(1f)) }
                }
            }
            Text("You play white. Your game saves itself, so you can stop and come back.", color = SaiyanColors.TextDim)
            return@SubScreen
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            MentorPortrait(Mentor.ROSHI, Modifier.size(48.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("Roshi · ${chosen.title}", style = MaterialTheme.typography.titleSmall, color = SaiyanColors.Gold)
                Text(
                    when {
                        thinking -> "Thinking..."
                        over -> ""
                        game.position.inCheck() && game.position.whiteToMove -> "Check! Your king is under attack."
                        else -> say ?: if (game.position.whiteToMove) "Your move." else ""
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Board(game, selected, hint, onTap = ::tap)

        if (over) {
            DbzPanel(Modifier.fillMaxWidth(), style = PanelStyle.Orange) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedText(
                        when (result) {
                            ChessResult.WHITE_WINS -> "YOU WIN!"
                            ChessResult.BLACK_WINS -> if (resigned) "RESIGNED" else "ROSHI WINS"
                            else -> "DRAW"
                        },
                        MaterialTheme.typography.headlineMedium,
                        outlineWidth = 4.dp,
                    )
                    Text(
                        when (result) {
                            ChessResult.STALEMATE -> "Stalemate: no legal moves, but no check."
                            ChessResult.FIFTY_MOVES -> "Fifty moves without a capture or pawn move."
                            ChessResult.REPETITION -> "The same position three times."
                            ChessResult.NO_MATERIAL -> "Neither side can mate any more."
                            else -> say ?: ""
                        },
                        color = Color.White,
                    )
                    Text("Wins against Roshi: $wins", color = SaiyanColors.Gold)
                    playResult?.let { r ->
                        Text(if (r.bondXp > 0) "+${r.bondXp} bond XP with Roshi" else "No more bond XP from games with Roshi today.", color = Color.White)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DbzSecondaryButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Done") }
                        DbzPrimaryButton(onClick = { level = null }, modifier = Modifier.weight(1f)) { Text("New game") }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DbzSecondaryButton(
                    onClick = {
                        // Back to your previous turn: your move and Roshi's reply.
                        val back = if (game.position.whiteToMove) 2 else 1
                        if (game.moves.size >= back) {
                            game = game.takeBack(back)
                            selected = null
                            hint = null
                            save()
                        }
                    },
                    enabled = !thinking && game.moves.size >= 2,
                    modifier = Modifier.weight(1f),
                ) { Text("Take back") }
                DbzSecondaryButton(
                    onClick = {
                        scope.launch {
                            thinking = true
                            hint = withContext(Dispatchers.Default) { ChessAi.hint(game.position) }
                            thinking = false
                            say = hint?.let { "Hmm. Try ${squareName(it.from)} to ${squareName(it.to)}." }
                        }
                    },
                    enabled = !thinking && game.position.whiteToMove,
                    modifier = Modifier.weight(1f),
                ) { Text("Hint") }
                DbzSecondaryButton(onClick = { confirmResign = true }, enabled = !thinking, modifier = Modifier.weight(1f)) { Text("Resign") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    promoting?.let { options ->
        AlertDialog(
            onDismissRequest = { promoting = null },
            title = { Text("Promote to") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    options.sortedByDescending { it.promotion }.forEach { move ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .background(LightSquare, RoundedCornerShape(8.dp))
                                .clickable {
                                    game = game.play(move)
                                    promoting = null
                                    selected = null
                                    save()
                                },
                        ) { PieceGlyph(move.promotion, 40) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { promoting = null }) { Text("Cancel") } },
        )
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text("Resign this game?") },
            text = { Text("Roshi takes the win.") },
            confirmButton = { TextButton(onClick = { resigned = true; confirmResign = false }) { Text("Resign", color = SaiyanColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmResign = false }) { Text("Keep playing") } },
        )
    }
}

@Composable
private fun Board(game: ChessGame, selected: Int?, hint: Move?, onTap: (Int) -> Unit) {
    val position = game.position
    val targets = selected?.let { s -> position.legalMoves().filter { it.from == s }.map { it.to }.toSet() }.orEmpty()
    val last = game.moves.lastOrNull()
    val checkedKing = if (position.inCheck()) position.kingSquare(position.whiteToMove) else -1
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(3.dp, SaiyanColors.FrameGold, RoundedCornerShape(4.dp)),
    ) {
        val cell = maxWidth / 8
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                val sq = rank * 8 + file
                val piece = position[sq]
                val light = (rank + file) % 2 == 1
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(x = cell * file, y = cell * (7 - rank))
                        .size(cell)
                        .background(
                            when {
                                sq == selected -> Color(0xFFFFB13B)
                                sq == checkedKing -> Color(0xFFE53935)
                                hint != null && (sq == hint.from || sq == hint.to) -> Color(0xFF8CFF6A)
                                last != null && (sq == last.from || sq == last.to) -> if (light) Color(0xFFF7E58A) else Color(0xFF6E8FD0)
                                light -> LightSquare
                                else -> DarkSquare
                            },
                        )
                        .semantics { contentDescription = squareName(sq) + if (piece != 0) " ${if (piece > 0) "white" else "black"} ${listOf("", "pawn", "knight", "bishop", "rook", "queen", "king")[abs(piece)]}" else "" }
                        .clickable { onTap(sq) },
                ) {
                    if (piece != 0) PieceGlyph(piece, (cell.value * 0.78f).toInt())
                    if (sq in targets) {
                        Box(
                            Modifier
                                .size(cell * (if (piece != 0) 0.9f else 0.3f))
                                .then(
                                    if (piece != 0) Modifier.border(3.dp, Color(0xAA0B1F5C), CircleShape)
                                    else Modifier.background(Color(0x660B1F5C), CircleShape),
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** A piece: white ones light with a dark outline, black ones dark with a light outline. */
@Composable
private fun PieceGlyph(piece: Int, sizeSp: Int) {
    val white = piece > 0
    OutlinedText(
        glyph(piece),
        MaterialTheme.typography.bodyLarge.copy(fontSize = sizeSp.sp, lineHeight = sizeSp.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Default),
        fill = Brush.verticalGradient(if (white) listOf(Color.White, Color(0xFFE0E0E0)) else listOf(Color(0xFF3A3A3A), Color(0xFF111111))),
        outline = if (white) Color(0xFF1B1200) else Color(0xFFEDEDED),
        outlineWidth = 2.dp,
    )
}
