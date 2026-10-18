package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

/** A fixed cost whose amount changed from [from] to [to] on [on]. */
data class PriceChange(val from: Double, val to: Double, val on: Date) {
    val difference: Double get() = to - from
    val wentUp: Boolean get() = difference > 0
}

data class Subscription(
    val bill: Transaction,
    val amount: Double,
    /** The next day it's charged, today included; null if it's never active. */
    val nextCharge: Date?,
    /** No due day of its own, so it's shown on payday. */
    val onPayday: Boolean,
    /** Charges a year: 12, or the number of months it's active in. */
    val timesAYear: Int,
    val change: PriceChange?
) {
    val yearly: Double get() = amount * timesAYear
    val monthly: Double get() = yearly / 12
}

/** Pure logic behind the subscription tracker: every fixed cost with its next charge and what it costs over a year. */
object SubscriptionTracker {

    /**
     * @param changes recorded price changes by [CashFlowCalculator.billKey].
     */
    fun list(
        transactions: List<Transaction>,
        dueDays: Map<String, Int>,
        payDay: Int,
        changes: Map<String, PriceChange>,
        today: Date
    ): List<Subscription> {
        val bills = transactions.filter { it.category == TransactionCategory.FIXED_COST }
        // Look ahead a year, a month at a time, for each bill's next charge
        val cal = Calendar.getInstance().apply { time = today }
        val upcoming = (0..12).flatMap { offset ->
            val month = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, offset) }
            BillsCalendar.events(month.get(Calendar.YEAR), month.get(Calendar.MONTH), bills, dueDays, payDay)
        }.filter { !it.date.before(startOfDay(today)) }

        return bills.map { bill ->
            val key = CashFlowCalculator.billKey(bill.account, bill.info)
            val next = upcoming.firstOrNull { it.account == bill.account && it.label == bill.info.trim() }
            Subscription(
                bill = bill,
                amount = abs(bill.amount),
                nextCharge = next?.date,
                onPayday = dueDays[key] == null,
                timesAYear = bill.activeMonths?.distinct()?.size?.coerceIn(1, 12) ?: 12,
                change = changes[key]
            )
        }.sortedWith(compareBy<Subscription, Date?>(nullsLast<Date>()) { it.nextCharge }.thenBy { it.bill.info.lowercase() })
    }

    /** A price change when [previous] (the last amount seen) and [current] differ by a penny or more. */
    fun detect(previous: Double?, current: Double, now: Date): PriceChange? {
        val before = previous?.let { abs(it) } ?: return null
        val after = abs(current)
        return if (abs(after - before) >= 0.005) PriceChange(before, after, now) else null
    }

    private fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time
}
