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
        private const val KEY_SPENDING_PROMPT_ENABLED = "spending_prompt_enabled"
        private const val KEY_SPENDING_PROMPT_THRESHOLD = "spending_prompt_threshold"
        private const val KEY_PAYDAY_PLAN_PENDING = "payday_plan_pending"
        private const val KEY_PLAN_INCLUDE_GOALS = "plan_include_goals"
        private const val KEY_PLAN_INCLUDE_DEBTS = "plan_include_debts"
        private const val KEY_DAILY_POT_OVERRIDE = "daily_pot_override"
        private const val KEY_DAILY_POT_PERIOD = "daily_pot_period"
        private const val KEY_DAILY_POT_AMOUNT = "daily_pot_amount"
        private const val KEY_DAILY_COUNT_BUDGETED = "daily_count_budgeted"
        private const val KEY_DAILY_ON_HOME = "daily_on_home"
        private const val KEY_DISMISSED_ATTENTION = "dismissed_attention"
        private const val KEY_HOME_SHOW_PERIOD = "home_show_period"
        private const val KEY_HOME_SHOW_GOALS = "home_show_goals"
        private const val KEY_HOME_SHOW_ACCOUNTS = "home_show_accounts"
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

    fun isSpendingPromptEnabled(): Boolean = prefs.getBoolean(KEY_SPENDING_PROMPT_ENABLED, true)
    fun setSpendingPromptEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SPENDING_PROMPT_ENABLED, enabled).apply()

    fun getSpendingPromptThreshold(): Double = prefs.getFloat(KEY_SPENDING_PROMPT_THRESHOLD, 20f).toDouble()
    fun setSpendingPromptThreshold(amount: Double) = prefs.edit().putFloat(KEY_SPENDING_PROMPT_THRESHOLD, amount.toFloat()).apply()

    /** Set when a new pay period starts; cleared once the payday plan is saved or skipped. */
    fun isPaydayPlanPending(): Boolean = prefs.getBoolean(KEY_PAYDAY_PLAN_PENDING, false)
    fun setPaydayPlanPending(pending: Boolean) = prefs.edit().putBoolean(KEY_PAYDAY_PLAN_PENDING, pending).apply()

    fun isPlanIncludeGoals(): Boolean = prefs.getBoolean(KEY_PLAN_INCLUDE_GOALS, true)
    fun setPlanIncludeGoals(include: Boolean) = prefs.edit().putBoolean(KEY_PLAN_INCLUDE_GOALS, include).apply()

    fun isPlanIncludeDebts(): Boolean = prefs.getBoolean(KEY_PLAN_INCLUDE_DEBTS, true)
    fun setPlanIncludeDebts(include: Boolean) = prefs.edit().putBoolean(KEY_PLAN_INCLUDE_DEBTS, include).apply()

    /** Money for non-fixed spending this pay period, typed in by hand. Null means work it out. */
    fun getDailyPotOverride(): Double? = prefs.getString(KEY_DAILY_POT_OVERRIDE, null)?.toDoubleOrNull()
    fun setDailyPotOverride(amount: Double?) = prefs.edit().putString(KEY_DAILY_POT_OVERRIDE, amount?.toString()).apply()

    /** The worked-out pot, fixed for the pay period starting [getDailyPotPeriod] (dd/MM/yyyy). */
    fun getDailyPotPeriod(): String? = prefs.getString(KEY_DAILY_POT_PERIOD, null)
    fun getDailyPotAmount(): Double = prefs.getString(KEY_DAILY_POT_AMOUNT, null)?.toDoubleOrNull() ?: 0.0
    fun setDailyPot(periodStart: String?, amount: Double) = prefs.edit()
        .putString(KEY_DAILY_POT_PERIOD, periodStart)
        .putString(KEY_DAILY_POT_AMOUNT, amount.toString())
        .apply()

    /** Whether spending in categories with their own budget also counts against the daily target. */
    fun isDailyTargetCountingBudgeted(): Boolean = prefs.getBoolean(KEY_DAILY_COUNT_BUDGETED, true)
    fun setDailyTargetCountingBudgeted(count: Boolean) = prefs.edit().putBoolean(KEY_DAILY_COUNT_BUDGETED, count).apply()

    /** Show the daily spend target on Home (and the widget) instead of Safe to spend. */
    fun isDailyTargetOnHome(): Boolean = prefs.getBoolean(KEY_DAILY_ON_HOME, true)
    fun setDailyTargetOnHome(show: Boolean) = prefs.edit().putBoolean(KEY_DAILY_ON_HOME, show).apply()

    /** "Needs attention" rows dismissed in the pay period starting [periodStart]. */
    fun getDismissedAttention(periodStart: String): Set<String> =
        prefs.getStringSet(KEY_DISMISSED_ATTENTION, emptySet()).orEmpty()
            .filter { it.startsWith("$periodStart|") }
            .map { it.removePrefix("$periodStart|") }
            .toSet()

    /** Remembers [key] as dismissed for this period and forgets older periods' dismissals. */
    fun addDismissedAttention(periodStart: String, key: String) {
        val kept = prefs.getStringSet(KEY_DISMISSED_ATTENTION, emptySet()).orEmpty()
            .filter { it.startsWith("$periodStart|") }
            .toSet()
        prefs.edit().putStringSet(KEY_DISMISSED_ATTENTION, kept + "$periodStart|$key").apply()
    }

    fun isHomeShowingPeriod(): Boolean = prefs.getBoolean(KEY_HOME_SHOW_PERIOD, true)
    fun setHomeShowingPeriod(show: Boolean) = prefs.edit().putBoolean(KEY_HOME_SHOW_PERIOD, show).apply()
    fun isHomeShowingGoals(): Boolean = prefs.getBoolean(KEY_HOME_SHOW_GOALS, true)
    fun setHomeShowingGoals(show: Boolean) = prefs.edit().putBoolean(KEY_HOME_SHOW_GOALS, show).apply()
    fun isHomeShowingAccounts(): Boolean = prefs.getBoolean(KEY_HOME_SHOW_ACCOUNTS, true)
    fun setHomeShowingAccounts(show: Boolean) = prefs.edit().putBoolean(KEY_HOME_SHOW_ACCOUNTS, show).apply()

    fun clearAll() = prefs.edit().clear().apply()
}
