package com.saiyanhabits.ui.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanhabits.GameConfig
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.data.DayRoutine
import com.saiyanhabits.data.RoutineTemplate
import com.saiyanhabits.sound.LocalSoundPlayer
import com.saiyanhabits.sound.SoundKey
import com.saiyanhabits.ui.components.ChoiceTile
import com.saiyanhabits.ui.components.DbzPanel
import com.saiyanhabits.ui.components.DbzPrimaryButton
import com.saiyanhabits.ui.components.DbzSecondaryButton
import com.saiyanhabits.ui.components.EmptyPanel
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.components.TabLabel
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayChip = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())
private val DayTitle = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(DayChip)
}

/** What the routines screen is showing. */
private sealed interface RoutineMode {
    data object Days : RoutineMode
    data object Templates : RoutineMode
    /** Editing a day routine, or a new one on [date] when [routineId] is null. */
    data class EditDay(val date: LocalDate, val routineId: Long?) : RoutineMode
    /** Editing a template, or a new one when [templateId] is null. */
    data class EditTemplate(val templateId: Long?) : RoutineMode
    /** Choosing days to plan a template on. */
    data class PlanTemplate(val templateId: Long) : RoutineMode
}

/** Routines: day checklists and the templates they come from. */
@Composable
fun RoutinesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val repo = app.routineRepository
    val sounds = LocalSoundPlayer.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val routines by repo.routinesFrom(today).collectAsStateWithLifecycle(initialValue = emptyList())
    val templates by repo.templates.collectAsStateWithLifecycle(initialValue = emptyList())
    var mode by remember { mutableStateOf<RoutineMode>(RoutineMode.Days) }
    var day by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(day)
    var message by remember { mutableStateOf<String?>(null) }

    when (val m = mode) {
        is RoutineMode.EditDay -> {
            val existing = m.routineId?.let { id -> routines.firstOrNull { it.routine.id == id } }
            StepsEditor(
                title = if (existing == null) "New routine" else "Edit routine",
                subtitle = m.date.format(DayTitle),
                initialName = existing?.routine?.name ?: "",
                initialSteps = existing?.items?.map { it.id to it.text } ?: emptyList(),
                templates = if (existing == null) templates else emptyList(),
                onCancel = { mode = RoutineMode.Days },
                onSave = { name, steps ->
                    scope.launch {
                        if (existing == null) repo.plan(m.date, name, steps.map { it.second })
                        else repo.editRoutine(existing.routine.id, name, steps)
                        mode = RoutineMode.Days
                    }
                },
            )
            return
        }
        is RoutineMode.EditTemplate -> {
            val existing = m.templateId?.let { id -> templates.firstOrNull { it.template.id == id } }
            StepsEditor(
                title = if (existing == null) "New template" else "Edit template",
                subtitle = "Saved for reuse. Planning it copies the steps onto a day.",
                initialName = existing?.template?.name ?: "",
                initialSteps = existing?.items?.map { null to it.text } ?: emptyList(),
                templates = emptyList(),
                onCancel = { mode = RoutineMode.Templates },
                onSave = { name, steps ->
                    scope.launch {
                        repo.saveTemplate(existing?.template?.id, name, steps.map { it.second })
                        mode = RoutineMode.Templates
                    }
                },
            )
            return
        }
        is RoutineMode.PlanTemplate -> {
            val template = templates.firstOrNull { it.template.id == m.templateId }
            if (template == null) {
                mode = RoutineMode.Templates
                return
            }
            PlanDays(
                template = template,
                today = today,
                onCancel = { mode = RoutineMode.Templates },
                onPlan = { dates ->
                    scope.launch {
                        val n = repo.planFromTemplate(template.template.id, dates)
                        message = "Planned “${template.template.name}” on $n day${if (n == 1) "" else "s"}."
                        day = dates.minOrNull()?.toEpochDay() ?: day
                        mode = RoutineMode.Days
                    }
                },
            )
            return
        }
        else -> Unit
    }

    SubScreen(title = "Routines", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceTile(mode == RoutineMode.Days, { mode = RoutineMode.Days }, "Days", "check things off", Modifier.weight(1f))
            ChoiceTile(mode == RoutineMode.Templates, { mode = RoutineMode.Templates }, "Templates", "${templates.size} saved", Modifier.weight(1f))
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.Gold) }

        if (mode == RoutineMode.Templates) {
            Text(
                "Save a routine once, then plan it on any days you like. Each planned day gets its own copy to check off.",
                style = MaterialTheme.typography.bodyMedium,
                color = SaiyanColors.TextDim,
            )
            DbzPrimaryButton(onClick = { mode = RoutineMode.EditTemplate(null) }, modifier = Modifier.fillMaxWidth()) { Text("New template") }
            if (templates.isEmpty()) {
                EmptyPanel(title = "No templates yet", body = "A morning routine, a gym day, a Sunday reset. Make it once, reuse it forever.")
            }
            templates.forEach { t ->
                TemplateCard(
                    template = t,
                    onPlan = { mode = RoutineMode.PlanTemplate(t.template.id) },
                    onEdit = { mode = RoutineMode.EditTemplate(t.template.id) },
                    onDelete = { scope.launch { repo.deleteTemplate(t.template.id) } },
                )
            }
            return@SubScreen
        }

        // Days: today, tomorrow, the rest of the fortnight, and any later day with something planned.
        val planned = routines.map { it.routine.epochDay }.toSet()
        val days = ((0L..13L).map { today.plusDays(it) } + planned.map { LocalDate.ofEpochDay(it) }).distinct().sorted()
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { d ->
                val count = routines.count { it.routine.epochDay == d.toEpochDay() }
                FilterChip(
                    selected = d.toEpochDay() == day,
                    onClick = { day = d.toEpochDay() },
                    label = { Text(dayLabel(d, today) + if (count > 0) " · $count" else "") },
                )
            }
        }
        Text(selected.format(DayTitle), style = MaterialTheme.typography.titleMedium, color = Color.White)

        val onDay = routines.filter { it.routine.epochDay == day }
        val isToday = day == today.toEpochDay()
        if (onDay.isEmpty()) {
            EmptyPanel(
                title = if (isToday) "Nothing planned today" else "Nothing planned yet",
                body = "Add a checklist from scratch or from one of your templates. Each item you check off on the day gives +${GameConfig.ROUTINE_ITEM_POWER} power.",
            )
        } else if (!isToday) {
            Text(
                "Planned ahead. Items can only be checked off, and earn power, on the day itself.",
                style = MaterialTheme.typography.bodySmall,
                color = SaiyanColors.TextDim,
            )
        }
        onDay.forEach { r ->
            RoutineCard(
                routine = r,
                canCheck = isToday,
                onToggle = { itemId ->
                    scope.launch {
                        val gained = repo.toggle(itemId)
                        if (gained > 0) sounds?.play(SoundKey.CHECK_OFF, 0.6f)
                    }
                },
                onEdit = { mode = RoutineMode.EditDay(selected, r.routine.id) },
                onSaveAsTemplate = {
                    scope.launch {
                        repo.saveAsTemplate(r.routine.id)
                        message = "Saved “${r.routine.name}” as a template."
                    }
                },
                onDelete = { scope.launch { repo.deleteRoutine(r.routine.id) } },
            )
        }
        DbzPrimaryButton(onClick = { mode = RoutineMode.EditDay(selected, null) }, modifier = Modifier.fillMaxWidth()) {
            Text("Add a routine for ${dayLabel(selected, today).lowercase().replaceFirstChar { it.uppercase() }}")
        }
        Spacer(Modifier.padding(4.dp))
    }
}

