package com.budgetr.app.util

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Named

class PreferencesManager @Inject constructor(
    @Named("encrypted") private val prefs: SharedPreferences
) {
    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        private const val KEY_SPREADSHEET_NAME = "spreadsheet_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHOTO = "user_photo"
        private const val KEY_PAY_DAY = "pay_day"
        private const val KEY_LAST_PAY_PERIOD_START = "last_pay_period_start"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun setAccessToken(token: String?) = prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()

    fun getSpreadsheetId(): String? = prefs.getString(KEY_SPREADSHEET_ID, null)
    fun setSpreadsheetId(id: String?) = prefs.edit().putString(KEY_SPREADSHEET_ID, id).apply()

    fun getSpreadsheetName(): String? = prefs.getString(KEY_SPREADSHEET_NAME, null)
    fun setSpreadsheetName(name: String?) = prefs.edit().putString(KEY_SPREADSHEET_NAME, name).apply()

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)
    fun setUserEmail(email: String?) = prefs.edit().putString(KEY_USER_EMAIL, email).apply()

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)
    fun setUserName(name: String?) = prefs.edit().putString(KEY_USER_NAME, name).apply()

    fun getUserPhoto(): String? = prefs.getString(KEY_USER_PHOTO, null)
    fun setUserPhoto(url: String?) = prefs.edit().putString(KEY_USER_PHOTO, url).apply()

    fun getPayDay(): Int = prefs.getString(KEY_PAY_DAY, "26")?.toIntOrNull() ?: 26
    fun setPayDay(day: Int) = prefs.edit().putString(KEY_PAY_DAY, day.toString()).apply()

    fun hasSpreadsheet(): Boolean = !getSpreadsheetId().isNullOrBlank()

    fun getLastPayPeriodStart(): String? = prefs.getString(KEY_LAST_PAY_PERIOD_START, null)
    fun setLastPayPeriodStart(date: String) = prefs.edit().putString(KEY_LAST_PAY_PERIOD_START, date).apply()

    fun isReminderEnabled(): Boolean = prefs.getBoolean(KEY_REMINDER_ENABLED, false)
    fun setReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()

    fun getReminderHour(): Int = prefs.getInt(KEY_REMINDER_HOUR, 20)
    fun getReminderMinute(): Int = prefs.getInt(KEY_REMINDER_MINUTE, 0)
    fun setReminderTime(hour: Int, minute: Int) {
        prefs.edit().putInt(KEY_REMINDER_HOUR, hour).putInt(KEY_REMINDER_MINUTE, minute).apply()
    }

    fun clearAll() = prefs.edit().clear().apply()
}
