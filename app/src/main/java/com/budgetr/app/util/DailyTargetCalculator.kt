package com.budgetr.app.util

import com.budgetr.app.data.model.DailyTransfer
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** One day of the pay period in the daily spend target. */
data class DailyTargetDay(
    val date: Date,
    /** This day's own share: the base, plus or minus any borrowing / paying forward. */
    val allocation: Double,
    /** What the day started with: [allocation] plus whatever earlier days left (or minus what
     *  they overspent). */
    val target: Double,
    val spent: Double
) {
    val carriedIn: Double get() = target - allocation
    val left: Double get() = target - spent
}

data class DailyTarget(
    val base: Double,
    val days: List<DailyTargetDay>,
    /** Index of today in [days]. */
    val todayIndex: Int
) {
    val today: DailyTargetDay get() = days[todayIndex]
    val leftToday: Double get() = today.left
    val isOverToday: Boolean get() = leftToday < 0
    /** Tomorrow's target if nothing more is spent today; null on the last day of the period. */
    val tomorrowIfNoMoreSpent: Double? get() = days.getOrNull(todayIndex + 1)?.let { leftToday + it.allocation }
    val daysLeft: Int get() = days.size - todayIndex
    /** Most that can be borrowed into today: everything later days still have. */
    val borrowable: Double get() = days.drop(todayIndex + 1).sumOf { it.allocation }.coerceAtLeast(0.0)
    /** Most that can be paid forward from today: what's left of it, never below £0. */
    val payableForward: Double get() = if (todayIndex + 1 < days.size) leftToday.coerceAtLeast(0.0) else 0.0
}

/**
 * Pure calculation behind the daily spend target. The period's pot (money for non-fixed
 * spending) is split into an equal base per day. Each day's target is its own share plus
 * whatever earlier days didn't spend, so spending £4 of £5 makes tomorrow £6, and
 * overspending lowers the days after. Everything is rebuilt from the transactions and the
 * transfers, so edits and backdated costs are reflected straight away.
 */
object DailyTargetCalculator {

    fun calculate(
        period: PayPeriod,
        pot: Double,
        transactions: List<Transaction>,
        transfers: List<DailyTransfer>,
        today: Date = Date(),
        countsAsSpending: (Transaction) -> Boolean = { true }
    ): DailyTarget {
        val dayCount = dayIndex(period, period.nextPayday).coerceAtLeast(1)
        val base = pot / dayCount
        val allocation = DoubleArray(dayCount) { base }

        transfers.forEach { transfer -> applyTransfer(allocation, period, transfer) }

        val spent = DoubleArray(dayCount)
        val fmt = dateFormat()
        transactions
            .filter { it.category == TransactionCategory.ONE_OFF_COST && countsAsSpending(it) }
            .forEach { tx ->
                val date = runCatching { fmt.parse(tx.date) }.getOrNull() ?: return@forEach
                val index = dayIndex(period, date)
                if (index in 0 until dayCount) spent[index] += abs(tx.amount)
            }

        var running = 0.0
        val days = (0 until dayCount).map { i ->
            running += allocation[i]
            val day = DailyTargetDay(dateAt(period, i), allocation[i], running, spent[i])
            running -= spent[i]
            day
        }
        val todayIndex = dayIndex(period, today).coerceIn(0, dayCount - 1)
        return DailyTarget(base, days, todayIndex)
    }

    /** A transfer whose other side has nowhere to go (e.g. spread from the last day) is ignored,
     *  so money is never created or lost. */
    private fun applyTransfer(allocation: DoubleArray, period: PayPeriod, transfer: DailyTransfer) {
        val fmt = dateFormat()
        val from = runCatching { fmt.parse(transfer.createdOn) }.getOrNull()?.let { dayIndex(period, it) } ?: return
        if (from !in allocation.indices) return
        val target = transfer.targetDay?.let { day ->
            runCatching { fmt.parse(day) }.getOrNull()?.let { dayIndex(period, it) } ?: return
        }
        if (target != null) {
            if (target !in allocation.indices || target <= from) return
            allocation[from] += transfer.amount
            allocation[target] -= transfer.amount
        } else {
            val later = allocation.size - from - 1
            if (later <= 0) return
            allocation[from] += transfer.amount
            for (i in from + 1 until allocation.size) allocation[i] -= transfer.amount / later
        }
    }

    /** Whole days from the period start to [date]; rounding absorbs clock-change days. */
    fun dayIndex(period: PayPeriod, date: Date): Int =
        ((startOfDay(date).time - period.start.time).toDouble() / DAY_MILLIS).roundToInt()

    private fun dateAt(period: PayPeriod, index: Int): Date = Calendar.getInstance().apply {
        time = period.start
        add(Calendar.DAY_OF_MONTH, index)
    }.time

    private fun dateFormat() = SimpleDateFormat("dd/MM/yyyy", Locale.UK)

    private fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time
}
