package com.cdlstudy

import android.app.Application
import com.cdlstudy.ai.AiSettings
import com.cdlstudy.content.ContentStore
import com.cdlstudy.data.AppDatabase
import com.cdlstudy.data.StudyRepository
import com.cdlstudy.manual.ManualStore
import com.cdlstudy.reminder.Reminders
import com.cdlstudy.ui.ReadingSettings
import com.cdlstudy.ui.ReminderSettings
import com.cdlstudy.ui.TestDaySettings

class CdlApp : Application() {

    lateinit var repository: StudyRepository
        private set

    /** Your own copy of the manual. The app ships none. */
    lateinit var manual: ManualStore
        private set

    /** Focus text, size and spacing: one setting for every screen. */
    lateinit var reading: ReadingSettings
        private set

    /** Whether the daily reminder is on, and when. */
    lateinit var reminder: ReminderSettings
        private set

    /** Your own API key for AI grading. Empty until you put one in. */
    lateinit var ai: AiSettings
        private set

    /** Which test-day items you have ticked off. */
    lateinit var testDay: TestDaySettings
        private set

    /** Content files that would not parse. The home screen shows them. */
    var contentErrors: List<String> = emptyList()
        private set

    override fun onCreate() {
        super.onCreate()
        val loaded = ContentStore.load(this)
        contentErrors = loaded.errors
        repository = StudyRepository(AppDatabase.get(this), loaded.content)
        manual = ManualStore(this)
        reading = ReadingSettings(this)
        reminder = ReminderSettings(this)
        ai = AiSettings(this)
        testDay = TestDaySettings(this)
        Reminders.createChannel(this)
    }
}
