package com.saiyanhabits.ui.habits

import com.saiyanhabits.ui.components.MentorWatermark
import com.saiyanhabits.ui.components.ScreenTitle
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.DbzPanel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saiyanhabits.R
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.domain.WeekSchedule
import com.saiyanhabits.domain.mentorFor
import com.saiyanhabits.ui.components.EmptyPanel
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color

/** All habits. [habits] is null while the database is still loading. */
@Composable
fun HabitsScreen(
    habits: List<HabitEntity>?,
    streaks: Map<Long, Int>,
    onAdd: () -> Unit,
    onEdit: (HabitEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            // Bottom padding leaves room for the floating button over the last row.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") {
                ScreenTitle(
                    "Habits",
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            when {
                habits == null -> Unit
                habits.isEmpty() -> item(key = "empty") {
                    EmptyPanel(
                        title = "No habits yet",
                        body = "A habit is something you want to do on set days of the week. " +
                            "Each one you check off raises your power level.",
                        actionLabel = "Add a habit",
                        onAction = onAdd,
                    )
                }
                else -> items(habits, key = { it.id }) { habit ->
                    HabitRow(habit = habit, streak = streaks[habit.id] ?: 0, onClick = { onEdit(habit) })
                }
            }
        }
        if (!habits.isNullOrEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("New habit") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun HabitRow(habit: HabitEntity, streak: Int, onClick: () -> Unit) {
    val mentor = mentorFor(habit.category, habit.isHardest)
    DbzPanel(
        style = PanelStyle.Blue,
        watermark = { MentorWatermark(mentor) },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp),
        ) {
            Box(
                Modifier
                    .size(width = 6.dp, height = 56.dp)
                    .clip(CircleShape)
                    .background(mentor.color),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${habit.category.label} · ${mentor.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = mentor.color,
                )
                Text(
                    "${WeekSchedule.describeHabit(habit.scheduleDays, habit.weeklyTarget, habit.dailyTarget, habit.unit)} · Difficulty ${habit.difficulty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SaiyanColors.TextDim,
                )
                val reminder = habit.reminderMinutes
                if (streak > 0 || reminder != null) {
                    Text(
                        listOfNotNull(
                            if (streak > 0) "$streak-day streak" else null,
                            reminder?.let { "Reminder ${formatReminderTime(it)}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (streak > 0) SaiyanColors.Orange else SaiyanColors.TextDim,
                    )
                }
            }
            if (habit.isHardest) {
                Spacer(Modifier.width(10.dp))
                Text(
                    "HARDEST",
                    style = MaterialTheme.typography.labelSmall,
                    color = mentor.color,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(mentor.color.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
