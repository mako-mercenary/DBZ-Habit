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
import com.saiyanhabits.ui.theme.SaiyanColors
import com.saiyanhabits.ui.components.NavItem
import com.saiyanhabits.ui.components.DbzNavBar
import com.saiyanhabits.ui.components.ArtImage
import com.saiyanhabits.art.ArtKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
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
import com.saiyanhabits.data.PlayerStateEntity
import com.saiyanhabits.data.QuestEvent
import com.saiyanhabits.domain.BondRules
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.ui.bonds.BondsScreen
import com.saiyanhabits.ui.bonds.BondsViewModel
import com.saiyanhabits.ui.bonds.MentorScreen
import com.saiyanhabits.ui.bonds.SceneScreen
import com.saiyanhabits.ui.chamber.ChamberScreen
import com.saiyanhabits.ui.chamber.ChamberViewModel
import com.saiyanhabits.ui.components.DragonOverlay
import com.saiyanhabits.ui.components.TransformationOverlay
import com.saiyanhabits.ui.components.VictoryOverlay
import com.saiyanhabits.ui.habits.HabitEditScreen
import com.saiyanhabits.ui.habits.HabitsScreen
import com.saiyanhabits.ui.habits.HabitsViewModel
import com.saiyanhabits.ui.history.HistoryScreen
import com.saiyanhabits.ui.history.AchievementsScreen
import com.saiyanhabits.ui.history.HistoryViewModel
import com.saiyanhabits.ui.history.RecapScreen
import com.saiyanhabits.domain.WeekRecap
import com.saiyanhabits.domain.Weeks
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import com.saiyanhabits.ui.home.HomeScreen
import com.saiyanhabits.ui.home.HomeViewModel
import com.saiyanhabits.ui.quests.BombDetailScreen
import com.saiyanhabits.ui.quests.BombEditScreen
import com.saiyanhabits.ui.quests.QuestsViewModel
import com.saiyanhabits.ui.quests.SagaDetailScreen
import com.saiyanhabits.ui.quests.SagaEditScreen
import com.saiyanhabits.ui.quests.SagasScreen
import com.saiyanhabits.ui.settings.ArtScreen
import com.saiyanhabits.ui.settings.ArtViewModel
import com.saiyanhabits.ui.settings.DataViewModel
import com.saiyanhabits.ui.settings.SettingsScreen
import com.saiyanhabits.ui.settings.WishesViewModel
import com.saiyanhabits.ui.story.ChapterScreen
import com.saiyanhabits.ui.story.StoryScreen
import com.saiyanhabits.ui.story.StoryViewModel
import kotlinx.coroutines.launch

/** Navigation routes. */
private object Routes {
    const val HOME = "home"
    const val HABITS = "habits"
    const val SAGAS = "sagas"
    const val CHAMBER = "chamber"
    const val BONDS = "bonds"
    const val STORY = "story"
    const val SETTINGS = "settings"
    const val ART = "art"
    const val HISTORY = "history"
    const val ACHIEVEMENTS = "achievements"
    const val STATUS = "status"

    const val WEEK_ARG = "week"
    const val RECAP = "recap/{$WEEK_ARG}"
    fun recap(weekStart: Long) = "recap/$weekStart"

    const val CHAPTER_ARG = "chapter"
    const val CHAPTER = "chapter/{$CHAPTER_ARG}"
    fun chapter(number: Int) = "chapter/$number"

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
    BONDS(Routes.BONDS, "Team", R.drawable.ic_bonds),
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
    val storyViewModel: StoryViewModel = viewModel(factory = factory)
    val chapters by storyViewModel.chapters.collectAsStateWithLifecycle()
    val wishesViewModel: WishesViewModel = viewModel(factory = factory)
    val wishes by wishesViewModel.wishes.collectAsStateWithLifecycle()
    val waitingWishes = wishes.filter { it.grantedAtEpochDay == null }
    val artViewModel: ArtViewModel = viewModel(factory = factory)
    val dataViewModel: DataViewModel = viewModel(factory = factory)
    val historyViewModel: HistoryViewModel = viewModel(factory = factory)
    // The player's form picks which of their portraits scenes and chapters show.
    val player by app.habitRepository.playerState.collectAsStateWithLifecycle(initialValue = PlayerStateEntity())

    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val snackbars = remember { SnackbarHostState() }

