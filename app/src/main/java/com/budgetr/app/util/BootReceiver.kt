package com.budgetr.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** AlarmManager alarms are cleared on reboot, so re-schedule the reminder (if enabled) once the
 *  device finishes booting. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prefs: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (prefs.isReminderEnabled()) {
            ReminderScheduler.schedule(context, prefs.getReminderHour(), prefs.getReminderMinute())
        }
    }
}
