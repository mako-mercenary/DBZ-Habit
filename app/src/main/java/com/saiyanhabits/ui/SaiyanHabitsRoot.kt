package com.saiyanhabits.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saiyanhabits.R
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.ui.habits.HabitEditScreen
import com.saiyanhabits.ui.habits.HabitsScreen
import com.saiyanhabits.ui.habits.HabitsViewModel
import com.saiyanhabits.ui.home.HomeScreen
import com.saiyanhabits.ui.home.HomeViewModel

private enum class Tab(val label: String, @DrawableRes val icon: Int) {
    HOME("Home", R.drawable.ic_home),
    HABITS("Habits", R.drawable.ic_habits),
}

/**
 * Top of the UI. Navigation is two bottom tabs plus a full-screen habit editor,
 * held as plain saved state. More tabs join in later milestones.
 */
@Composable
fun SaiyanHabitsRoot() {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val factory = remember(app) { saiyanViewModelFactory(app.habitRepository) }
    val homeViewModel: HomeViewModel = viewModel(factory = factory)
    val habitsViewModel: HabitsViewModel = viewModel(factory = factory)
    val habits by habitsViewModel.habits.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    // Which habit the editor is editing; null means a new one.
    var editorHabitId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun openEditor(habitId: Long?) {
        editorHabitId = habitId
        editorOpen = true
    }

    if (editorOpen) {
        BackHandler { editorOpen = false }
        val loaded = habits
        val editing = loaded?.firstOrNull { it.id == editorHabitId }
        when {
            // Still reading the database (only happens when the app is restored straight into the editor).
            loaded == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            // The habit being edited is gone.
            editorHabitId != null && editing == null -> LaunchedEffect(Unit) { editorOpen = false }
            else -> HabitEditScreen(
                initial = editing,
                otherHardestName = loaded.firstOrNull { it.isHardest && it.id != editing?.id }?.name,
                onSave = {
                    habitsViewModel.save(it)
                    editorOpen = false
                },
                onDelete = {
                    habitsViewModel.delete(it)
                    editorOpen = false
                },
                onBack = { editorOpen = false },
            )
        }
    } else {
        BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            },
        ) { padding ->
            when (tab) {
                Tab.HOME -> HomeScreen(
                    viewModel = homeViewModel,
                    onAddHabit = { openEditor(null) },
                    modifier = Modifier.padding(padding),
                )
                Tab.HABITS -> HabitsScreen(
                    habits = habits,
                    onAdd = { openEditor(null) },
                    onEdit = { openEditor(it.id) },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}
