@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.saiyanhabits.ui.cards

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.key
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.art.ArtKey
import com.saiyanhabits.data.CardMatchSetup
import com.saiyanhabits.data.ChallengerToday
import com.saiyanhabits.data.LossOutcome
import com.saiyanhabits.data.RedoOption
import com.saiyanhabits.domain.CaptureKind
import com.saiyanhabits.domain.CardAi
import com.saiyanhabits.domain.CardCollecting
import com.saiyanhabits.domain.CardDef
import com.saiyanhabits.domain.CardRules
import com.saiyanhabits.domain.CardRulesEngine
import com.saiyanhabits.domain.Challenger
import com.saiyanhabits.domain.MatchState
import com.saiyanhabits.domain.Side
import com.saiyanhabits.sound.LocalSoundPlayer
import com.saiyanhabits.sound.SoundKey
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.DbzPrimaryButton
import com.saiyanhabits.ui.components.DbzSecondaryButton
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.OutlinedText
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Where the nine squares are in the board picture (art key card_board, 1086 x 1448),
 * row by row: top, bottom, left edge, right edge. The board leans back, so rows narrow towards the top.
 */
private val CARD_BOARD_ROWS = listOf(
    floatArrayOf(380f, 603f, 178f, 898f),
    floatArrayOf(622f, 848f, 158f, 925f),
    floatArrayOf(866f, 1112f, 130f, 955f),
)
private const val BOARD_W = 1086f
private const val BOARD_H = 1448f

/** Square [i]'s box in the picture: left, top, width, height. */
private fun cellBox(i: Int): FloatArray {
    val (top, bottom, left, right) = CARD_BOARD_ROWS[i / 3].let { listOf(it[0], it[1], it[2], it[3]) }
    val w = (right - left) / 3
    val inset = 12f
    return floatArrayOf(left + (i % 3) * w + inset, top + inset * 0.6f, w - inset * 2, bottom - top - inset * 1.2f)
}

/** What the card battle section is showing. */
private sealed interface CardScreen {
    data object Hub : CardScreen
    data object Collection : CardScreen
    data class PickHand(val challenger: Challenger) : CardScreen
    data class Playing(val setup: CardMatchSetup, val hand: List<CardDef>) : CardScreen
    data class Won(val setup: CardMatchSetup) : CardScreen
    data class Lost(val setup: CardMatchSetup, val hand: List<CardDef>) : CardScreen
}

/** The card battle section: today's challenger, the daily pack, your collection, and matches. */
@Composable
fun CardBattleScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val repo = app.cardRepository
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<CardScreen>(CardScreen.Hub) }
    var refresh by remember { mutableStateOf(0) }
    val collection by repo.collection.collectAsStateWithLifecycle(initialValue = emptyMap())
    val book by produceState<com.saiyanhabits.domain.CardBook?>(null) { value = repo.book() }
    var newCards by remember { mutableStateOf<Pair<String, List<CardDef>>?>(null) }

    LaunchedEffect(Unit) {
        val given = repo.sync()
        if (given.isNotEmpty()) newCards = "Unlocked by your bonds" to given
    }

    newCards?.let { (title, cards) ->
        NewCardsDialog(title, cards, onDone = { newCards = null })
        return
    }
    val b = book ?: return
    when (val s = screen) {
        CardScreen.Hub -> CardHub(
            refresh = refresh,
            collectionSize = collection.values.sum(),
            onChallenge = { screen = CardScreen.PickHand(it) },
            onCollection = { screen = CardScreen.Collection },
            onOpenPack = {
                scope.launch {
                    repo.openPack()?.let { newCards = "Today's pack" to it }
                    refresh++
                }
            },
            onBack = onBack,
        )
        CardScreen.Collection -> CollectionScreen(b, collection, onBack = { screen = CardScreen.Hub })
        is CardScreen.PickHand -> PickHand(
            book = b,
            collection = collection,
            challenger = s.challenger,
            onBack = { screen = CardScreen.Hub },
            onStart = { hand ->
                scope.launch { screen = CardScreen.Playing(repo.setUp(s.challenger), hand) }
            },
        )
        is CardScreen.Playing -> MatchScreen(
            setup = s.setup,
            playerHand = s.hand,
            onQuit = { screen = CardScreen.Hub },
            onEnd = { state ->
                val mine = state.score(Side.PLAYER)
                val theirs = state.score(Side.OPPONENT)
                screen = when {
                    mine > theirs -> CardScreen.Won(s.setup)
                    mine < theirs -> CardScreen.Lost(s.setup, s.hand)
                    else -> {
                        scope.launch { repo.recordDraw(s.setup) }
                        CardScreen.PickHand(s.setup.challenger)
                    }
                }
            },
        )
        is CardScreen.Won -> if (!s.setup.stakes) {
            FriendlyWin(s.setup, onDone = {
                scope.launch {
                    repo.recordWin(s.setup, null)
                    refresh++
                    screen = CardScreen.Hub
                }
            })
        } else WonScreen(s.setup, onPick = { card ->
            scope.launch {
                repo.recordWin(s.setup, card)
                refresh++
                screen = CardScreen.Hub
            }
        })
        is CardScreen.Lost -> LostScreen(s.setup, s.hand, onDone = {
            refresh++
            screen = CardScreen.Hub
        })
    }
}

