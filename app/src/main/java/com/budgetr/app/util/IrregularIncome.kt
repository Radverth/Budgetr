package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The daily target's money when income varies: only what has arrived so far counts. */
data class IrregularPlan(
    /** Income dated in this pay period, up to and including today. */
    val received: Double,
    /** Bills, goals, debts and yearly-bill set-asides, taken from the first money in. */
    val commitments: Double,
    /** What's left of [received] for day-to-day spending, never below £0. */
    val available: Double,
    /** Each day's own share: every payment is spread over the days left from the day it arrived. */
    val allocations: DoubleArray
)

/**
 * Pure logic for the "My income varies" mode. Pay periods stay monthly, but instead of
 * fixing the period's pot at payday from expected income, the pot grows as money actually
 * arrives. Commitments come off the first money in; anything above them is shared evenly
 * across the days left, so a big payment mid-month doesn't all land on one day.
 */
object IrregularIncome {

    private val incomeCategories = setOf(TransactionCategory.INCOME, TransactionCategory.SALARY, TransactionCategory.RECURRING_INCOME)

    fun plan(period: PayPeriod, transactions: List<Transaction>, commitments: Double, today: Date): IrregularPlan {
        val dayCount = DailyTargetCalculator.dayIndex(period, period.nextPayday).coerceAtLeast(1)
        val todayIndex = DailyTargetCalculator.dayIndex(period, today).coerceIn(0, dayCount - 1)
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)

        // Income by the day it arrived, ignoring anything before this period or still to come
        val byDay = DoubleArray(dayCount)
        transactions.filter { it.category in incomeCategories && it.amount > 0 }.forEach { tx ->
            val date = runCatching { fmt.parse(tx.date) }.getOrNull() ?: return@forEach
            val index = DailyTargetCalculator.dayIndex(period, date)
            if (index in 0..todayIndex) byDay[index] += tx.amount
        }

        val allocations = DoubleArray(dayCount)
        val owed = commitments.coerceAtLeast(0.0)
        var received = 0.0
        var available = 0.0
        for (k in 0..todayIndex) {
            if (byDay[k] == 0.0) continue
            received += byDay[k]
            val nowAvailable = (received - owed).coerceAtLeast(0.0)
            val added = nowAvailable - available
            available = nowAvailable
            if (added > 0) for (d in k until dayCount) allocations[d] += added / (dayCount - k)
        }
        return IrregularPlan(received, owed, available, allocations)
    }
}
