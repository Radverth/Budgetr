package com.budgetr.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.data.repository.SyncOutcome
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/** Sends changes saved offline once the phone is back online, retrying with back-off. */
class PendingSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncEntryPoint {
        fun sheetsRepository(): SheetsRepository
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java).sheetsRepository()
        return when (repository.syncPendingChanges()) {
            SyncOutcome.OFFLINE -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "pending-sheet-sync"

        /** Runs as soon as there's a connection. Calling it again while one is waiting does nothing. */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<PendingSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