@Composable
private fun ChallengerBadge(challenger: Challenger, size: Int) {
    val mentor = challenger.mentor
    if (mentor != null) {
        MentorPortrait(mentor, Modifier.size(size.dp))
        return
    }
    Box(
        Modifier
            .size(size.dp)
            .background(Brush.radialGradient(listOf(Color(0xFFFFB300), Color(0xFFB71C1C))), CircleShape)
            .border(3.dp, SaiyanColors.FrameGold, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedText(
            challenger.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) },
            TextStyle(fontSize = (size / 2.6).sp, fontWeight = FontWeight.Black),
            outlineWidth = 3.dp,
        )
    }
}

@Composable
private fun CardHub(
    refresh: Int,
    collectionSize: Int,
    onChallenge: (Challenger) -> Unit,
    onCollection: () -> Unit,
    onOpenPack: () -> Unit,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val repo = app.cardRepository
    val today by produceState<ChallengerToday?>(null, refresh) { value = repo.challengerToday() }
    val packWaiting by produceState(false, refresh) { value = repo.packWaiting() }
    val todayComplete by produceState(false, refresh) { value = repo.todayComplete() }
    val pending by repo.pending.collectAsStateWithLifecycle(initialValue = emptyList())
    val book by produceState<com.saiyanhabits.domain.CardBook?>(null) { value = repo.book() }
    var showHelp by remember { mutableStateOf(false) }
    if (showHelp) CardHelpDialog(onDismiss = { showHelp = false })
    SubScreen(title = "Card Battle", onBack = onBack, actions = { Box(Modifier.padding(end = 8.dp)) { HelpButton(onClick = { showHelp = true }) } }) {
        val t = today
        if (t != null) {
            DbzPanel(style = PanelStyle.Orange, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChallengerBadge(t.challenger, 64)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            TabLabel("Today's challenger")
                            Text(t.challenger.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text(t.challenger.difficulty.label, style = MaterialTheme.typography.bodySmall, color = SaiyanColors.Gold)
                        }
                    }
                    Text(
                        "“${t.challenger.intro.first()}”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    Text(
                        "Rules: " + rulesLabel(CardRules.forDifficulty(t.challenger.difficulty)),
                        style = MaterialTheme.typography.bodySmall,
                        color = SaiyanColors.TextDim,
                    )
                    Text(
                        when {
                            t.stakes ->
                                "Win: pick any card from their deck, including ${book?.card(t.challenger.signature)?.name ?: "their signature card"}. " +
                                    "Lose: they take your best card, or you redo one of today's habits to keep it."
                            t.wonToday -> "You beat them today and took a card. They're your challenger until tomorrow: rematches are just for fun."
                            else -> "They took a card from you today. Rematches are just for fun until tomorrow, when you can win it back."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SaiyanColors.TextHigh,
                    )
                    if (t.holding.isNotEmpty()) {
                        Text(
                            "They're holding your ${t.holding.joinToString { it.name }}. They'll play it: win it back.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SaiyanColors.Danger,
                        )
                    }
                    DbzPrimaryButton(
                        onClick = { onChallenge(t.challenger) },
                        enabled = collectionSize >= CardCollecting.HAND_SIZE,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Challenge ${t.challenger.name}") }
                }
            }
        }
        DbzPanel(style = if (packWaiting) PanelStyle.Selected else PanelStyle.Blue, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TabLabel("Daily pack")
                Text(
                    when {
                        packWaiting -> "Every habit done today. Your pack is ready: five random cards."
                        todayComplete -> "Today's pack is opened. Finish every habit tomorrow for another."
                        else -> "Finish every habit that counts today to earn a pack of five cards."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                if (packWaiting) DbzPrimaryButton(onClick = onOpenPack, modifier = Modifier.fillMaxWidth()) { Text("Open pack") }
            }
        }
        if (pending.isNotEmpty()) {
            DbzPanel(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TabLabel("Cards on the line")
                    pending.forEach { p ->
                        val habit by produceState<String?>(null, p.habitId) { value = app.habitRepository.getHabit(p.habitId)?.name }
                        Text(
                            "${book?.card(p.cardId)?.name ?: p.cardId}: check off “${habit ?: "the habit"}” again today to keep it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SaiyanColors.Gold,
                        )
                    }
                }
            }
        }
        DbzSecondaryButton(onClick = onCollection, modifier = Modifier.fillMaxWidth()) { Text("Your collection ($collectionSize cards)") }
        Spacer(Modifier.height(8.dp))
    }
}