@Composable
private fun RoutineCard(
    routine: DayRoutine,
    canCheck: Boolean,
    onToggle: (Long) -> Unit,
    onEdit: () -> Unit,
    onSaveAsTemplate: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${routine.routine.name}”?") },
            text = { Text(if (routine.done > 0) "The power its checked items gave goes with it." else "Its template, if it has one, is kept.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = SaiyanColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
    DbzPanel(style = if (routine.finished) PanelStyle.Selected else PanelStyle.Blue, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TabLabel(routine.routine.name)
                    Text(
                        "${routine.done} / ${routine.items.size} done" + if (routine.finished) " · complete!" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (routine.finished) SaiyanColors.Gold else SaiyanColors.TextDim,
                    )
                }
            }
            routine.items.forEach { item ->
                val done = item.doneAtMillis != null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canCheck) { onToggle(item.id) },
                ) {
                    Checkbox(
                        checked = done,
                        onCheckedChange = if (canCheck) ({ onToggle(item.id) }) else null,
                        enabled = canCheck,
                        colors = CheckboxDefaults.colors(checkedColor = SaiyanColors.Orange),
                    )
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (done) SaiyanColors.TextDim else Color.White,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                    )
                    if (done && canCheck) {
                        Text("+${GameConfig.ROUTINE_ITEM_POWER}", style = MaterialTheme.typography.labelLarge, color = SaiyanColors.Gold)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onEdit) { Text("Edit", color = SaiyanColors.Gold) }
                TextButton(onClick = onSaveAsTemplate) { Text("Save as template", color = SaiyanColors.Gold) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = SaiyanColors.Danger) }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: RoutineTemplate, onPlan: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete the “${template.template.name}” template?") },
            text = { Text("Days already planned from it keep their checklists.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = SaiyanColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
    DbzPanel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TabLabel(template.template.name)
            Text("${template.items.size} steps", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
            template.items.take(6).forEach {
                Text("• ${it.text}", style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextHigh)
            }
            if (template.items.size > 6) {
                Text("and ${template.items.size - 6} more", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DbzPrimaryButton(onClick = onPlan) { Text("Plan on days") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("Edit", color = SaiyanColors.Gold) }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = SaiyanColors.Danger) }
            }
        }
    }
}

