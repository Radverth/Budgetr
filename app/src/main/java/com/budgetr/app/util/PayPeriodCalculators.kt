package com.budgetr.app.util

import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** The pay period containing a given day: from [start] (a payday) up to, but not including,
 *  [nextPayday]. Both dates are midnight and already weekend-adjusted. */
data class PayPeriod(val start: Date, val nextPayday: Date) {

    /** Whole days from [today] until [nextPayday], counting today. Always at least 1, since
     *  [today] is inside the period. */
    fun daysUntilPayday(today: Date = Date()): Int {
        // roundToInt absorbs the odd 23/25-hour day either side of a clock change
        val days = ((nextPayday.time - startOfDay(today).time).toDouble() / DAY_MILLIS).roundToInt()
        return days.coerceAtLeast(1)
    }
}

/** Pure calculations for pay-period dates. Paydays that land on a weekend move back to the
 *  Friday before, and a payday past the end of a short month uses its last day. */
object PayPeriodCalculator {

    /** The real payday in the calendar month of [year]/[month] (Calendar's 0-based month). */
    fun effectivePayday(year: Int, month: Int, payDay: Int): Date {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }
        cal.set(Calendar.DAY_OF_MONTH, minOf(payDay, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> cal.add(Calendar.DAY_OF_MONTH, -1)
            Calendar.SUNDAY -> cal.add(Calendar.DAY_OF_MONTH, -2)
        }
        return cal.time
    }

    /** The pay period that [today] falls in. */
    fun current(payDay: Int, today: Date = Date()): PayPeriod {
        val day = startOfDay(today)
        val cal = Calendar.getInstance().apply { time = day }
        // A weekend adjustment can pull a payday back into the previous calendar month (e.g. the
        // 1st on a Sunday), so look at paydays either side of this month and pick the pair around today.
        val paydays = (-1..2).map { offset ->
            val c = cal.clone() as Calendar
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, offset)
            effectivePayday(c.get(Calendar.YEAR), c.get(Calendar.MONTH), payDay)
        }
        val start = paydays.last { !it.after(day) }
        val next = paydays.first { it.after(day) }
        return PayPeriod(start, next)
    }
}

data class SafeToSpend(
    val perDay: Double,
    val thisWeek: Double,
    val daysLeft: Int,
    /** Money left this period before anything is set aside. */
    val available: Double = 0.0,
    /** Money still unspent in category budgets, kept aside for those categories. */
    val reserved: Double = 0.0
) {
    /** Spent more than there was this period. */
    val isOverspent: Boolean get() = available < 0
    /** Still in credit, but category budgets need more than is left. */
    val isOverBudgeted: Boolean get() = !isOverspent && available - reserved < 0
}

/** Pure calculation behind Home's "safe to spend" card: spreads what's left this pay period
 *  evenly over the days until payday, after setting aside what category budgets still need. */
object SafeToSpendCalculator {

    /** [available] is money left for the rest of the period. Fixed costs are already taken
     *  off, because they're dated at the start of each period. */
    fun calculate(available: Double, daysLeft: Int, reserved: Double = 0.0): SafeToSpend {
        val days = daysLeft.coerceAtLeast(1)
        val kept = reserved.coerceAtLeast(0.0)
        // Overspent: show the shortfall per day. Otherwise never below £0 a day, even when
        // budgets need more than is left.
        val perDay = if (available < 0) available / days else (available - kept).coerceAtLeast(0.0) / days
        return SafeToSpend(
            perDay = perDay,
            thisWeek = perDay * minOf(7, days),
            daysLeft = days,
            available = available,
            reserved = kept
        )
    }
}

private fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
    time = date
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.time