private fun rulesLabel(r: CardRules): String =
    listOfNotNull("Open", "Same".takeIf { r.same }, "Plus".takeIf { r.plus }, "Combo".takeIf { r.combo }, "Elemental".takeIf { r.elemental })
        .joinToString(" · ")

/** Choosing five cards to play. */
@Composable
private fun PickHand(
    book: com.saiyanhabits.domain.CardBook,
    collection: Map<String, Int>,
    challenger: Challenger,
    onBack: () -> Unit,
    onStart: (List<CardDef>) -> Unit,
) {
    BackHandler(onBack = onBack)
    // One entry per copy owned, strongest first.
    val copies = collection.flatMap { (id, n) -> List(n) { book.card(id) } }.filterNotNull().sortedByDescending { it.strength }
    val chosen = remember { mutableStateListOf<Int>() }
    SubScreen(title = "Pick 5 cards", onBack = onBack) {
        Text("Against ${challenger.name}. Tap five cards to take into the match.", style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DbzSecondaryButton(onClick = {
                chosen.clear()
                chosen.addAll(copies.indices.take(CardCollecting.HAND_SIZE))
            }, modifier = Modifier.weight(1f)) { Text("Best five") }
            DbzPrimaryButton(
                onClick = { onStart(chosen.map { copies[it] }) },
                enabled = chosen.size == CardCollecting.HAND_SIZE,
                modifier = Modifier.weight(1f),
            ) { Text(if (chosen.size == CardCollecting.HAND_SIZE) "Fight!" else "${chosen.size} / 5") }
        }
        CardGrid(copies, selected = chosen.toSet(), onTap = { i -> if (i in chosen) chosen.remove(i) else if (chosen.size < 5) chosen.add(i) })
    }
}

