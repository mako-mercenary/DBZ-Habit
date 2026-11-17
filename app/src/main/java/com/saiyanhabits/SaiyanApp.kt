package com.saiyanhabits

import android.app.Application
import com.saiyanhabits.art.ArtRepository
import com.saiyanhabits.chamber.TimeChamber
import com.saiyanhabits.data.AppDatabase
import com.saiyanhabits.data.BondRepository
import com.saiyanhabits.data.DialogueRepository
import com.saiyanhabits.data.HabitRepository
import com.saiyanhabits.data.QuestRepository
import com.saiyanhabits.data.StoryRepository
import com.saiyanhabits.data.WishRepository
import com.saiyanhabits.reminders.Notifications
import com.saiyanhabits.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SaiyanApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }

    val habitRepository: HabitRepository by lazy { HabitRepository(database) }

    val questRepository: QuestRepository by lazy { QuestRepository(database) }

    val bondRepository: BondRepository by lazy { BondRepository(database) }

    val dialogue: DialogueRepository by lazy { DialogueRepository(this) }

    val storyRepository: StoryRepository by lazy { StoryRepository(this) }

    val wishRepository: WishRepository by lazy { WishRepository(database) }

    val art: ArtRepository by lazy { ArtRepository(this) }

    val reminders: ReminderScheduler by lazy { ReminderScheduler(this) }

    val timeChamber: TimeChamber by lazy { TimeChamber(this, habitRepository, questRepository) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        appScope.launch {
            habitRepository.syncDays()
            reminders.ensureScheduled(habitRepository.getActiveHabits())
            timeChamber.restore()
        }
    }
}
