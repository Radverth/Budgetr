package com.budgetr.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Schedules the daily "log your transactions" reminder via a one-shot alarm that reschedules
 *  itself each time it fires (see [ReminderReceiver]). */
object ReminderScheduler {

    private const val REQUEST_CODE = 1001

    fun schedule(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = nextTriggerTimeMillis(hour, minute)
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
        } catch (_: SecurityException) {
            // Some OEMs restrict background alarms further than stock Android; fail silently
            // rather than crash — the reminder just won't fire on that device.
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    /** Next occurrence of [hour]:[minute] at or after [now] — today if it hasn't passed yet, tomorrow otherwise. */
    fun nextTriggerTimeMillis(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