@Composable
private fun CardGrid(cards: List<CardDef>, selected: Set<Int> = emptySet(), dimmed: Set<Int> = emptySet(), onTap: ((Int) -> Unit)? = null) {
    var detail by remember { mutableStateOf<CardDef?>(null) }
    detail?.let { CardDetailDialog(it, onDismiss = { detail = null }) }
    cards.indices.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(0.8f)
                        .combinedClickable(onLongClick = { detail = cards[i] }, onClick = { onTap?.invoke(i) ?: run { detail = cards[i] } }),
                ) {
                    CardFace(cards[i], owner = null, selected = i in selected, modifier = Modifier.fillMaxSize())
                    if (i in dimmed) Box(Modifier.fillMaxSize().background(Color(0xAA000000), RoundedCornerShape(8.dp)))
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Every card in the game: the ones you own, and the ones still to find. */
@Composable
private fun CollectionScreen(book: com.saiyanhabits.domain.CardBook, collection: Map<String, Int>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    SubScreen(title = "Collection", onBack = onBack) {
        val owned = book.cards.count { (collection[it.id] ?: 0) > 0 }
        Text("$owned of ${book.cards.size} found. Dimmed cards are still out there.", style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextDim)
        val groups = listOf(
            "Signature cards: beat their challenger" to book.cards.filter { it.source == com.saiyanhabits.domain.CardSource.SIGNATURE },
            "Bond cards: bond 5 and 10 with each mentor" to book.cards.filter { it.source == com.saiyanhabits.domain.CardSource.BOND },
        ) + com.saiyanhabits.domain.CardRarity.entries.take(4).reversed().map { r ->
            r.label to book.packCards.filter { it.rarity == r }
        }
        groups.forEach { (title, cards) ->
            SectionLabel("$title (${cards.count { (collection[it.id] ?: 0) > 0 }}/${cards.size})")
            CardGrid(cards, dimmed = cards.indices.filter { (collection[cards[it].id] ?: 0) == 0 }.toSet())
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The match: the board picture with the cards on it, the challenger on the left, your hand below. */
@Composable
private fun MatchScreen(setup: CardMatchSetup, playerHand: List<CardDef>, onQuit: () -> Unit, onEnd: (MatchState) -> Unit) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val sounds = LocalSoundPlayer.current
    val random = remember { Random(System.nanoTime()) }
    val rules = remember { CardRules.forDifficulty(setup.challenger.difficulty) }
    var state by remember {
        mutableStateOf(MatchState.start(playerHand, setup.hand, rules, if (random.nextBoolean()) Side.PLAYER else Side.OPPONENT, random))
    }
    var picked by remember { mutableStateOf<Int?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var says by remember { mutableStateOf(setup.challenger.intro.random(random)) }
    var flashed by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var confirmQuit by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    if (showHelp) CardHelpDialog(onDismiss = { showHelp = false })
    var detail by remember { mutableStateOf<CardDef?>(null) }
    detail?.let { CardDetailDialog(it, onDismiss = { detail = null }) }
    val boardArt by produceState<ImageBitmap?>(null) { value = app.art.image(ArtKey.CARD_BOARD) }
    BackHandler { confirmQuit = true }

    fun apply(card: CardDef, square: Int) {
        val r = CardRulesEngine.play(state, card, square)
        state = r.state
        flashed = r.flips.map { it.first }.toSet()
        val kinds = r.flips.map { it.second }.toSet()
        banner = when {
            CaptureKind.SAME in kinds -> "Same!"
            CaptureKind.PLUS in kinds -> "Plus!"
            CaptureKind.COMBO in kinds -> "Combo!"
            else -> null
        }
        if (r.flips.isNotEmpty()) {
            sounds?.play(SoundKey.CHECK_OFF, 0.5f)
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }

    // The challenger's turns, and the end of the match.
    LaunchedEffect(state.turn, state.isOver) {
        if (state.isOver) {
            delay(1200)
            onEnd(state)
            return@LaunchedEffect
        }
        if (state.turn == Side.OPPONENT) {
            delay(700)
            val move = withContext(Dispatchers.Default) { CardAi.choose(state, setup.challenger.difficulty, random) }
            apply(move.first, move.second)
        }
    }
    // Who won the coin toss.
    LaunchedEffect(Unit) {
        banner = if (state.turn == Side.PLAYER) "You go first!" else "${setup.challenger.name.split(" ").first()} goes first"
    }
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(1100)
            banner = null
        }
    }

    if (confirmQuit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Leave the match?") },
            text = { Text("Nothing is won or lost if you leave now.") },
            confirmButton = { TextButton(onClick = onQuit) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Keep playing") } },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3F7FD9), Color(0xFF9E9AB8), Color(0xFF6E6A86)))),
    ) {
        Column(Modifier.fillMaxSize()) {
            // The challenger, what they say, the score and help: above the board, never over it.
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChallengerBadge(setup.challenger, 44)
                Spacer(Modifier.width(8.dp))
                Text(
                    says,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xDD0B1F5C), RoundedCornerShape(10.dp))
                        .border(1.dp, SaiyanColors.FrameGold, RoundedCornerShape(10.dp))
                        .padding(6.dp),
                )
                Spacer(Modifier.width(6.dp))
                HelpButton(onClick = { showHelp = true })
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScoreChip("YOU", state.score(Side.PLAYER), Color(0xFF1E5BD8))
                Spacer(Modifier.width(6.dp))
                ScoreChip(initials(setup.challenger.name), state.score(Side.OPPONENT), Color(0xFFC62828))
                Spacer(Modifier.weight(1f))
                Text(rulesLabel(rules), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            // The board picture, with the cards on its squares.
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                val scale = minOf(constraints.maxWidth / BOARD_W, constraints.maxHeight / BOARD_H)
                val density = LocalDensity.current
                val w = with(density) { (BOARD_W * scale).toDp() }
                val h = with(density) { (BOARD_H * scale).toDp() }
                Box(Modifier.size(w, h)) {
                    boardArt?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
                    for (i in 0 until 9) {
                        val (x, y, cw, ch) = cellBox(i).toList()
                        val mod = Modifier
                            .offset(with(density) { (x * scale).toDp() }, with(density) { (y * scale).toDp() })
                            .size(with(density) { (cw * scale).toDp() }, with(density) { (ch * scale).toDp() })
                        val placed = state.board[i]
                        val element = state.elements[i]
                        val canDrop = placed == null && picked != null && state.turn == Side.PLAYER && !state.isOver
                        Box(
                            mod.then(
                                if (canDrop) {
                                    Modifier.clickable {
                                        val card = state.playerHand[picked!!]
                                        picked = null
                                        apply(card, i)
                                    }
                                } else Modifier,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (placed == null && element != null) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(element.color.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                        .border(2.dp, element.color, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    OutlinedText(element.label, MaterialTheme.typography.labelLarge, outlineWidth = 3.dp)
                                }
                            }
                            if (canDrop) {
                                Box(Modifier.fillMaxSize().border(3.dp, Color(0xFFFFEB3B), RoundedCornerShape(10.dp)))
                            }
                            if (placed != null) {
                                // A new card drops in; a captured one flips over to its new owner.
                                key(placed.card.id, placed.owner) {
                                    val flip = i in flashed
                                    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
                                    LaunchedEffect(Unit) {
                                        anim.animateTo(1f, androidx.compose.animation.core.tween(if (flip) 420 else 260))
                                    }
                                    val density2 = LocalDensity.current.density
                                    CardFace(
                                        placed.card,
                                        placed.owner,
                                        selected = i in flashed,
                                        showName = false,
                                        tag = if (placed.owner == Side.PLAYER) "YOU" else initials(setup.challenger.name),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                cameraDistance = 14f * density2
                                                if (flip) {
                                                    rotationY = (1f - anim.value) * 90f
                                                } else {
                                                    val s = 1.25f - 0.25f * anim.value
                                                    scaleX = s
                                                    scaleY = s
                                                    alpha = 0.4f + 0.6f * anim.value
                                                }
                                            },
                                    )
                                }
                            }
                        }
                    }
                    banner?.let {
                        OutlinedText(
                            it,
                            TextStyle(fontSize = 42.sp, fontWeight = FontWeight.Black),
                            outlineWidth = 5.dp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
            // Their cards, small and face up.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${setup.challenger.name.split(" ").first()}:", style = MaterialTheme.typography.labelMedium, color = Color.White)
                state.opponentHand.forEach { c ->
                    CardFace(c, Side.OPPONENT, showName = false, modifier = Modifier.size(40.dp, 50.dp))
                }
            }
            // Whose turn.
            val label = when {
                state.isOver -> when {
                    state.score(Side.PLAYER) > state.score(Side.OPPONENT) -> "You win!"
                    state.score(Side.PLAYER) < state.score(Side.OPPONENT) -> "${setup.challenger.name} wins"
                    else -> "Draw"
                }
                state.turn == Side.PLAYER -> if (picked == null) "Your Turn: pick a card" else "Your Turn: tap a square"
                else -> "${setup.challenger.name} is thinking…"
            }
            OutlinedText(
                label,
                TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black),
                outlineWidth = 4.dp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xCC0D47A1), Color.Transparent)))
                    .padding(horizontal = 32.dp, vertical = 2.dp),
            )
            // Your hand.
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.playerHand.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.8f)
                            .offset(y = if (picked == i) (-10).dp else 0.dp)
                            .combinedClickable(
                                enabled = state.turn == Side.PLAYER && !state.isOver,
                                onLongClick = { detail = c },
                                onClick = { picked = if (picked == i) null else i },
                            ),
                    ) {
                        CardFace(c, Side.PLAYER, selected = picked == i, modifier = Modifier.fillMaxSize())
                    }
                }
                repeat(CardCollecting.HAND_SIZE - state.playerHand.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    LaunchedEffect(state.isOver) {
        if (state.isOver) {
            val won = state.score(Side.PLAYER) > state.score(Side.OPPONENT)
            says = if (won) setup.challenger.win.random(random) else if (state.score(Side.PLAYER) < state.score(Side.OPPONENT)) setup.challenger.lose.random(random) else says
        }
    }
}

