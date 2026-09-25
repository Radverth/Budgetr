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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

const val REMINDER_CHANNEL_ID = "transaction_reminders"
private const val REMINDER_NOTIFICATION_ID = 2001

/** Fires once at the scheduled time, posts the reminder notification, then reschedules itself
 *  for the same time tomorrow (each AlarmManager trigger set via [ReminderScheduler] is one-shot). */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prefs: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        postNotification(context)
        if (prefs.isReminderEnabled()) {
            ReminderScheduler.schedule(context, prefs.getReminderHour(), prefs.getReminderMinute())
        }
    }

    private fun postNotification(context: Context) {
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
            .setContentTitle("Log today's transactions")
            .setContentText("Keep your budget accurate — add anything you've spent or earned today.")
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