    var unlocked by remember { mutableStateOf<GameEvent.FormUnlocked?>(null) }
    var shake by remember { mutableStateOf(Offset.Zero) }
    var defeated by remember { mutableStateOf<QuestEvent.SagaDefeated?>(null) }
    var bondLevelUp by remember { mutableStateOf<BondLevelUpNotice?>(null) }
    var summoning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val achievements by app.achievementRepository.state.collectAsStateWithLifecycle(initialValue = null)
    val fighter by app.fighterRepository.state.collectAsStateWithLifecycle(initialValue = null)

    // Announce achievements as they unlock.
    LaunchedEffect(achievements) {
        val state = achievements ?: return@LaunchedEffect
        when (val news = app.achievementRepository.takeNews(state)) {
            null -> Unit
            is com.saiyanhabits.data.AchievementNews.Backdated -> launch {
                snackbars.showSnackbar("Your past training already earned ${news.count} achievements. Tap your title to see them.")
            }
            is com.saiyanhabits.data.AchievementNews.Unlocked -> {
                app.sounds.play(com.saiyanhabits.sound.SoundKey.LEVEL_UP)
                news.achievements.forEach { achievement ->
                    launch { snackbars.showSnackbar("Achievement unlocked: ${achievement.title}") }
                }
            }
        }
    }

