package com.budgetr.app.data.backup

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

/**
 * Everything Budgetr keeps only on the phone, so it can be saved to the Google Sheet and
 * restored on a new phone. Transactions, goals and debts already live in the Sheet.
 */
@JsonClass(generateAdapter = true)
data class PhoneBackup(
    val version: Int = CURRENT_VERSION,
    val savedAt: Long,
    val categoryBudgets: List<CategoryBudgetBackup> = emptyList(),
    val envelopes: List<EnvelopeBackup> = emptyList(),
    val goalLinks: List<GoalLinkBackup> = emptyList(),
    val subscriptionReviews: List<ReviewBackup> = emptyList(),
    val dailyTransfers: List<DailyTransferBackup> = emptyList(),
    val rollovers: List<RolloverBackup> = emptyList(),
    val categoryRules: List<CategoryRuleBackup> = emptyList(),
    val billDueDays: List<BillDueDayBackup> = emptyList(),
    val periodReviews: List<com.budgetr.app.data.local.entity.PeriodReviewEntity> = emptyList(),
    val settings: SettingsBackup = SettingsBackup()
) {
    /** True when there's nothing worth restoring. */
    val isEmpty: Boolean
        get() = categoryBudgets.isEmpty() && envelopes.isEmpty() && goalLinks.isEmpty() &&
            subscriptionReviews.isEmpty() && dailyTransfers.isEmpty() && rollovers.isEmpty() &&
            categoryRules.isEmpty() && billDueDays.isEmpty() && periodReviews.isEmpty()

    /** Same contents, ignoring when it was saved, so an unchanged backup isn't written again. */
    fun sameContentAs(other: PhoneBackup?): Boolean = other != null && copy(savedAt = 0) == other.copy(savedAt = 0)

    companion object {
        const val CURRENT_VERSION = 1
    }
}

@JsonClass(generateAdapter = true)
data class CategoryBudgetBackup(val category: String, val limit: Double)

@JsonClass(generateAdapter = true)
data class EnvelopeBackup(
    val tag: String,
    val limit: Double,
    val rollover: Boolean,
    val carriedOver: Double,
    val carriedForPeriod: String?
)

@JsonClass(generateAdapter = true)
data class GoalLinkBackup(val goalName: String, val category: String)

@JsonClass(generateAdapter = true)
data class ReviewBackup(
    val account: String,
    val info: String,
    val firstSeenDate: Long,
    val lastAmount: Double,
    val dismissedUntil: Long?
)

@JsonClass(generateAdapter = true)
data class DailyTransferBackup(val periodStart: String, val createdOn: String, val amount: Double, val targetDay: String?)

@JsonClass(generateAdapter = true)
data class CategoryRuleBackup(val pattern: String, val tag: String)

@JsonClass(generateAdapter = true)
data class BillDueDayBackup(val account: String, val infoKey: String, val dueDay: Int)

@JsonClass(generateAdapter = true)
data class RolloverBackup(val account: String, val amount: Double, val recordedDate: String)

/** Preferences worth keeping. Sign-in details and which Sheet is open are left out on purpose. */
@JsonClass(generateAdapter = true)
data class SettingsBackup(
    val payDay: Int? = null,
    val lastPayPeriodStart: String? = null,
    val reminderEnabled: Boolean? = null,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val spendingPromptEnabled: Boolean? = null,
    val spendingPromptThreshold: Double? = null,
    val planIncludeGoals: Boolean? = null,
    val planIncludeDebts: Boolean? = null,
    val dailyPotOverride: Double? = null,
    val dailyPotPeriod: String? = null,
    val dailyPotAmount: Double? = null,
    val dailyCountBudgeted: Boolean? = null,
    val dailyOnHome: Boolean? = null,
    val homeShowPeriod: Boolean? = null,
    val homeShowGoals: Boolean? = null,
    val homeShowAccounts: Boolean? = null,
    val forecastSpendingAccount: String? = null
)

/** Turns a backup into one line of JSON for a Sheet cell, and back. */
object BackupCodec {
    private val adapter = Moshi.Builder().build().adapter(PhoneBackup::class.java)

    fun encode(backup: PhoneBackup): String = adapter.toJson(backup)

    /** Null for an empty, unreadable or newer-than-this-app backup, so restore never half-applies one. */
    fun decode(json: String?): PhoneBackup? {
        if (json.isNullOrBlank()) return null
        val backup = runCatching { adapter.fromJson(json) }.getOrNull() ?: return null
        return backup.takeIf { it.version <= PhoneBackup.CURRENT_VERSION }
    }
}
