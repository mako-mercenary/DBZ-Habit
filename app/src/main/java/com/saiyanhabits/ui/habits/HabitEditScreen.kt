package com.saiyanhabits.ui.habits

import com.saiyanhabits.ui.components.OutlinedText
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults
import com.saiyanhabits.ui.components.DbzPrimaryButton
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.DbzPanel
import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.saiyanhabits.GameConfig
import com.saiyanhabits.R
import com.saiyanhabits.data.HabitEntity
import com.saiyanhabits.domain.Gravity
import com.saiyanhabits.domain.HabitCategory
import com.saiyanhabits.domain.PowerCalculator
import com.saiyanhabits.domain.ReminderTimes
import com.saiyanhabits.domain.WeekSchedule
import com.saiyanhabits.domain.mentorFor
import com.saiyanhabits.reminders.Notifications
import com.saiyanhabits.ui.components.ChoiceTile
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.theme.color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private const val MAX_NAME_LENGTH = 60
private const val MAX_UNIT_LENGTH = 20
private const val DEFAULT_REMINDER_MINUTES = 8 * 60
private val DifficultyNames = mapOf(1 to "Easy", 2 to "Medium", 3 to "Hard")

/**
 * Add a habit ([initial] is null) or edit an existing one.
 * [otherHardestName] is the habit that currently holds the "hardest" flag, if it is not this one.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditScreen(
    initial: HabitEntity?,
    otherHardestName: String?,
    onSave: (HabitEntity) -> Unit,
    onDelete: (HabitEntity) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: HabitCategory.DAILY_BASICS) }
    var difficulty by rememberSaveable { mutableIntStateOf(initial?.difficulty ?: GameConfig.DEFAULT_DIFFICULTY) }
    var days by rememberSaveable { mutableIntStateOf(initial?.scheduleDays ?: WeekSchedule.EVERY_DAY) }
    var hardest by rememberSaveable { mutableStateOf(initial?.isHardest ?: false) }
    var weekly by rememberSaveable { mutableStateOf(initial?.weeklyTarget != null) }
    var weeklyTarget by rememberSaveable { mutableIntStateOf(initial?.weeklyTarget ?: GameConfig.DEFAULT_WEEKLY_TARGET) }
    var counted by rememberSaveable { mutableStateOf((initial?.dailyTarget ?: 1) > 1) }
    var dailyTarget by rememberSaveable {
        mutableIntStateOf(initial?.dailyTarget?.takeIf { it > 1 } ?: GameConfig.DEFAULT_DAILY_TARGET)
    }
    var unit by rememberSaveable { mutableStateOf(initial?.unit.orEmpty()) }
    var reminderMinutes by rememberSaveable { mutableStateOf(initial?.reminderMinutes) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(Notifications.areEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        // The user may have just come back from the system settings.
        notificationsAllowed = Notifications.areEnabled(context)
        onPauseOrDispose { }
    }
    val askForNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationsAllowed = Notifications.areEnabled(context) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    val canSave = name.isNotBlank() && (weekly || days != WeekSchedule.NONE)
    val mentor = mentorFor(category, hardest)

    Scaffold(
        modifier = Modifier.imePadding(),
        // Transparent, so the app's background shows through.
        containerColor = Color.Transparent,
        contentColor = SaiyanColors.TextHigh,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    OutlinedText(
                        (if (initial == null) "New habit" else "Edit habit").uppercase(),
                        MaterialTheme.typography.titleLarge,
                        outlineWidth = 4.dp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
                    }
                },
                actions = {
                    if (initial != null) {
                        TextButton(onClick = { confirmingDelete = true }) {
                            Text("Delete", color = SaiyanColors.Danger)
                        }
                    }
                },
            )
        },
        bottomBar = {
            DbzPrimaryButton(
                onClick = {
                    val base = initial ?: HabitEntity(
                        name = "",
                        category = category,
                        difficulty = difficulty,
                        scheduleDays = days,
                        createdAtEpochDay = LocalDate.now().toEpochDay(),
                    )
                    onSave(
                        base.copy(
                            name = name.trim(),
                            category = category,
                            difficulty = difficulty,
                            // A weekly goal can be done on any day.
                            scheduleDays = if (weekly) WeekSchedule.EVERY_DAY else days,
                            isHardest = hardest,
                            reminderMinutes = reminderMinutes,
                            weeklyTarget = if (weekly) weeklyTarget else null,
                            dailyTarget = if (counted) dailyTarget else 1,
                            unit = if (counted) unit.trim() else "",
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(52.dp),
            ) {
                Text(if (initial == null) "Add habit" else "Save changes")
            }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionLabel("Name")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                placeholder = { Text("e.g. 20 push-ups") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))
            SectionLabel("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitCategory.entries.forEach { option ->
                    FilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        label = { Text(option.label) },
                    )
                }
            }
            Text(
                "Mentor: ${mentor.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = mentor.color,
            )

            Spacer(Modifier.height(6.dp))
            SectionLabel("Difficulty")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (level in GameConfig.MIN_DIFFICULTY..GameConfig.MAX_DIFFICULTY) {
                    ChoiceTile(
                        selected = difficulty == level,
                        onClick = { difficulty = level },
                        title = DifficultyNames[level] ?: "Level $level",
                        caption = "+${PowerCalculator.completionPoints(level, Gravity.NORMAL)} points",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("How often")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceTile(
                    selected = !weekly,
                    onClick = { weekly = false },
                    title = "Set days",
                    caption = "on the days you pick",
                    modifier = Modifier.weight(1f),
                )
                ChoiceTile(
                    selected = weekly,
                    onClick = { weekly = true },
                    title = "Times a week",
                    caption = "on any days",
                    modifier = Modifier.weight(1f),
                )
            }
            if (weekly) {
                Stepper(
                    value = weeklyTarget,
                    range = 1..GameConfig.MAX_WEEKLY_TARGET,
                    label = if (weeklyTarget == 1) "time a week" else "times a week",
                    onChange = { weeklyTarget = it },
                )
                Text(
                    "Do it on any days. It only becomes due when the rest of the week leaves no room, " +
                        "and a goal that slips counts as one missed day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SaiyanColors.TextDim,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        DayToggle(
                            day = day,
                            selected = WeekSchedule.isScheduled(days, day),
                            onClick = { days = WeekSchedule.toggle(days, day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    if (days == WeekSchedule.NONE) "Pick at least one day." else WeekSchedule.describe(days),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (days == WeekSchedule.NONE) SaiyanColors.Danger else SaiyanColors.TextDim,
                )
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("Each time")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceTile(
                    selected = !counted,
                    onClick = { counted = false },
                    title = "Once",
                    caption = "one check-off",
                    modifier = Modifier.weight(1f),
                )
                ChoiceTile(
                    selected = counted,
                    onClick = { counted = true },
                    title = "A count",
                    caption = "e.g. 8 glasses",
                    modifier = Modifier.weight(1f),
                )
            }
            if (counted) {
                Stepper(
                    value = dailyTarget,
                    range = 2..GameConfig.MAX_DAILY_TARGET,
                    label = unit.trim().ifBlank { "times" },
                    onChange = { dailyTarget = it },
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it.take(MAX_UNIT_LENGTH) },
                    label = { Text("What you count") },
                    placeholder = { Text("e.g. glasses") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Tap the habit on the home screen once for each. The last one checks it off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SaiyanColors.TextDim,
                )
            }

            Spacer(Modifier.height(6.dp))
            DbzPanel(
                style = PanelStyle.Blue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // The whole row flips the switch, not just the switch itself.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .toggleable(value = hardest, role = Role.Switch, onValueChange = { hardest = it })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("My hardest habit", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                hardest && otherHardestName != null ->
                                    "Takes the title from \"$otherHardestName\". Vegeta trains this one."
                                hardest -> "Vegeta trains this one."
                                else -> "Only one habit can hold this title. Vegeta trains it."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SaiyanColors.TextDim,
                        )
                    }
                    Switch(
                        checked = hardest,
                        onCheckedChange = null,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            ReminderRow(
                weekly = weekly,
                reminderMinutes = reminderMinutes,
                notificationsAllowed = notificationsAllowed,
                onPick = { pickingTime = true },
                onClear = { reminderMinutes = null },
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickingTime) {
        ReminderTimeDialog(
            initialMinutes = reminderMinutes ?: DEFAULT_REMINDER_MINUTES,
            onConfirm = { minutes ->
                reminderMinutes = minutes
                pickingTime = false
                if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = { pickingTime = false },
        )
    }

    if (confirmingDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this habit?") },
            text = {
                Text("\"${initial.name}\" leaves your lists. The power you earned from it stays.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete(initial)
                }) { Text("Delete", color = SaiyanColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/** A reminder time in the phone's own style, e.g. "7:30 AM" or "07:30". */
