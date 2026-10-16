package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class CalendarEventKind { BILL, INCOME }

/** A bill or income landing on [date]. [onPayday] marks a bill with no due day of its own. */
data class CalendarEvent(
    val date: Date,
    val label: String,
    val amount: Double,
    val kind: CalendarEventKind,
    val account: String,
    val onPayday: Boolean = false
)

/** One day's spending against its daily target. */
data class DaySpend(val spent: Double, val target: Double) {
    val underTarget: Boolean get() = spent <= target + 0.005
}

data class CalendarDay(
    val date: Date,
    /** False for the days from the months either side that fill the first and last weeks. */
    val inMonth: Boolean,
    val events: List<CalendarEvent>,
    /** Only for days so far in the current pay period. */
    val spend: DaySpend?,
    val isToday: Boolean
)

/**
 * Pure logic behind the bills calendar. Bills fall on their due day (set on the cash-flow
 * forecast), or on payday when none is set. Salary and other income land on payday and
 * recurring income on its own day of the month. Weeks start on Monday.
 */
object BillsCalendar {

    /** Bills and income in calendar month [month0] (0-based) of [year]. */
    fun events(year: Int, month0: Int, transactions: List<Transaction>, dueDays: Map<String, Int>, payDay: Int): List<CalendarEvent> {
        val paydays = paydaysIn(year, month0, payDay)
        val events = mutableListOf<CalendarEvent>()
        transactions.forEach { tx ->
            when (tx.category) {
                TransactionCategory.FIXED_COST -> {
                    val dueDay = dueDays[CashFlowCalculator.billKey(tx.account, tx.info)]
                    val dates = if (dueDay != null) listOf(dayIn(year, month0, dueDay)) else paydays
                    dates.filter { isActive(tx, it) }.forEach {
                        events += CalendarEvent(it, tx.info.trim(), -abs(tx.amount), CalendarEventKind.BILL, tx.account, onPayday = dueDay == null)
                    }
                }
                TransactionCategory.SALARY, TransactionCategory.INCOME -> paydays.forEach {
                    events += CalendarEvent(it, tx.info.trim().ifEmpty { "Pay" }, abs(tx.amount), CalendarEventKind.INCOME, tx.account)
                }
                TransactionCategory.RECURRING_INCOME -> {
                    val day = tx.date.split("/").firstOrNull()?.toIntOrNull() ?: return@forEach
                    events += CalendarEvent(dayIn(year, month0, day), tx.info.trim(), abs(tx.amount), CalendarEventKind.INCOME, tx.account)
                }
                else -> Unit
            }
        }
        return events.sortedWith(compareBy<CalendarEvent> { it.date }.thenBy { it.kind }.thenBy { it.label.lowercase() })
    }

    /** The month as whole Monday-to-Sunday weeks. [days] gives each day's spending against target, when known. */
    fun grid(year: Int, month0: Int, events: List<CalendarEvent>, days: List<DaySpendOn>, today: Date): List<CalendarDay> {
        val first = Calendar.getInstance().apply { clear(); set(year, month0, 1) }
        // Calendar's SUNDAY = 1 … SATURDAY = 7; Monday-first offset
        val lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val length = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = ((lead + length + 6) / 7) * 7
        val todayKey = key(today)
        val spendByDay = days.associate { key(it.date) to DaySpend(it.spent, it.target) }
        val eventsByDay = events.groupBy { key(it.date) }
        return (0 until cells).map { index ->
            val date = (first.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, index - lead) }.time
            val inMonth = index - lead in 0 until length
            val k = key(date)
            CalendarDay(
                date = date,
                inMonth = inMonth,
                events = if (inMonth) eventsByDay[k].orEmpty() else emptyList(),
                spend = spendByDay[k]?.takeIf { inMonth && k <= todayKey },
                isToday = k == todayKey
            )
        }
    }

    /** Bills with a due day of their own that fall tomorrow, for the evening reminder. */
    fun dueTomorrow(today: Date, transactions: List<Transaction>, dueDays: Map<String, Int>, payDay: Int): List<CalendarEvent> {
        val tomorrow = Calendar.getInstance().apply { time = today; add(Calendar.DAY_OF_MONTH, 1) }
        val k = key(tomorrow.time)
        return events(tomorrow.get(Calendar.YEAR), tomorrow.get(Calendar.MONTH), transactions, dueDays, payDay)
            .filter { it.kind == CalendarEventKind.BILL && !it.onPayday && key(it.date) == k }
    }

    private fun isActive(tx: Transaction, date: Date): Boolean {
        val months = tx.activeMonths ?: return true
        return Calendar.getInstance().apply { time = date }.get(Calendar.MONTH) + 1 in months
    }

    /** Paydays (weekend-adjusted) that land in this calendar month; a moved payday can land in the month before. */
    private fun paydaysIn(year: Int, month0: Int, payDay: Int): List<Date> = (-1..1).mapNotNull { offset ->
        val c = Calendar.getInstance().apply { clear(); set(year, month0, 1); add(Calendar.MONTH, offset) }
        val payday = PayPeriodCalculator.effectivePayday(c.get(Calendar.YEAR), c.get(Calendar.MONTH), payDay)
        val p = Calendar.getInstance().apply { time = payday }
        payday.takeIf { p.get(Calendar.YEAR) == year && p.get(Calendar.MONTH) == month0 }
    }

    /** Day [day] of the month, or its last day in shorter months. */
    private fun dayIn(year: Int, month0: Int, day: Int): Date = Calendar.getInstance().apply {
        clear(); set(year, month0, 1)
        set(Calendar.DAY_OF_MONTH, day.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
    }.time

    private fun key(date: Date): String = SimpleDateFormat("yyyyMMdd", Locale.UK).format(date)
}

/** A day's spending and target, as the daily target works them out. */
data class DaySpendOn(val date: Date, val spent: Double, val target: Double)