/** Editing a name and a list of steps. Steps carry an id when they already exist, so ticks survive edits. */
@Composable
private fun StepsEditor(
    title: String,
    subtitle: String,
    initialName: String,
    initialSteps: List<Pair<Long?, String>>,
    templates: List<RoutineTemplate>,
    onCancel: () -> Unit,
    onSave: (String, List<Pair<Long?, String>>) -> Unit,
) {
    BackHandler(onBack = onCancel)
    var name by remember { mutableStateOf(initialName) }
    val steps = remember { mutableStateListOf<Pair<Long?, String>>().apply { addAll(initialSteps.ifEmpty { listOf(null to "") }) } }
    val canSave = steps.any { it.second.isNotBlank() }
    SubScreen(
        title = title,
        onBack = onCancel,
        actions = {
            TextButton(onClick = { onSave(name, steps.toList()) }, enabled = canSave) {
                Text("Save", color = if (canSave) SaiyanColors.Gold else SaiyanColors.TextDim)
            }
        },
    ) {
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextDim)
        if (templates.isNotEmpty()) {
            SectionLabel("Start from a template")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                templates.forEach { t ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            name = t.template.name
                            steps.clear()
                            steps.addAll(t.items.map { null to it.text })
                        },
                        label = { Text(t.template.name) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text("Name") },
            placeholder = { Text("Morning routine") },
            modifier = Modifier.fillMaxWidth(),
        )
        SectionLabel("Steps")
        steps.forEachIndexed { i, (id, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}.", style = MaterialTheme.typography.titleSmall, color = SaiyanColors.TextDim, modifier = Modifier.width(28.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { steps[i] = id to it },
                    singleLine = true,
                    placeholder = { Text("Something to do") },
                    modifier = Modifier.weight(1f),
                )
                Column {
                    Text(
                        "▲",
                        color = if (i > 0) SaiyanColors.TextHigh else SaiyanColors.TextDim,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(enabled = i > 0) { steps.add(i - 1, steps.removeAt(i)) }
                            .padding(4.dp),
                    )
                    Text(
                        "▼",
                        color = if (i < steps.lastIndex) SaiyanColors.TextHigh else SaiyanColors.TextDim,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(enabled = i < steps.lastIndex) { steps.add(i + 1, steps.removeAt(i)) }
                            .padding(4.dp),
                    )
                }
                Text(
                    "✕",
                    color = SaiyanColors.Danger,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { if (steps.size > 1) steps.removeAt(i) else steps[0] = null to "" }
                        .padding(6.dp),
                )
            }
        }
        DbzSecondaryButton(onClick = { steps.add(null to "") }, modifier = Modifier.fillMaxWidth()) { Text("Add a step") }
        DbzPrimaryButton(onClick = { onSave(name, steps.toList()) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text("Save") }
    }
}

