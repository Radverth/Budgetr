package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/** A bill or income landing in an account on [date]. [amount] is negative for money going out. */
data class ForecastEvent(val date: Date, val account: String, val label: String, val amount: Double)

/** An account's balance at the end of [date]. */
data class ForecastDay(val date: Date, val balance: Double)

data class AccountForecast(
    val account: String,
    /** End of each day from today until the day before payday. */
    val days: List<ForecastDay>,
    /** Bills and income still to come, oldest first. */
    val events: List<ForecastEvent>,
    val lowest: ForecastDay,
    /** The first day the balance ends below £0, or null if it never does. */
    val firstBelowZero: ForecastDay?
) {
    val endBalance: Double get() = days.last().balance
}

/** "Move [moveAmount] from [fromAccount] into [account] before [date]". No [fromAccount] when
 *  no other account can spare it. */
data class ForecastWarning(
    val account: String,
    val date: Date,
    val shortfall: Double,
    val moveAmount: Double,
    val fromAccount: String?
)

data class CashFlowForecast(
    val accounts: List<AccountForecast>,
    val warnings: List<ForecastWarning>,
    /** The account day-to-day spending is expected to come from. */
    val spendingAccount: String?
)

/**
 * Pure projection of each account's balance from today until payday.
 *
 * The Sheet's balances already have every fixed cost taken off (they're all dated on payday),
 * so bills that aren't due yet are added back first and then taken off again on their due day.
 * Recurring income lands on its own date, and the expected day-to-day spending (what the daily
 * target still allows) comes out of the spending account each day.
 */
object CashFlowCalculator {

    /** Key for a bill's due day: the account and description, ignoring case and spaces at the ends. */
    fun billKey(account: String, info: String): String = "$account\u0000${info.trim().lowercase(Locale.ROOT)}"

    /** When a bill due on day-of-month [dueDay] falls in [period]. Unset means payday. */
    fun dueDate(dueDay: Int?, period: PayPeriod): Date {
        if (dueDay == null) return period.start
        val cal = Calendar.getInstance().apply { time = period.start }
        repeat(2) {
            val candidate = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, minOf(dueDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
            if (!candidate.time.before(period.start)) {
                // A weekend-moved payday can make the period a day or two short of a month
                return if (candidate.time.before(period.nextPayday)) candidate.time else addDays(period.nextPayday, -1)
            }
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, 1)
        }
        return period.start
    }

    /** The account with the most one-off spending in [period], for when none has been chosen. */
    fun busiestSpendingAccount(transactions: List<Transaction>, period: PayPeriod, accounts: List<String>): String? {
        val fmt = dateFormat()
        return transactions
            .filter { it.category == TransactionCategory.ONE_OFF_COST && it.account in accounts }
            .filter { tx -> runCatching { fmt.parse(tx.date) }.getOrNull()?.let { !it.before(period.start) && it.before(period.nextPayday) } == true }
            .groupBy { it.account }
            .maxByOrNull { (_, txs) -> txs.sumOf { abs(it.amount) } }
            ?.key
            ?: accounts.firstOrNull()
    }

    /**
     * @param balances each account's balance today as the app shows it (future recurring income
     *   taken out, carry-over added).
     * @param dueDays bill due days by [billKey].
     * @param expectedSpend spending still expected on each day from today, index 0 = today.
     */
    fun forecast(
        period: PayPeriod,
        today: Date,
        balances: Map<String, Double>,
        transactions: List<Transaction>,
        dueDays: Map<String, Int>,
        spendingAccount: String?,
        expectedSpend: List<Double>
    ): CashFlowForecast {
        val start = startOfDay(today)
        val dayCount = period.daysUntilPayday(start)
        val dates = (0 until dayCount).map { addDays(start, it) }
        val fmt = dateFormat()

        val bills = transactions.filter { it.category == TransactionCategory.FIXED_COST && it.account in balances }
            .mapNotNull { tx ->
                val due = dueDate(dueDays[billKey(tx.account, tx.info)], period)
                val month = Calendar.getInstance().apply { time = due }.get(Calendar.MONTH) + 1
                if (tx.activeMonths != null && month !in tx.activeMonths) return@mapNotNull null
                ForecastEvent(due, tx.account, tx.info.trim(), -abs(tx.amount))
            }
        val income = transactions.filter { it.category == TransactionCategory.RECURRING_INCOME && it.account in balances }
            .mapNotNull { tx ->
                val date = runCatching { fmt.parse(tx.date) }.getOrNull() ?: return@mapNotNull null
                ForecastEvent(startOfDay(date), tx.account, tx.info.trim(), abs(tx.amount))
            }
        // Only what happens after today moves the balance; today's bills and income are already in it
        val upcoming = (bills + income).filter { it.date.after(start) && it.date.before(period.nextPayday) }
            .sortedWith(compareBy<ForecastEvent> { it.date }.thenBy { it.amount })

        val accounts = balances.map { (account, balance) ->
            val events = upcoming.filter { it.account == account }
            // Bills not due yet are still in the bank, even though the Sheet has taken them off
            var running = balance + events.filter { it.amount < 0 }.sumOf { -it.amount }
            val days = dates.mapIndexed { index, date ->
                running += events.filter { it.date == date }.sumOf { it.amount }
                if (account == spendingAccount) running -= expectedSpend.getOrElse(index) { 0.0 }.coerceAtLeast(0.0)
                ForecastDay(date, running)
            }
            AccountForecast(
                account = account,
                days = days,
                events = events,
                lowest = days.minBy { it.balance },
                firstBelowZero = days.firstOrNull { it.balance < -0.005 }
            )
        }
        return CashFlowForecast(accounts, warnings(accounts), spendingAccount)
    }

    private fun warnings(accounts: List<AccountForecast>): List<ForecastWarning> {
        // Each account can only lend what it would still have at its own lowest point
        val spare = accounts.associate { it.account to it.lowest.balance.coerceAtLeast(0.0) }.toMutableMap()
        return accounts.filter { it.firstBelowZero != null }.sortedBy { it.firstBelowZero!!.date }.map { forecast ->
            val shortfall = -forecast.lowest.balance
            val move = ceil(shortfall / 5.0) * 5.0
            val from = spare.filterKeys { it != forecast.account }.filterValues { it >= move }.maxByOrNull { it.value }?.key
            if (from != null) spare[from] = spare.getValue(from) - move
            ForecastWarning(forecast.account, forecast.firstBelowZero!!.date, shortfall, move, from)
        }
    }

    private fun dateFormat() = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply { isLenient = false }

    private fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time

    private fun addDays(date: Date, days: Int): Date = Calendar.getInstance().apply {
        time = date
        add(Calendar.DAY_OF_MONTH, days)
    }.time
}
