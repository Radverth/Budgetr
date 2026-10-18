package com.budgetr.app

import android.app.Application
import com.budgetr.app.data.backup.BackupWorker
import com.budgetr.app.data.sync.BackgroundRefreshWorker
import com.budgetr.app.data.sync.BillReminderWorker
import com.budgetr.app.data.sync.MidnightWidgetWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BudgetrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Daily backup of budgets and settings to the Google Sheet
        BackupWorker.schedule(this)
        // Keep data and the widget current while the app isn't open
        BackgroundRefreshWorker.schedule(this)
        MidnightWidgetWorker.schedule(this)
        // "Due tomorrow" check each evening
        BillReminderWorker.schedule(this)
    }
}
