package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

private fun dateFormat() = SimpleDateFormat("dd/MM/yyyy", Locale.UK)

/** Pure calculations behind the category budget caps shown on Home and the Budgets screen. */
object BudgetCapCalculator {
    private const val APPROACHING_THRESHOLD = 0.8f

    /** Fraction of [limit] used by [spend]. Returns 0f for a non-positive limit rather than
     *  dividing by zero. */
    fun percentUsed(spend: Double, limit: Double): Float {
        if (limit <= 0) return 0f
        return (spend / limit).toFloat().coerceAtLeast(0f)
    }

    /** True once spend crosses [APPROACHING_THRESHOLD] of the limit but hasn't gone over yet. */
    fun isApproachingLimit(spend: Double, limit: Double): Boolean {
        val pct = percentUsed(spend, limit)
        return pct in APPROACHING_THRESHOLD..1f
    }

    fun isOverLimit(spend: Double, limit: Double): Boolean = percentUsed(spend, limit) > 1f
}

/** Pure calculation behind Home's "no-spend streak" card. */
object NoSpendStreakCalculator {

    /** Whole days since the most recent transaction date in [spendDates] (dd/MM/yyyy), or since
     *  [periodStart] if there's no spend yet this period. Unparseable dates are ignored. */
    fun currentStreakDays(spendDates: List<String>, periodStart: Date, today: Date = Date()): Int {
        val fmt = dateFormat()
        val mostRecentSpend = spendDates.mapNotNull { runCatching { fmt.parse(it) }.getOrNull() }.maxOrNull()
        val reference = mostRecentSpend ?: periodStart
        return ((today.time - reference.time) / DAY_MILLIS).toInt().coerceAtLeast(0)
    }
}

data class SpendingTrend(val thisWeek: Double, val lastWeek: Double) {
    /** Percentage change from last week to this week. Positive means spending more. */
    val percentChange: Float?
        get() = if (lastWeek <= 0) null else (((thisWeek - lastWeek) / lastWeek) * 100).toFloat()
}

/** Pure calculation behind Home's "this week vs last week" spending digest. */
object SpendingTrendCalculator {

    /** Buckets [transactions] into the last 7 days and the 7 days before that, relative to
     *  [today]. Returns null if there's no spend in either window to compare. */
    fun weekOverWeek(transactions: List<Transaction>, today: Date = Date()): SpendingTrend? {
        val fmt = dateFormat()
        val dated = transactions.mapNotNull { tx ->
            runCatching { fmt.parse(tx.date) }.getOrNull()?.let { it to kotlin.math.abs(tx.amount) }
        }
        if (dated.isEmpty()) return null

        val oneWeekAgo = today.time - 7 * DAY_MILLIS
        val twoWeeksAgo = today.time - 14 * DAY_MILLIS

        val thisWeek = dated.filter { (date, _) -> date.time in oneWeekAgo..today.time }.sumOf { it.second }
        val lastWeek = dated.filter { (date, _) -> date.time in twoWeeksAgo until oneWeekAgo }.sumOf { it.second }

        if (thisWeek <= 0 && lastWeek <= 0) return null
        return SpendingTrend(thisWeek, lastWeek)
    }
}

/** Pure calculation behind Home's "review your subscriptions" nudge. */
object RecurringCostReviewCalculator {
    const val DEFAULT_REVIEW_AGE_DAYS = 90

    /** True if a fixed cost first seen on [firstSeenDate] is old enough to flag for review,
     *  and hasn't been dismissed (or its dismissal has expired). */
    fun isDueForReview(
        firstSeenDate: Long,
        dismissedUntil: Long?,
        now: Long = System.currentTimeMillis(),
        thresholdDays: Int = DEFAULT_REVIEW_AGE_DAYS
    ): Boolean {
        if (dismissedUntil != null && now < dismissedUntil) return false
        val ageDays = (now - firstSeenDate) / DAY_MILLIS
        return ageDays >= thresholdDays
    }
}
