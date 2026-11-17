package com.saiyanhabits.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanhabits.GameConfig
import com.saiyanhabits.art.ArtKey
import com.saiyanhabits.R
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.domain.BeanOffer
import com.saiyanhabits.domain.Forms
import com.saiyanhabits.domain.Gravity
import com.saiyanhabits.domain.PowerCalculator
import com.saiyanhabits.data.mentor
import com.saiyanhabits.domain.Perks
import com.saiyanhabits.domain.RivalStanding
import com.saiyanhabits.domain.StoryChapter
import com.saiyanhabits.ui.components.ArtImage
import com.saiyanhabits.ui.components.EmptyPanel
import com.saiyanhabits.ui.components.MentorPortrait
import com.saiyanhabits.ui.components.MeterBar
import com.saiyanhabits.ui.components.ScouterReadout
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color
import com.saiyanhabits.ui.theme.formAura
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HeaderDate = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    /** An unlocked story chapter that has not been read yet, if any. */
    newChapter: StoryChapter?,
    /** True when at least one wish is written down and waiting. */
    hasWaitingWish: Boolean,
    onAddHabit: () -> Unit,
    onOpenStory: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onSummon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }
    val state = uiState ?: return

    val haptics = LocalHapticFeedback.current
    var choosingGravityFor by remember { mutableStateOf<HabitEntity?>(null) }
    var undoing by remember { mutableStateOf<TodayHabit?>(null) }

    Box(modifier.fillMaxSize()) {
    ArtImage(
        key = ArtKey.BACKGROUND_HOME,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    // A scrim so the cards stay readable over any picture.
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SaiyanColors.Night.copy(alpha = 0.45f), SaiyanColors.Night.copy(alpha = 0.8f)),
                ),
            ),
    )
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        item(key = "date") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(state.date.format(HeaderDate), Modifier.weight(1f).padding(start = 4.dp))
                IconButton(onClick = onOpenStory) {
                    Icon(painterResource(R.drawable.ic_story), contentDescription = "Story", tint = SaiyanColors.TextDim)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = SaiyanColors.TextDim)
                }
            }
        }
        if (newChapter != null) {
            item(key = "chapter") {
                NewChapterCard(newChapter, onClick = { onOpenChapter(newChapter.number) })
            }
        }
        item(key = "power") {
            ScouterReadout(
                powerLevel = state.powerLevel,
                gains = viewModel.gains,
                formName = Forms.name(state.form),
                aura = formAura(state.form),
                numberColor = if (state.form == Forms.BASE) SaiyanColors.Orange else formAura(state.form),
                avatarForm = state.form,
            )
        }
        state.mentorLine?.let { line ->
            item(key = "mentor") { MentorLineCard(line) }
        }
        item(key = "stats") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                StreakCard(state, Modifier.weight(1f).fillMaxHeight())
                BeansCard(state, Modifier.weight(1f).fillMaxHeight())
            }
        }
        item(key = "progress") {
            TodayProgress(
                done = state.doneCount,
                total = state.counted.size,
                doublePoints = state.perks.doublePointsToday,
            )
        }
        when {
            state.hasNoHabits -> item(key = "empty") {
                EmptyPanel(
                    title = "Your training starts here",
                    body = "Add a habit, check it off each day, and watch your power level climb.",
                    actionLabel = "Add your first habit",
                    onAction = onAddHabit,
                )
            }
            state.today.isEmpty() -> item(key = "rest") {
                EmptyPanel(
                    title = "Rest day",
                    body = "Nothing is scheduled for today.",
                )
            }
            else -> items(state.today, key = { it.habit.id }) { entry ->
                TodayHabitRow(
                    entry = entry,
                    perks = state.perks,
                    onClick = {
                        when {
                            entry.isDone -> undoing = entry
                            !entry.excused -> choosingGravityFor = entry.habit
                        }
                    },
                )
            }
        }
        item(key = "rival") { RivalCard(state.rival) }
        item(key = "orbs") { WishOrbsCard(state.wishOrbs, hasWaitingWish, onSummon, onOpenSettings) }
    }
    }

    choosingGravityFor?.let { habit ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { choosingGravityFor = null },
            sheetState = sheetState,
            containerColor = SaiyanColors.Panel,
        ) {
            GravityChooser(
                habit = habit,
                perks = state.perks,
                onChoose = { gravity ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.complete(habit, gravity)
                    scope.launch { sheetState.hide() }
                        .invokeOnCompletion { choosingGravityFor = null }
                },
            )
        }
    }

    state.beanOffer?.let { offer ->
        BeanOfferDialog(
            offer = offer,
            beans = state.beans,
            onAccept = viewModel::acceptBeanOffer,
            onDecline = viewModel::declineBeanOffer,
        )
    }

    undoing?.let { entry ->
        AlertDialog(
            onDismissRequest = { undoing = null },
            title = { Text("Undo this check-off?") },
            text = {
                Text(
                    "\"${entry.habit.name}\" goes back to not done, and the " +
                        "${entry.completion?.points ?: 0} points it gave are removed.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.undo(entry.habit)
                    undoing = null
                }) { Text("Undo check-off") }
            },
            dismissButton = {
                TextButton(onClick = { undoing = null }) { Text("Keep it") }
            },
        )
    }
}

