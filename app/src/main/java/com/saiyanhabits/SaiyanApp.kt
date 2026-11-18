package com.saiyanhabits

import android.app.Application
import com.saiyanhabits.art.ArtRepository
import com.saiyanhabits.chamber.TimeChamber
import com.saiyanhabits.data.AppDatabase
import com.saiyanhabits.data.BackupRepository
import com.saiyanhabits.data.BondRepository
import com.saiyanhabits.data.DialogueRepository
import com.saiyanhabits.data.HabitRepository
import com.saiyanhabits.data.HistoryRepository
import com.saiyanhabits.data.QuestRepository
import com.saiyanhabits.data.StoryRepository
import com.saiyanhabits.data.WishRepository
import com.saiyanhabits.reminders.Notifications
import com.saiyanhabits.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

private const val WIDGET_REFRESH_DELAY_MILLIS = 300L

class SaiyanApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }

    val habitRepository: HabitRepository by lazy { HabitRepository(database) }

    val questRepository: QuestRepository by lazy { QuestRepository(database) }

    val bondRepository: BondRepository by lazy { BondRepository(database) }

    val historyRepository: HistoryRepository by lazy { HistoryRepository(this, database) }

    val gameRepository: com.saiyanhabits.data.GameRepository by lazy { com.saiyanhabits.data.GameRepository(database) }

    val achievementRepository: com.saiyanhabits.data.AchievementRepository by lazy {
        com.saiyanhabits.data.AchievementRepository(this, database, habitRepository)
    }

    val dialogue: DialogueRepository by lazy { DialogueRepository(this) }

    val storyRepository: StoryRepository by lazy { StoryRepository(this) }

    val wishRepository: WishRepository by lazy { WishRepository(database) }

    val art: ArtRepository by lazy { ArtRepository(this) }

    val backupRepository: BackupRepository by lazy { BackupRepository(this, database) }

    val reminders: ReminderScheduler by lazy { ReminderScheduler(this) }

    val prefs: com.saiyanhabits.data.AppPrefs by lazy { com.saiyanhabits.data.AppPrefs(this) }

    val sounds: com.saiyanhabits.sound.SoundPlayer by lazy { com.saiyanhabits.sound.SoundPlayer(this, prefs.sound) }

    val timeChamber: TimeChamber by lazy { TimeChamber(this, habitRepository, questRepository) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        // Load the sound effects now, so the first one is ready when it's needed.
        appScope.launch { sounds }
        appScope.launch {
            habitRepository.syncDays()
            reminders.ensureScheduled(habitRepository.getActiveHabits())
            reminders.updateEveningNudge(prefs.eveningNudge.value, prefs.eveningNudgeMinutes.value, replace = false)
            timeChamber.restore()
        }
        // Keep the home-screen widget in step with the app.
        appScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            database.achievementDao().observeWidgetChanges()
                .debounce(WIDGET_REFRESH_DELAY_MILLIS)
                .collect { com.saiyanhabits.widget.WidgetUpdater.update(this@SaiyanApp) }
        }
    }
}
