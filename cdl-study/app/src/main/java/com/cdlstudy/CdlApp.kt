package com.cdlstudy

import android.app.Application
import com.cdlstudy.content.ContentStore
import com.cdlstudy.data.AppDatabase
import com.cdlstudy.data.StudyRepository
import com.cdlstudy.manual.ManualStore
import com.cdlstudy.ui.ReadingSettings

class CdlApp : Application() {

    lateinit var repository: StudyRepository
        private set

    /** Your own copy of the manual. The app ships none. */
    lateinit var manual: ManualStore
        private set

    /** Focus text, size and spacing: one setting for every screen. */
    lateinit var reading: ReadingSettings
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
    }
}
