package com.budgetr.app.data.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.budgetr.app.MainActivity
import com.budgetr.app.R
import com.budgetr.app.data.repository.BillDueDayRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.BillsCalendar
import com.budgetr.app.util.CalendarEvent
import com.budgetr.app.util.PreferencesManager
import com.budgetr.app.util.ReminderScheduler
import com.budgetr.app.util.toCurrencyString
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BillReminderEntryPoint {
    fun sheetsRepository(): SheetsRepository
    fun billDueDayRepository(): BillDueDayRepository
    fun preferencesManager(): PreferencesManager
}

/**
 * Each evening at 6pm: if any bill with its own due day goes out tomorrow, say so, then book
 * the next evening. Works from the phone's copy of the Sheet, so no connection is needed.
 */
class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, BillReminderEntryPoint::class.java)
        val prefs = entry.preferencesManager()
        runCatching {
            if (prefs.isBillRemindersEnabled()) {
                val bills = BillsCalendar.dueTomorrow(
                    Date(),
                    entry.sheetsRepository().getAllTransactions().first(),
                    entry.billDueDayRepository().getDueDays().first(),
                    prefs.getPayDay()
                )
                if (bills.isNotEmpty()) notify(applicationContext, bills)
            }
        }
        schedule(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "bill-due-tomorrow"
        private const val CHANNEL_ID = "bill_reminders"
        private const val NOTIFICATION_ID = 4000
        private const val HOUR = 18

        fun schedule(context: Context) {
            val delay = ReminderScheduler.nextTriggerTimeMillis(HOUR, 0) - System.currentTimeMillis()
            val request = OneTimeWorkRequestBuilder<BillReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** Title and text, e.g. "Council tax goes out tomorrow" / "£142.50 from Monzo". */
        fun message(bills: List<CalendarEvent>): Pair<String, String> {
            val total = bills.sumOf { abs(it.amount) }
            return if (bills.size == 1) {
                val bill = bills.single()
                "${bill.label} goes out tomorrow" to "${abs(bill.amount).toCurrencyString()} from ${bill.account}"
            } else {
                val names = bills.map { "${it.label} ${abs(it.amount).toCurrencyString()}" }
                "${bills.size} bills go out tomorrow" to
                    names.dropLast(1).joinToString(", ") + " and " + names.last() + " (${total.toCurrencyString()} in all)"
            }
        }

        private fun notify(context: Context, bills: List<CalendarEvent>) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Bills due tomorrow", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "The evening before a bill with a due day goes out"
                }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val open = PendingIntent.getActivity(
                context, NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val (title, text) = message(bills)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
