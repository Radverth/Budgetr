package com.budgetr.app

import android.app.Application
import com.budgetr.app.data.backup.BackupWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BudgetrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Daily backup of budgets and settings to the Google Sheet
        BackupWorker.schedule(this)
    }
}
