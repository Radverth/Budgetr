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
import com.budgetr.app.data.model.RecurringCostReview
import kotlin.math.abs

/** Says when a subscription or bill's amount changes, e.g. "Netflix went up to £12.99". */
object PriceChangeNotifier {

    private const val CHANNEL_ID = "price_changes"
    private const val NOTIFICATION_ID = 5000

    /** Title and text for the changes found in one sync. */
    fun message(changes: List<RecurringCostReview>): Pair<String, String> {
        fun line(c: RecurringCostReview): String {
            val from = abs(c.previousAmount ?: 0.0)
            val to = abs(c.lastAmount)
            return "${c.info.trim()} went ${if (to > from) "up" else "down"} from ${from.toCurrencyString()} to ${to.toCurrencyString()}"
        }
        return if (changes.size == 1) line(changes.single()) to "Check it's right on the Subscriptions screen."
        else "${changes.size} bills changed price" to changes.joinToString(". ") { line(it) } + "."
    }

    fun notify(context: Context, changes: List<RecurringCostReview>) {
        if (changes.isEmpty()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Price changes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a bill or subscription's amount changes"
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
        val (title, text) = message(changes)
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }
}
