package com.budgetr.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.widget.WidgetRefresher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Calendar
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RefreshEntryPoint {
    fun sheetsRepository(): SheetsRepository
}

/**
 * Every few hours with a connection: send anything saved offline, re-read the Sheet (so
 * changes made on a computer or another phone show up) and redraw the widget.
 */
class BackgroundRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, RefreshEntryPoint::class.java).sheetsRepository()
        return try {
            repository.syncPendingChanges()
            repository.refreshAccountBalances()
            repository.refreshAllTransactions()
            repository.refreshSavingsGoals()
            repository.refreshDebts()
            WidgetRefresher.refresh(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // Try again at the next window rather than hammering a flaky connection
            Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "background-sheet-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackgroundRefreshWorker>(4, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/**
 * Just after midnight: redraw the widget so "left today" moves to the new day, then book the
 * next night. No connection needed, since the daily target is worked out on the phone.
 */
class MidnightWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WidgetRefresher.refresh(applicationContext)
        schedule(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "midnight-widget-refresh"

        /** Milliseconds from [now] until five minutes past the next midnight. */
        fun delayUntilAfterMidnight(now: Long = System.currentTimeMillis()): Long {
            val next = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 5)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            return next.timeInMillis - now
        }

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<MidnightWidgetWorker>()
                .setInitialDelay(delayUntilAfterMidnight(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
