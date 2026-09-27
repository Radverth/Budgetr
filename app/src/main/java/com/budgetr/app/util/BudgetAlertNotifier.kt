package com.budgetr.app.util

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
import com.budgetr.app.MainActivity
import com.budgetr.app.R
import com.budgetr.app.data.model.TransactionCategory

const val BUDGET_ALERT_CHANNEL_ID = "budget_alerts"
private const val NOTIFICATION_ID_BASE = 3000

/** Posts a notification the moment a saved transaction pushes a capped category over its
 *  limit. Fired directly at save time (see TransactionsViewModel) rather than on a schedule,
 *  since the crossing is only knowable right after the write that caused it. */
object BudgetAlertNotifier {

    fun notifyOverBudget(context: Context, category: TransactionCategory, spend: Double, limit: Double) {
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
        val notification = NotificationCompat.Builder(context, BUDGET_ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${category.displayName} budget exceeded")
            .setContentText("You've spent ${spend.toCurrencyString()} of your ${limit.toCurrencyString()} cap this pay period.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + category.ordinal, notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            BUDGET_ALERT_CHANNEL_ID,
            "Budget alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when a category spending cap is exceeded"
        }
        manager.createNotificationChannel(channel)
    }
}
