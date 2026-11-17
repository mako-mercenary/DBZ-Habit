package com.saiyanhabits.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.saiyanhabits.GameConfig
import com.saiyanhabits.R
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.data.GameEvent
import com.saiyanhabits.data.QuestEvent
import com.saiyanhabits.domain.BondRules
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.ui.bonds.BondsScreen
import com.saiyanhabits.ui.bonds.BondsViewModel
import com.saiyanhabits.ui.bonds.MentorScreen
import com.saiyanhabits.ui.bonds.SceneScreen
import com.saiyanhabits.ui.chamber.ChamberScreen
import com.saiyanhabits.ui.chamber.ChamberViewModel
import com.saiyanhabits.ui.components.TransformationOverlay
import com.saiyanhabits.ui.components.VictoryOverlay
import com.saiyanhabits.ui.habits.HabitEditScreen
import com.saiyanhabits.ui.habits.HabitsScreen
import com.saiyanhabits.ui.habits.HabitsViewModel
import com.saiyanhabits.ui.home.HomeScreen
import com.saiyanhabits.ui.home.HomeViewModel
import com.saiyanhabits.ui.quests.BombDetailScreen
import com.saiyanhabits.ui.quests.BombEditScreen
import com.saiyanhabits.ui.quests.QuestsViewModel
import com.saiyanhabits.ui.quests.SagaDetailScreen
import com.saiyanhabits.ui.quests.SagaEditScreen
import com.saiyanhabits.ui.quests.SagasScreen
import kotlinx.coroutines.launch

/** Navigation routes. */
private object Routes {
    const val HOME = "home"
    const val HABITS = "habits"
    const val SAGAS = "sagas"
    const val CHAMBER = "chamber"
    const val BONDS = "bonds"

    const val MENTOR_ARG = "mentor"
    const val MENTOR = "mentor/{$MENTOR_ARG}"
    fun mentor(mentor: Mentor) = "mentor/${mentor.name}"

    const val SCENE_LEVEL_ARG = "level"
    const val SCENE = "scene/{$MENTOR_ARG}/{$SCENE_LEVEL_ARG}"
    fun scene(mentor: Mentor, level: Int) = "scene/${mentor.name}/$level"

    const val NEW_SAGA = "new-saga"
    const val SAGA_ID_ARG = "sagaId"
    const val SAGA = "saga/{$SAGA_ID_ARG}"
    fun saga(id: Long) = "saga/$id"

    const val NEW_BOMB = "new-bomb"
    const val BOMB_ID_ARG = "bombId"
    const val BOMB = "bomb/{$BOMB_ID_ARG}"
    fun bomb(id: Long) = "bomb/$id"

    private const val HABIT_ID = "habitId"
    const val NEW_HABIT = -1L

    /** The habit editor; without an id it adds a new habit. */
    const val HABIT_EDITOR = "habit?$HABIT_ID={$HABIT_ID}"
    const val HABIT_ID_ARG = HABIT_ID

    fun habitEditor(habitId: Long? = null): String =
        if (habitId == null) "habit" else "habit?$HABIT_ID=$habitId"
}

/** The tabs in the bottom bar. */
private enum class Tab(val route: String, val label: String, @DrawableRes val icon: Int) {
    HOME(Routes.HOME, "Home", R.drawable.ic_home),
    HABITS(Routes.HABITS, "Habits", R.drawable.ic_habits),
    SAGAS(Routes.SAGAS, "Sagas", R.drawable.ic_sagas),
    CHAMBER(Routes.CHAMBER, "Chamber", R.drawable.ic_chamber),
    BONDS(Routes.BONDS, "Bonds", R.drawable.ic_bonds),
}

