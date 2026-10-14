package com.budgetr.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.budgetr.app.MainActivity
import com.budgetr.app.R
import com.budgetr.app.data.repository.DailyTargetRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

const val REMINDER_CHANNEL_ID = "transaction_reminders"
private const val REMINDER_NOTIFICATION_ID = 2001

/** Fires once at the scheduled time, posts the reminder notification, then reschedules itself
 *  for the same time tomorrow (each AlarmManager trigger set via [ReminderScheduler] is one-shot). */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prefs: PreferencesManager

    @Inject
    lateinit var dailyTargetRepository: DailyTargetRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (prefs.isReminderEnabled()) {
            ReminderScheduler.schedule(context, prefs.getReminderHour(), prefs.getReminderMinute())
        }
        // Reading the daily target touches the database, so finish off the main thread
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                postNotification(context, dailyTargetText())
            } finally {
                pending.finish()
            }
        }
    }

    /** Title and text that mention today's spend target, or null to use the plain reminder. */
    private suspend fun dailyTargetText(): Pair<String, String>? {
        if (!prefs.isDailyTargetOnHome()) return null
        val target = withTimeoutOrNull(5_000) { dailyTargetRepository.observe().first().target } ?: return null
        val tomorrow = target.tomorrowIfNoMoreSpent?.let { " Tomorrow starts at ${it.toCurrencyString()}." } ?: ""
        return if (target.isOverToday) {
            "${(-target.leftToday).toCurrencyString()} over today's target" to
                "Log anything else you've spent today.$tomorrow"
        } else {
            "${target.leftToday.toCurrencyString()} left of today's target" to
                "Log anything you've spent today so it stays accurate.$tomorrow"
        }
    }

    private fun postNotification(context: Context, daily: Pair<String, String>?) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(daily?.first ?: "Log today's transactions")
            .setContentText(daily?.second ?: "Keep your budget accurate — add anything you've spent or earned today.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Transaction reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily reminder to log your transactions"
        }
        manager.createNotificationChannel(channel)
    }
}