@Composable
private fun StatCard(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SaiyanColors.Panel,
        border = BorderStroke(1.dp, SaiyanColors.Outline),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            SectionLabel(label)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun StreakCard(state: HomeUiState, modifier: Modifier = Modifier) {
    StatCard("Streak", modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${state.overallStreak}",
                style = MaterialTheme.typography.headlineMedium,
                color = if (state.overallStreak > 0) SaiyanColors.Orange else SaiyanColors.TextDim,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (state.overallStreak == 1) "day" else "days",
                style = MaterialTheme.typography.bodyMedium,
                color = SaiyanColors.TextDim,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        val nextAt = state.nextFormAt
        if (nextAt == null) {
            Text("Every form unlocked", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
        } else {
            Text(
                "${Forms.name(state.form + 1)} at $nextAt",
                style = MaterialTheme.typography.bodySmall,
                color = SaiyanColors.TextDim,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (state.overallStreak.toFloat() / nextAt).coerceIn(0f, 1f) },
                color = formAura(state.form + 1),
                trackColor = SaiyanColors.PanelRaised,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun BeansCard(state: HomeUiState, modifier: Modifier = Modifier) {
    val capacity = state.beanCapacity
    StatCard("Healing beans", modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(36.dp)
                .semantics { contentDescription = "${state.beans} of $capacity healing beans" },
        ) {
            repeat(capacity) { index ->
                val held = index < state.beans
                Box(
                    Modifier
                        .size(width = 18.dp, height = 26.dp)
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (held) {
                                Modifier.background(SaiyanColors.Bean)
                            } else {
                                Modifier.border(1.5.dp, SaiyanColors.Outline, RoundedCornerShape(50))
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                state.beans >= capacity -> "Pouch is full"
                state.daysUntilNextBean == 1 -> "Next bean in 1 perfect day"
                else -> "Next bean in ${state.daysUntilNextBean} perfect days"
            },
            style = MaterialTheme.typography.bodySmall,
            color = SaiyanColors.TextDim,
        )
    }
}

@Composable
private fun BeanOfferDialog(offer: BeanOffer, beans: Int, onAccept: () -> Unit, onDecline: () -> Unit) {
    val one = offer.cost == 1
    AlertDialog(
        // A yes or no is needed: tapping outside or pressing back leaves it up.
        onDismissRequest = {},
        title = { Text(if (one) "You missed a day" else "You missed ${offer.cost} days") },
        text = {
            Text(
                (if (one) "Eat a healing bean" else "Eat ${offer.cost} healing beans") +
                    " to keep your ${offer.streakSaved}-day streak alive? You have $beans.",
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(if (one) "Eat a bean" else "Eat ${offer.cost} beans") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("Let the streak end", color = SaiyanColors.TextDim) }
        },
    )
}

@Composable
private fun NewChapterCard(chapter: StoryChapter, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, SaiyanColors.Orange),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("NEW STORY CHAPTER", style = MaterialTheme.typography.labelMedium, color = SaiyanColors.Orange)
                Text(
                    "Chapter ${chapter.number}: ${chapter.title}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("Read", style = MaterialTheme.typography.labelLarge, color = SaiyanColors.Orange)
        }
    }
}

/** Last week's power gain as the rival, and how this week compares. */
@Composable
private fun RivalCard(rival: RivalStanding) {
    val leading = rival.beaten || rival.lead >= 0
    val tint = if (!rival.hasRival) SaiyanColors.TextDim else if (leading) SaiyanColors.Bean else SaiyanColors.Danger
    StatCard("Rival: last week's you", Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RivalFigure(Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("Rival", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
                        Text(
                            if (rival.hasRival) "+${rival.rivalTotal}" else "None yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = SaiyanColors.Danger.copy(alpha = if (rival.hasRival) 1f else 0.5f),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("You, this week", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
                        Text("+${rival.mine}", style = MaterialTheme.typography.titleLarge, color = SaiyanColors.Orange)
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (rival.hasRival) {
                    MeterBar(rival.mine.toFloat() / maxOf(rival.rivalTotal, rival.mine, 1), tint)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    when {
                        !rival.hasRival -> "This week's gain becomes next week's rival."
                        rival.beaten -> "Rival beaten: ${rival.mine - rival.rivalTotal} past all of last week."
                        rival.lead > 0 -> "Ahead: ${rival.lead} more than last week had by this day."
                        rival.lead < 0 -> "Behind: ${-rival.lead} short of where last week was by this day."
                        else -> "Level with last week at this point."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tint,
                )
            }
        }
    }
}

/** The rival's picture, cropped to a circle. */
@Composable
private fun RivalFigure(modifier: Modifier = Modifier) {
    ArtImage(
        key = ArtKey.RIVAL,
        contentDescription = "Rival",
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape),
    )
}

@Composable
private fun WishOrbsCard(orbs: Int, hasWaitingWish: Boolean, onSummon: () -> Unit, onOpenSettings: () -> Unit) {
    val needed = GameConfig.WISH_ORBS_NEEDED
    val ready = orbs >= needed
    StatCard("Wish orbs", Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { contentDescription = "$orbs of $needed wish orbs" },
        ) {
            repeat(needed) { index ->
                val held = index < orbs
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .then(
                            if (held) {
                                Modifier.background(
                                    Brush.radialGradient(listOf(Color(0xFFFFE9A8), SaiyanColors.Orange, SaiyanColors.OrangeDeep)),
                                )
                            } else {
                                Modifier.border(1.5.dp, SaiyanColors.Outline, CircleShape)
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                ready && hasWaitingWish -> "All $needed gathered. The dragon will grant one wish."
                ready -> "All $needed gathered. Write a wish first, then summon the dragon."
                else -> "$orbs of $needed. A perfect week, Monday to Sunday, earns one."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (ready) SaiyanColors.Orange else SaiyanColors.TextDim,
        )
        when {
            ready && hasWaitingWish -> {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onSummon, modifier = Modifier.fillMaxWidth()) { Text("Summon the dragon") }
            }
            !hasWaitingWish -> TextButton(onClick = onOpenSettings) {
                Text(if (ready) "Write a wish" else "Write a wish for later")
            }
        }
    }
}

@Composable
private fun MentorLineCard(line: MentorLine) {
    Crossfade(targetState = line, label = "mentor line") { shown -> MentorLineCardContent(shown) }
}

@Composable
private fun MentorLineCardContent(line: MentorLine) {
    val mentor = line.mentor
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SaiyanColors.Panel,
        border = BorderStroke(1.dp, mentor.color.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            MentorPortrait(mentor, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mentor.displayName.uppercase(), style = MaterialTheme.typography.labelMedium, color = mentor.color)
                Text(line.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TodayProgress(done: Int, total: Int, doublePoints: Boolean) {
    Column(Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Today's training", Modifier.weight(1f))
            if (total > 0) {
                val allDone = done == total
                Text(
                    text = if (allDone) "ALL DONE" else "$done / $total",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (allDone) SaiyanColors.Orange else SaiyanColors.TextHigh,
                )
            }
        }
        if (doublePoints) {
            Text(
                "Kaio-ken is active: check-offs pay ${GameConfig.TECHNIQUE_KAIOKEN_MULTIPLIER}x today.",
                style = MaterialTheme.typography.bodySmall,
                color = SaiyanColors.Orange,
            )
        }
        if (total > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                color = SaiyanColors.Orange,
                trackColor = SaiyanColors.PanelRaised,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun TodayHabitRow(entry: TodayHabit, perks: Perks, onClick: () -> Unit) {
    val habit = entry.habit
    val mentor = habit.mentor
    val done = entry.completion
    val faded = done != null || entry.excused
    // A short swell when the habit goes from not done to done, not on first showing.
    val bump = remember { Animatable(1f) }
    var wasDone by remember { mutableStateOf(done != null) }
    LaunchedEffect(done != null) {
        if (done != null && !wasDone) {
            bump.snapTo(1.05f)
            bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 320f))
        }
        wasDone = done != null
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (faded) SaiyanColors.Panel.copy(alpha = 0.6f) else SaiyanColors.Panel,
        border = BorderStroke(1.dp, if (done != null) SaiyanColors.Orange.copy(alpha = 0.45f) else SaiyanColors.Outline),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = bump.value
                scaleY = bump.value
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        ) {
            Box(
                Modifier
                    .size(width = 6.dp, height = 40.dp)
                    .clip(CircleShape)
                    .background(mentor.color.copy(alpha = if (faded) 0.45f else 1f)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (faded) SaiyanColors.TextDim else SaiyanColors.TextHigh,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val streak = if (entry.streak > 0) " · ${entry.streak}-day streak" else ""
                Text(
                    text = when {
                        done != null -> "${done.gravity.label} · +${done.points}$streak"
                        entry.excused -> "Day off today · streak safe"
                        else -> {
                            val base = PowerCalculator.completionPoints(habit.difficulty, Gravity.NORMAL, mentor, perks)
                            "${mentor.displayName} · +$base and up$streak"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (done != null) SaiyanColors.Orange else SaiyanColors.TextDim,
                )
            }
            if (!entry.excused || done != null) {
                Spacer(Modifier.width(12.dp))
                CheckMark(checked = done != null, description = if (done != null) "Done" else "Not done")
            }
        }
    }
}

@Composable
private fun CheckMark(checked: Boolean, description: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (checked) {
                    Modifier.background(SaiyanColors.Orange)
                } else {
                    Modifier.border(2.dp, SaiyanColors.TextDim, CircleShape)
                },
            ),
    ) {
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun GravityChooser(habit: HabitEntity, perks: Perks, onChoose: (Gravity) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        Text("How did you train?", style = MaterialTheme.typography.headlineSmall)
        Text(
            habit.name,
            style = MaterialTheme.typography.bodyMedium,
            color = SaiyanColors.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Gravity.entries.forEach { gravity ->
            val accent = when (gravity) {
                Gravity.NORMAL -> SaiyanColors.Blue
                Gravity.X10 -> SaiyanColors.Orange
                Gravity.X100 -> SaiyanColors.Danger
            }
            Surface(
                onClick = { onChoose(gravity) },
                shape = RoundedCornerShape(16.dp),
                color = SaiyanColors.PanelRaised,
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(gravity.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            gravity.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = SaiyanColors.TextDim,
                        )
                    }
                    Text(
                        "+${PowerCalculator.completionPoints(habit.difficulty, gravity, habit.mentor, perks)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                    )
                }
            }
        }
    }
}