/** You won: pick one card from their deck. */
@Composable
private fun WonScreen(setup: CardMatchSetup, onPick: (CardDef) -> Unit) {
    var chosen by remember { mutableStateOf<Int?>(null) }
    val cards = remember { setup.deck.distinctBy { it.id }.sortedByDescending { it.strength } }
    SubScreen(title = "You win!", onBack = {}) {
        Text("“${setup.challenger.win.first()}”", style = MaterialTheme.typography.bodyLarge, color = Color.White)
        Text("Pick one card from ${setup.challenger.name}'s deck to keep.", style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.Gold)
        CardGrid(cards, selected = setOfNotNull(chosen), onTap = { chosen = it })
        DbzPrimaryButton(onClick = { chosen?.let { onPick(cards[it]) } }, enabled = chosen != null, modifier = Modifier.fillMaxWidth()) {
            Text(chosen?.let { "Take ${cards[it].name}" } ?: "Pick a card")
        }
    }
}

/** You lost: give up your best card, or redo a habit to keep it. */
@Composable
private fun LostScreen(setup: CardMatchSetup, hand: List<CardDef>, onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val repo = app.cardRepository
    val scope = rememberCoroutineScope()
    val atStake by produceState<CardDef?>(null) { value = repo.cardAtStake(setup, hand) }
    val loaded by produceState(false) { repo.cardAtStake(setup, hand); value = true }
    val options by produceState<List<RedoOption>>(emptyList()) { value = repo.redoOptions() }
    var outcome by remember { mutableStateOf<LossOutcome?>(null) }
    SubScreen(title = "${setup.challenger.name} wins", onBack = {}) {
        Text("“${setup.challenger.lose.first()}”", style = MaterialTheme.typography.bodyLarge, color = Color.White)
        val card = atStake
        when {
            outcome != null -> {
                Text(
                    when (val o = outcome) {
                        is LossOutcome.Taken -> "${setup.challenger.name} took your ${o.card.name}. They'll play it every time you face them, so you can win it back."
                        is LossOutcome.Pending -> "Your ${o.card.name} is on the line. Check off “${o.habitName}” again before midnight to keep it."
                        else -> "Nothing changed hands."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SaiyanColors.Gold,
                )
                DbzPrimaryButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
            !loaded -> Unit
            card == null -> {
                Text(
                    if (setup.stakes) "You need to keep at least ${CardCollecting.MIN_COLLECTION} cards, so nothing is taken."
                    else "They already took a card today, so nothing changes hands this time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SaiyanColors.TextDim,
                )
                DbzPrimaryButton(onClick = {
                    scope.launch { outcome = repo.recordLoss(setup, hand, null) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Okay") }
            }
            else -> {
                Text("They want your best card:", style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextDim)
                Box(Modifier.size(120.dp, 150.dp).align(Alignment.CenterHorizontally)) {
                    CardFace(card, Side.PLAYER, modifier = Modifier.fillMaxSize())
                }
                DbzSecondaryButton(onClick = {
                    scope.launch { outcome = repo.recordLoss(setup, hand, null) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Hand it over") }
                if (options.isNotEmpty()) {
                    SectionLabel("Or redo a habit to keep it")
                    Text(
                        "The habit is unticked. Check it off again before midnight and the card stays yours; if not, they take it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SaiyanColors.TextDim,
                    )
                    options.forEach { o ->
                        DbzPrimaryButton(onClick = {
                            scope.launch { outcome = repo.recordLoss(setup, hand, o) }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Redo “${o.name}”") }
                    }
                } else {
                    Text("No habits checked off today to redo.", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
                }
            }
        }
    }
}

/** New cards: from a pack, or unlocked by bonds. */
@Composable
private fun NewCardsDialog(title: String, cards: List<CardDef>, onDone: () -> Unit) {
    // Cards start face down and turn over one by one; tap one to turn it early.
    val turned = remember { mutableStateListOf<Int>() }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(cards) {
        delay(500)
        for (i in cards.indices) {
            if (i !in turned) turned += i
            if (cards[i].rarity.rank >= com.saiyanhabits.domain.CardRarity.EPIC.rank) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            }
            delay(if (cards[i].rarity.rank >= com.saiyanhabits.domain.CardRarity.EPIC.rank) 900 else 450)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1F5C), Color(0xFF3B1060))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedText(title, TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black), outlineWidth = 4.dp)
        cards.indices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { i ->
                    val c = cards[i]
                    val shown = i in turned
                    val angle by androidx.compose.animation.core.animateFloatAsState(
                        if (shown) 0f else 180f,
                        androidx.compose.animation.core.tween(500),
                        label = "turn",
                    )
                    val density = LocalDensity.current.density
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.8f)
                                .graphicsLayer {
                                    rotationY = angle
                                    cameraDistance = 14f * density
                                }
                                .clickable { if (i !in turned) turned += i },
                        ) {
                            if (angle <= 90f) {
                                CardFace(c, null, modifier = Modifier.fillMaxSize())
                            } else {
                                CardBack(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f })
                            }
                        }
                        Text(
                            if (shown) c.rarity.label else " ",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.rarity.color,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        DbzPrimaryButton(onClick = onDone, enabled = turned.size == cards.size, modifier = Modifier.fillMaxWidth()) {
            Text(if (turned.size == cards.size) "Nice!" else "Revealing…")
        }
    }
}

/** One card up close: long-press any card to see it. */
@Composable
private fun CardDetailDialog(card: CardDef, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(card.name) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(200.dp, 250.dp)) { CardFace(card, null, modifier = Modifier.fillMaxSize()) }
                Text(
                    "${card.rarity.label} · ${card.element.label}",
                    style = MaterialTheme.typography.titleSmall,
                    color = card.rarity.color,
                )
                Text(
                    "Top ${sideLabel(card.top)} · Right ${sideLabel(card.right)} · Bottom ${sideLabel(card.bottom)} · Left ${sideLabel(card.left)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    when (card.source) {
                        com.saiyanhabits.domain.CardSource.PACK -> "Found in daily packs and challengers' decks."
                        com.saiyanhabits.domain.CardSource.SIGNATURE -> "A signature card: only won by beating its challenger."
                        com.saiyanhabits.domain.CardSource.BOND ->
                            if (card.mentor == "ALL") "Unlocked when every mentor reaches bond ${card.bondLevel}."
                            else "Unlocked at bond ${card.bondLevel} with ${card.mentor?.lowercase()?.replaceFirstChar { it.uppercase() }}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SaiyanColors.TextDim,
                )
            }
        },
    )
}

/** The home screen's way in: a pack waiting, or today's challenger. */
@Composable
fun CardBattleHomeCard(onClick: () -> Unit) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val repo = app.cardRepository
    val today by produceState<ChallengerToday?>(null) { value = repo.challengerToday() }
    val pack by produceState(false) { value = repo.packWaiting() }
    DbzPanel(onClick = onClick, style = if (pack) PanelStyle.Selected else PanelStyle.Blue, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            today?.let { ChallengerBadge(it.challenger, 44) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                TabLabel("Card Battle")
                Text(
                    if (pack) "Your daily pack is ready to open!"
                    else today?.let { "${it.challenger.name} is today's challenger." } ?: "Collect cards and take on challengers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }
    }
}


private fun initials(name: String): String =
    name.split(" ", ".").filter { it.isNotBlank() && it[0].isLetter() }.take(2).joinToString("") { it.take(1).uppercase() }

@Composable
private fun ScoreChip(label: String, score: Int, color: Color) {
    Row(
        Modifier
            .background(color, RoundedCornerShape(50))
            .border(1.dp, Color.White, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
        Spacer(Modifier.width(6.dp))
        Text("$score", style = MaterialTheme.typography.titleSmall, color = Color.White)
    }
}

@Composable
private fun HelpButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .background(Color(0xFF1565C0), CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .clickable(onClickLabel = "How to play", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("?", style = MaterialTheme.typography.titleMedium, color = Color.White) }
}

/** How card battles work. */
@Composable
fun CardHelpDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        title = { Text("How to play") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HelpPara("The match", "You and the challenger take turns placing one of your five cards on an empty square. Most cards owned when the board is full wins (cards left in your hand count too).")
                HelpPara("Whose card is whose", "Your cards are blue with a YOU tag in the corner. The challenger's are red with their initials. When a card is captured, its colour and tag change to its new owner. The chips at the top keep the score.")
                HelpPara("Capturing", "Each card has four numbers, one per side (A is 10). Place a card next to an opponent's card: if your number on the touching side is higher than theirs, their card becomes yours.")
                HelpPara("Elemental", "Some squares are charged with an element (Ki, Strength, Spirit or Tech). A card of the same element gets +1 on every side there; any other card gets -1. The card's element is the letter in its top corner.")
                HelpPara("Same", "If two or more of your card's touching numbers are exactly equal to the numbers they touch, all those opponent cards are captured.")
                HelpPara("Plus", "If two or more touching pairs add up to the same total (your side + their side), all those opponent cards are captured.")
                HelpPara("Combo", "Cards captured by Same or Plus then capture their own weaker neighbours, which can chain across the board.")
                HelpPara("Open", "Both hands are face up, so you can plan around the challenger's cards.")
                HelpPara("Which rules", "Easy challengers play Open and Elemental. Medium add Same and Combo. Hard and very hard add Plus.")
                HelpPara("Winning and losing", "One challenger a day, all day. Your first win against them earns one card from their deck, including their signature card. If they win, they take your best card from the hand you played, unless you redo one of today's habits and check it off again before midnight. Once a card has changed hands either way, rematches that day are just for fun.")
                HelpPara("Getting cards back", "A challenger always plays the cards they've taken from you. Beat them and you can pick yours back.")
                HelpPara("Getting new cards", "Finish every habit that counts today for a pack of five. Bond cards unlock at bond 5 and 10 with each mentor, and mentors become challengers at bond 9.")
            }
        },
    )
}

@Composable
private fun HelpPara(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = SaiyanColors.Gold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}


/** A rematch won after a card already changed hands today: nothing to pick. */
@Composable
private fun FriendlyWin(setup: CardMatchSetup, onDone: () -> Unit) {
    SubScreen(title = "You win!", onBack = onDone) {
        Text("\u201C${setup.challenger.win.first()}\u201D", style = MaterialTheme.typography.bodyLarge, color = Color.White)
        Text(
            "A card already changed hands with ${setup.challenger.name} today, so this one was for fun. Tomorrow brings a new challenger, with cards on the line again.",
            style = MaterialTheme.typography.bodyMedium,
            color = SaiyanColors.TextDim,
        )
        DbzPrimaryButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