@Composable
fun SaiyanHabitsRoot() {
    val app = LocalContext.current.applicationContext as SaiyanApp
    val factory = remember(app) { saiyanViewModelFactory(app) }
    // Created out here so every screen shares the same view models.
    val homeViewModel: HomeViewModel = viewModel(factory = factory)
    val habitsViewModel: HabitsViewModel = viewModel(factory = factory)
    val questsViewModel: QuestsViewModel = viewModel(factory = factory)
    val chamberViewModel: ChamberViewModel = viewModel(factory = factory)
    val bondsViewModel: BondsViewModel = viewModel(factory = factory)
    val perks by bondsViewModel.perks.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val snackbars = remember { SnackbarHostState() }

    var unlocked by remember { mutableStateOf<GameEvent.FormUnlocked?>(null) }
    var shake by remember { mutableStateOf(Offset.Zero) }
    var defeated by remember { mutableStateOf<QuestEvent.SagaDefeated?>(null) }
    var bondLevelUp by remember { mutableStateOf<BondLevelUpNotice?>(null) }

    LaunchedEffect(app) {
        app.questRepository.events.collect { event ->
            when (event) {
                is QuestEvent.SagaDefeated -> defeated = event
            }
        }
    }

    LaunchedEffect(app) {
        app.habitRepository.events.collect { event ->
            when (event) {
                is GameEvent.FormUnlocked -> unlocked = event
                // Snackbars wait for the one before, so they get their own coroutine and
                // never hold up the events behind them.
                is GameEvent.BeanEarned -> launch {
                    snackbars.showSnackbar("Healing bean earned. You hold ${event.beans} of ${event.capacity}.")
                }
                is GameEvent.BondLevelUp -> bondLevelUp = BondLevelUpNotice(
                    mentor = event.mentor,
                    level = event.level,
                    sceneTitle = app.dialogue.all()[event.mentor]?.scene(event.level)?.title,
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            // Each screen handles the status bar itself; the bottom bar handles the navigation bar.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbars) },
            bottomBar = {
                if (Tab.entries.any { it.route == currentRoute }) {
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navController.switchTab(tab.route) },
                                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
            modifier = Modifier.graphicsLayer {
                translationX = shake.x
                translationY = shake.y
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onAddHabit = { navController.navigate(Routes.habitEditor()) },
                    )
                }
                composable(Routes.HABITS) {
                    val habits by habitsViewModel.habits.collectAsStateWithLifecycle()
                    val streaks by habitsViewModel.streaks.collectAsStateWithLifecycle()
                    HabitsScreen(
                        habits = habits,
                        streaks = streaks,
                        onAdd = { navController.navigate(Routes.habitEditor()) },
                        onEdit = { navController.navigate(Routes.habitEditor(it.id)) },
                    )
                }
                composable(Routes.SAGAS) {
                    val overview by questsViewModel.overview.collectAsStateWithLifecycle()
                    SagasScreen(
                        overview = overview,
                        onNewSaga = { navController.navigate(Routes.NEW_SAGA) },
                        onOpenSaga = { navController.navigate(Routes.saga(it)) },
                        onNewBomb = { navController.navigate(Routes.NEW_BOMB) },
                        onOpenBomb = { navController.navigate(Routes.bomb(it)) },
                    )
                }
                composable(Routes.CHAMBER) {
                    ChamberScreen(viewModel = chamberViewModel, focusMultiplier = perks.focusMultiplier)
                }
                composable(Routes.BONDS) {
                    val state by bondsViewModel.uiState.collectAsStateWithLifecycle()
                    LifecycleResumeEffect(Unit) {
                        bondsViewModel.refreshToday()
                        onPauseOrDispose { }
                    }
                    BondsScreen(state = state, onOpenMentor = { navController.navigate(Routes.mentor(it)) })
                }
                composable(Routes.MENTOR) { entry ->
                    val mentor = entry.mentorArg() ?: return@composable
                    val state by bondsViewModel.uiState.collectAsStateWithLifecycle()
                    LifecycleResumeEffect(Unit) {
                        bondsViewModel.refreshToday()
                        onPauseOrDispose { }
                    }
                    state?.let {
                        MentorScreen(
                            mentor = mentor,
                            state = it,
                            onOpenScene = { level -> navController.navigate(Routes.scene(mentor, level)) },
                            onUseKaioken = bondsViewModel::useKaioken,
                            onUseDayOff = bondsViewModel::useDayOff,
                            onBack = { navController.popBackStack(Routes.MENTOR, inclusive = true) },
                        )
                    }
                }
                composable(
                    route = Routes.SCENE,
                    arguments = listOf(navArgument(Routes.SCENE_LEVEL_ARG) { type = NavType.IntType }),
                ) { entry ->
                    val mentor = entry.mentorArg() ?: return@composable
                    val level = entry.arguments?.getInt(Routes.SCENE_LEVEL_ARG) ?: return@composable
                    val state by bondsViewModel.uiState.collectAsStateWithLifecycle()
                    val close = { navController.popBackStack(Routes.SCENE, inclusive = true) }
                    val loaded = state ?: return@composable
                    val scene = loaded.dialogues[mentor]?.scene(level)
                    if (scene == null || !BondRules.isSceneUnlocked(level, loaded.bond(mentor).level)) {
                        LaunchedEffect(Unit) { close() }
                    } else {
                        SceneScreen(mentor = mentor, scene = scene, onBack = { close() })
                    }
                }
                composable(Routes.NEW_SAGA) {
                    SagaEditScreen(
                        onCreate = { name, boss, milestones ->
                            questsViewModel.createSaga(name, boss, milestones) { id ->
                                // Straight into the fight, with the editor gone from the back stack.
                                navController.navigate(Routes.saga(id)) {
                                    popUpTo(Routes.NEW_SAGA) { inclusive = true }
                                }
                            }
                        },
                        onBack = { navController.popBackStack(Routes.NEW_SAGA, inclusive = true) },
                    )
                }
                composable(
                    route = Routes.SAGA,
                    arguments = listOf(navArgument(Routes.SAGA_ID_ARG) { type = NavType.LongType }),
                ) { entry ->
                    val sagaId = entry.arguments?.getLong(Routes.SAGA_ID_ARG) ?: return@composable
                    val loaded by remember(sagaId) { questsViewModel.saga(sagaId) }
                        .collectAsStateWithLifecycle(initialValue = null)
                    val close = { navController.popBackStack(Routes.SAGA, inclusive = true) }
                    val saga = loaded?.value
                    when {
                        loaded == null -> Unit
                        saga == null -> LaunchedEffect(Unit) { close() }
                        else -> SagaDetailScreen(
                            saga = saga,
                            perks = perks,
                            onStrike = questsViewModel::strike,
                            onAddMilestone = { questsViewModel.addMilestone(sagaId, it) },
                            onRemoveMilestone = questsViewModel::removeMilestone,
                            onAbandon = {
                                questsViewModel.abandonSaga(sagaId)
                                close()
                            },
                            onBack = { close() },
                        )
                    }
                }
                composable(Routes.NEW_BOMB) {
                    BombEditScreen(
                        onCreate = { name, unit, target, size ->
                            questsViewModel.createBomb(name, unit, target, size) { id ->
                                navController.navigate(Routes.bomb(id)) {
                                    popUpTo(Routes.NEW_BOMB) { inclusive = true }
                                }
                            }
                        },
                        onBack = { navController.popBackStack(Routes.NEW_BOMB, inclusive = true) },
                    )
                }
                composable(
                    route = Routes.BOMB,
                    arguments = listOf(navArgument(Routes.BOMB_ID_ARG) { type = NavType.LongType }),
                ) { entry ->
                    val bombId = entry.arguments?.getLong(Routes.BOMB_ID_ARG) ?: return@composable
                    val loaded by remember(bombId) { questsViewModel.bomb(bombId) }
                        .collectAsStateWithLifecycle(initialValue = null)
                    val lastLog by remember(bombId) { questsViewModel.lastBombLog(bombId) }
                        .collectAsStateWithLifecycle(initialValue = null)
                    val close = { navController.popBackStack(Routes.BOMB, inclusive = true) }
                    val bomb = loaded?.value
                    when {
                        loaded == null -> Unit
                        bomb == null -> LaunchedEffect(Unit) { close() }
                        else -> BombDetailScreen(
                            bomb = bomb,
                            lastLog = lastLog,
                            onLog = { questsViewModel.logBombProgress(bombId, it) },
                            onUndoLastLog = { questsViewModel.undoLastBombLog(bombId) },
                            onThrow = { onThrown -> questsViewModel.throwBomb(bombId, onThrown) },
                            onAbandon = {
                                questsViewModel.abandonBomb(bombId)
                                close()
                            },
                            onBack = { close() },
                        )
                    }
                }
                composable(
                    route = Routes.HABIT_EDITOR,
                    arguments = listOf(
                        navArgument(Routes.HABIT_ID_ARG) {
                            type = NavType.LongType
                            defaultValue = Routes.NEW_HABIT
                        },
                    ),
                ) { entry ->
                    val habitId = entry.arguments?.getLong(Routes.HABIT_ID_ARG) ?: Routes.NEW_HABIT
                    val habits by habitsViewModel.habits.collectAsStateWithLifecycle()
                    // Leaves the editor. Does nothing if it has already been left.
                    val close = { navController.popBackStack(Routes.HABIT_EDITOR, inclusive = true) }

                    val loaded = habits
                    val editing = loaded?.firstOrNull { it.id == habitId }
                    when {
                        // Still reading the database (only when the app is restored straight into the editor).
                        loaded == null -> Unit
                        // The habit being edited is gone.
                        habitId != Routes.NEW_HABIT && editing == null -> LaunchedEffect(Unit) { close() }
                        else -> HabitEditScreen(
                            initial = editing,
                            otherHardestName = loaded.firstOrNull { it.isHardest && it.id != editing?.id }?.name,
                            onSave = {
                                habitsViewModel.save(it)
                                close()
                            },
                            onDelete = {
                                habitsViewModel.delete(it)
                                close()
                            },
                            onBack = { close() },
                        )
                    }
                }
            }
        }

        unlocked?.let { event ->
            TransformationOverlay(
                form = event.form,
                streak = event.streak,
                onShake = { shake = it },
                onFinished = {
                    shake = Offset.Zero
                    unlocked = null
                },
            )
        }

        bondLevelUp?.let { notice ->
            BondLevelUpDialog(
                notice = notice,
                onView = {
                    bondLevelUp = null
                    navController.navigate(Routes.mentor(notice.mentor))
                },
                onDismiss = { bondLevelUp = null },
            )
        }

        defeated?.let { event ->
            VictoryOverlay(
                sagaName = event.sagaName,
                boss = event.boss,
                bonus = event.bonus,
                onDismiss = { defeated = null },
            )
        }
    }
}

/** What to tell the player when a bond level is gained. */
private data class BondLevelUpNotice(val mentor: Mentor, val level: Int, val sceneTitle: String?)

@Composable
private fun BondLevelUpDialog(notice: BondLevelUpNotice, onView: () -> Unit, onDismiss: () -> Unit) {
    val mentor = notice.mentor
    val technique = BondRules.techniqueUnlockedAt(mentor, notice.level)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bond level ${notice.level} with ${mentor.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (notice.sceneTitle != null) Text("New scene: \"${notice.sceneTitle}\".")
                if (technique != null) Text("New technique: ${technique.title}. ${technique.description}")
                if (notice.level == GameConfig.ASSIST_MIN_BOND_LEVEL) {
                    Text("${mentor.displayName} now joins your boss fights and adds bonus damage to every hit.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onView) { Text("Go and see") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

private fun NavBackStackEntry.mentorArg(): Mentor? {
    val name = arguments?.getString(Routes.MENTOR_ARG)
    return Mentor.entries.firstOrNull { it.name == name }
}

/** Bottom-bar navigation: one copy of each tab, and each tab remembers where it was. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