/** Choosing the days to plan a template on: the next two weeks, any number of them. */
@Composable
private fun PlanDays(template: RoutineTemplate, today: LocalDate, onCancel: () -> Unit, onPlan: (List<LocalDate>) -> Unit) {
    BackHandler(onBack = onCancel)
    val chosen = remember { mutableStateListOf<LocalDate>() }
    var weeks by remember { mutableStateOf(2) }
    SubScreen(title = "Plan “${template.template.name}”", onBack = onCancel) {
        Text("Pick the days. Each one gets its own copy of the checklist.", style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = false, onClick = {
                chosen.clear()
                chosen.addAll((0L until weeks * 7L).map { today.plusDays(it) })
            }, label = { Text("Every day") })
            FilterChip(selected = false, onClick = {
                chosen.clear()
                chosen.addAll((0L until weeks * 7L).map { today.plusDays(it) }.filter { it.dayOfWeek.value <= 5 })
            }, label = { Text("Weekdays") })
            FilterChip(selected = false, onClick = { chosen.clear() }, label = { Text("None") })
        }
        (0L until weeks * 7L).map { today.plusDays(it) }.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    val on = d in chosen
                    DbzPanel(
                        onClick = { if (on) chosen.remove(d) else chosen.add(d) },
                        style = if (on) PanelStyle.Selected else PanelStyle.Blue,
                        corner = 10.dp,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(vertical = 8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).take(2), style = MaterialTheme.typography.labelSmall, color = SaiyanColors.TextDim)
                            Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        }
                    }
                }
            }
        }
        if (weeks * 7 < GameConfig.ROUTINE_PLAN_DAYS_AHEAD) {
            TextButton(onClick = { weeks += 2 }) { Text("Show two more weeks", color = SaiyanColors.Gold) }
        }
        DbzPrimaryButton(onClick = { onPlan(chosen.sorted()) }, enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (chosen.isEmpty()) "Pick some days" else "Plan on ${chosen.size} day${if (chosen.size == 1) "" else "s"}")
        }
    }
}

/** The home screen's view of today's routines, or a way in when there are none. */
@Composable
fun RoutinesHomeCard(onClick: () -> Unit) {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val today = remember { LocalDate.now() }
    val all by app.routineRepository.routinesFrom(today).collectAsStateWithLifecycle(initialValue = emptyList())
    val todays = all.filter { it.routine.epochDay == today.toEpochDay() }
    DbzPanel(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TabLabel("Routines")
            if (todays.isEmpty()) {
                Text("Nothing planned today. Tap to plan a checklist.", style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.TextDim)
            } else {
                todays.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.routine.name, style = MaterialTheme.typography.bodyLarge, color = Color.White, modifier = Modifier.weight(1f))
                        Text(
                            "${r.done}/${r.items.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (r.finished) SaiyanColors.Gold else SaiyanColors.TextHigh,
                        )
                    }
                }
            }
        }
    }
}