    // The weekly recap opens by itself the first time the app comes to the front
    // in a new week, over the home screen only, so it never interrupts an editor.
    LifecycleResumeEffect(app) {
        val job = scope.launch {
            app.habitRepository.syncDays()
            val week = app.historyRepository.dueRecapWeek() ?: return@launch
            if (navController.currentDestination?.route == Routes.HOME) navController.navigate(Routes.recap(week))
        }
        onPauseOrDispose { job.cancel() }
    }

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
                is GameEvent.OrbEarned -> launch {
                    snackbars.showSnackbar(
                        "A perfect week! Wish orb earned: ${event.orbs} of ${GameConfig.WISH_ORBS_NEEDED}.",
                    )
                }
                is GameEvent.BondLevelUp -> bondLevelUp = BondLevelUpNotice(
                    mentor = event.mentor,
                    level = event.level,
                    sceneTitle = app.dialogue.all()[event.mentor]?.scene(event.level)?.title,
                )
            }
        }
    }

    // A sound for every check-off.
    LaunchedEffect(homeViewModel) {
        homeViewModel.gains.collect { app.sounds.play(com.saiyanhabits.sound.SoundKey.CHECK_OFF) }
    }

    androidx.compose.runtime.CompositionLocalProvider(com.saiyanhabits.sound.LocalSoundPlayer provides app.sounds) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Your background behind every screen, under a scrim so the cards stay readable.
        ArtImage(
            key = ArtKey.BACKGROUND_HOME,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(SaiyanColors.Night.copy(alpha = 0.35f), SaiyanColors.Night.copy(alpha = 0.75f)),
                    ),
                ),
        )
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = SaiyanColors.TextHigh,
            // Each screen handles the status bar itself; the bottom bar handles the navigation bar.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbars) },
            bottomBar = {
                if (Tab.entries.any { it.route == currentRoute }) {
                    DbzNavBar(
                        Tab.entries.map { tab ->
                            NavItem(
                                label = tab.label,
                                icon = tab.icon,
                                selected = currentRoute == tab.route,
                                onClick = { navController.switchTab(tab.route) },
                            )
                        },
                    )
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
                    val homeDate = homeViewModel.uiState.collectAsStateWithLifecycle().value?.date ?: java.time.LocalDate.now()
                    val todayNote by remember(homeDate) { app.historyRepository.note(homeDate.toEpochDay()) }
                        .collectAsStateWithLifecycle(initialValue = null)
                    HomeScreen(
                        title = achievements?.title,
                        level = fighter?.sheet?.level,
                        zeni = fighter?.zeni,
                        onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
                        onOpenStatus = { navController.navigate(Routes.STATUS) },
                        todayNote = todayNote,
                        onSaveNote = { text -> scope.launch { app.historyRepository.saveNote(homeDate.toEpochDay(), text) } },
                        viewModel = homeViewModel,
                        newChapter = chapters?.firstOrNull { it.isNew }?.chapter,
                        hasWaitingWish = waitingWishes.isNotEmpty(),
                        onAddHabit = { navController.navigate(Routes.habitEditor()) },
                        onOpenStory = { navController.navigate(Routes.STORY) },
                        onOpenHistory = {
                            historyViewModel.reset()
                            navController.navigate(Routes.HISTORY)
                        },
                        onOpenChapter = { navController.navigate(Routes.chapter(it)) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onSummon = { summoning = true },
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
                composable(Routes.STORY) {
                    StoryScreen(
                        chapters = chapters,
                        onOpen = { navController.navigate(Routes.chapter(it)) },
                        onBack = { navController.popBackStack(Routes.STORY, inclusive = true) },
                    )
                }
                composable(
                    route = Routes.CHAPTER,
                    arguments = listOf(navArgument(Routes.CHAPTER_ARG) { type = NavType.IntType }),
                ) { entry ->
                    val number = entry.arguments?.getInt(Routes.CHAPTER_ARG) ?: return@composable
                    val close = { navController.popBackStack(Routes.CHAPTER, inclusive = true) }
                    val loaded = chapters ?: return@composable
                    val status = loaded.firstOrNull { it.chapter.number == number }
                    if (status == null || !status.unlocked) {
                        LaunchedEffect(Unit) { close() }
                    } else {
                        // Opening a chapter is what counts as reading it.
                        LaunchedEffect(number) { storyViewModel.markRead(number) }
                        ChapterScreen(chapter = status.chapter, playerForm = player.highestForm, onBack = { close() })
                    }
                }
                composable(Routes.SETTINGS) {
                    val nudgeOn by app.prefs.eveningNudge.collectAsStateWithLifecycle()
                    val nudgeMinutes by app.prefs.eveningNudgeMinutes.collectAsStateWithLifecycle()
                    val soundOn by app.prefs.sound.collectAsStateWithLifecycle()
                    val dataState by dataViewModel.state.collectAsStateWithLifecycle()
                    SettingsScreen(
                        wishes = wishes,
                        onAddWish = wishesViewModel::add,
                        onRemoveWish = wishesViewModel::remove,
                        onOpenArt = { navController.navigate(Routes.ART) },
                        data = dataState,
                        onExport = dataViewModel::export,
                        onImport = dataViewModel::check,
                        onConfirmImport = dataViewModel::confirmImport,
                        onCancelImport = dataViewModel::cancelImport,
                        nudgeOn = nudgeOn,
                        nudgeMinutes = nudgeMinutes,
                        onNudge = { on ->
                            app.prefs.setEveningNudge(on)
                            app.reminders.updateEveningNudge(on, app.prefs.eveningNudgeMinutes.value, replace = true)
                        },
                        onNudgeTime = { minutes ->
                            app.prefs.setEveningNudgeMinutes(minutes)
                            app.reminders.updateEveningNudge(app.prefs.eveningNudge.value, minutes, replace = true)
                        },
                        soundOn = soundOn,
                        onSound = app.prefs::setSound,
                        onBack = { navController.popBackStack(Routes.SETTINGS, inclusive = true) },
                    )
                }
                composable(Routes.STATUS) {
                    com.saiyanhabits.ui.bonds.StatusScreen(
                        state = fighter,
                        onBack = { navController.popBackStack(Routes.STATUS, inclusive = true) },
                    )
                }
                composable(Routes.ACHIEVEMENTS) {
                    AchievementsScreen(
                        state = achievements,
                        onBack = { navController.popBackStack(Routes.ACHIEVEMENTS, inclusive = true) },
                    )
                }
                composable(Routes.HISTORY) {
                    val state by historyViewModel.uiState.collectAsStateWithLifecycle()
                    val home by homeViewModel.uiState.collectAsStateWithLifecycle()
                    HistoryScreen(
                        state = state,
                        currentStreak = home?.overallStreak ?: 0,
                        onPrevMonth = { state?.let { historyViewModel.showMonth(it.month.minusMonths(1)) } },
                        onNextMonth = { state?.let { historyViewModel.showMonth(it.month.plusMonths(1)) } },
                        onSelectDay = historyViewModel::select,
                        onRange = historyViewModel::setRange,
                        onSaveNote = historyViewModel::saveNote,
                        onOpenRecap = {
                            val today = java.time.LocalDate.now().toEpochDay()
                            navController.navigate(Routes.recap(Weeks.startOf(today) - Weeks.DAYS))
                        },
                        onBack = { navController.popBackStack(Routes.HISTORY, inclusive = true) },
                    )
                }
                composable(
                    route = Routes.RECAP,
                    arguments = listOf(navArgument(Routes.WEEK_ARG) { type = NavType.LongType }),
                ) { entry ->
                    val week = entry.arguments?.getLong(Routes.WEEK_ARG) ?: return@composable
                    val recap by produceState<WeekRecap?>(null, week) { value = app.historyRepository.recap(week) }
                    LaunchedEffect(week) { app.historyRepository.markRecapShown(week) }
                    RecapScreen(recap = recap, onDone = { navController.popBackStack(Routes.RECAP, inclusive = true) })
                }
                composable(Routes.ART) {
                    val overridden by artViewModel.overridden.collectAsStateWithLifecycle()
                    val message by artViewModel.message.collectAsStateWithLifecycle()
                    ArtScreen(
                        overridden = overridden,
                        message = message,
                        onPick = artViewModel::pick,
                        onReset = artViewModel::reset,
                        onBack = { navController.popBackStack(Routes.ART, inclusive = true) },
                    )
                }
                composable(Routes.BONDS) {
                    val state by bondsViewModel.uiState.collectAsStateWithLifecycle()
                    LifecycleResumeEffect(Unit) {
                        bondsViewModel.refreshToday()
                        onPauseOrDispose { }
                    }
                    BondsScreen(
                        state = state,
                        fighter = fighter,
                        onOpenStatus = { navController.navigate(Routes.STATUS) },
                        onOpenMentor = { navController.navigate(Routes.mentor(it)) },
                    )
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
                        SceneScreen(mentor = mentor, scene = scene, playerForm = player.highestForm, onBack = { close() })
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

        if (summoning) {
            DragonOverlay(
                waiting = waitingWishes,
                onGrant = wishesViewModel::grant,
                onWriteWish = {
                    summoning = false
                    navController.navigate(Routes.SETTINGS)
                },
                onDismiss = { summoning = false },
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
}

/** What to tell the player when a bond level is gained. */
private data class BondLevelUpNotice(val mentor: Mentor, val level: Int, val sceneTitle: String?)

@Composable
private fun BondLevelUpDialog(notice: BondLevelUpNotice, onView: () -> Unit, onDismiss: () -> Unit) {
    val sounds = com.saiyanhabits.sound.LocalSoundPlayer.current
    LaunchedEffect(notice) { sounds?.play(com.saiyanhabits.sound.SoundKey.LEVEL_UP) }
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
