package com.saiyanhabits

import android.app.Application
import com.saiyanhabits.data.AppDatabase
import com.saiyanhabits.data.HabitRepository

class SaiyanApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }

    val habitRepository: HabitRepository by lazy {
        HabitRepository(database.habitDao(), database.completionDao())
    }
}
