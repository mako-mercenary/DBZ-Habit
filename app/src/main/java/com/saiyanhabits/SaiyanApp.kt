package com.saiyanhabits

import android.app.Application
import com.saiyanhabits.chamber.TimeChamber
import com.saiyanhabits.data.AppDatabase
import com.saiyanhabits.data.HabitRepository
import com.saiyanhabits.data.QuestRepository
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