fun formatReminderTime(minutesAfterMidnight: Int): String =
    ReminderTimes.toLocalTime(minutesAfterMidnight)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
private fun ReminderRow(
    weekly: Boolean,
    reminderMinutes: Int?,
    notificationsAllowed: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    DbzPanel(
        style = PanelStyle.Blue,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClickLabel = "Set reminder time", onClick = onPick)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Reminder", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        reminderMinutes == null -> "Off. Tap to pick a time."
                        notificationsAllowed && weekly -> "Each day until this week's goal is reached."
                        notificationsAllowed -> "On scheduled days, if it isn't done yet."
                        else -> "Notifications are turned off for Saiyan Habits, so this can't show. " +
                            "Allow them in your phone's settings."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reminderMinutes != null && !notificationsAllowed) {
                        SaiyanColors.Danger
                    } else {
                        SaiyanColors.TextDim
                    },
                )
            }
            if (reminderMinutes != null) {
                Text(
                    formatReminderTime(reminderMinutes),
                    style = MaterialTheme.typography.titleMedium,
                    color = SaiyanColors.Blue,
                    modifier = Modifier.padding(start = 12.dp),
                )
                TextButton(onClick = onClear) { Text("Remove", color = SaiyanColors.TextDim) }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initialMinutes: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = SaiyanColors.Panel) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            ) {
                SectionLabel("Remind me at", Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                TimePicker(state = pickerState)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) {
                        Text("Set reminder")
                    }
                }
            }
        }
    }
}

@Composable
private fun DayToggle(
    day: DayOfWeek,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fullName = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) SaiyanColors.Blue else SaiyanColors.Panel,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondary else SaiyanColors.TextDim,
        border = BorderStroke(1.dp, if (selected) SaiyanColors.Blue else SaiyanColors.Outline),
        modifier = modifier
            .aspectRatio(1f)
            .semantics {
                role = Role.Checkbox
                this.selected = selected
                contentDescription = fullName
            },
    ) {
        Text(
            text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** A number with minus and plus buttons, e.g. "- 3 times a week +". */
@Composable
private fun Stepper(value: Int, range: IntRange, label: String, onChange: (Int) -> Unit) {
    DbzPanel(style = PanelStyle.Blue, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
                Text("−", style = MaterialTheme.typography.headlineMedium, color = if (value > range.first) Color.White else SaiyanColors.TextDim)
            }
            Text(
                "$value $label",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
                Text("+", style = MaterialTheme.typography.headlineMedium, color = if (value < range.last) Color.White else SaiyanColors.TextDim)
            }
        }
    }
}
