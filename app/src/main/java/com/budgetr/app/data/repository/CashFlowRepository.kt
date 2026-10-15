package com.budgetr.app.data.repository

import com.budgetr.app.data.model.AccountBalance
import com.budgetr.app.data.model.BalanceRollover
import com.budgetr.app.data.model.Envelope
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.util.CashFlowCalculator
import com.budgetr.app.util.CashFlowForecast
import com.budgetr.app.util.EnvelopeCalculator
import com.budgetr.app.util.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** A fixed cost and when it leaves the bank this pay period. [dueDay] null means on payday. */
data class BillRow(
    val account: String,
    val info: String,
    val amount: Double,
    val dueDay: Int?,
    val dueDate: Date
)

data class CashFlowSnapshot(
    /** Null until there's at least one account. */
    val forecast: CashFlowForecast?,
    val accounts: List<String>,
    /** True when the spending account was worked out rather than chosen. */
    val spendingAccountIsAuto: Boolean,
    /** Average spending a day still expected until payday. */
    val spendPerDay: Double,
    val nextPayday: Date,
    val bills: List<BillRow>
)

/** Projects each account's balance until payday for the forecast screen, the Budget tab and
 *  Home; see [CashFlowCalculator]. */
@Singleton
class CashFlowRepository @Inject constructor(
    private val sheetsRepository: SheetsRepository,
    private val budgetRepository: BudgetRepository,
    private val dailyTargetRepository: DailyTargetRepository,
    private val billDueDayRepository: BillDueDayRepository,
    private val prefs: PreferencesManager
) {
    private val chosenSpendingAccount = MutableStateFlow(prefs.getForecastSpendingAccount())

    fun observe(): Flow<CashFlowSnapshot> {
        val sheet = combine(
            sheetsRepository.getAccountBalances(),
            sheetsRepository.getBalanceRollovers(),
            sheetsRepository.getAllTransactions()
        ) { balances, rollovers, transactions -> Triple(balances, rollovers, transactions) }
        return combine(
            sheet,
            dailyTargetRepository.observe(),
            billDueDayRepository.getDueDays(),
            budgetRepository.getEnvelopes(),
            chosenSpendingAccount
        ) { (balances, rollovers, transactions), daily, dueDays, envelopes, chosen ->
            build(balances, rollovers, transactions, daily, dueDays, envelopes, chosen)
        }
    }

    /** Null goes back to working it out from where most one-off spending happens. */
    fun setSpendingAccount(account: String?) {
        prefs.setForecastSpendingAccount(account)
        chosenSpendingAccount.value = account
    }

    private fun build(
        balances: List<AccountBalance>,
        rollovers: List<BalanceRollover>,
        transactions: List<Transaction>,
        daily: DailyTargetState,
        dueDays: Map<String, Int>,
        envelopes: List<Envelope>,
        chosen: String?
    ): CashFlowSnapshot {
        val period = daily.period
        val target = daily.target
        val today = target.today.date
        val accounts = balances.map { it.account }

        // What the daily target still allows today and on each later day, plus money still
        // set aside in category budgets when the target leaves those out
        val daysLeft = target.daysLeft
        val reservedPerDay = if (prefs.isDailyTargetCountingBudgeted()) 0.0 else {
            EnvelopeCalculator.reserved(envelopes.map { EnvelopeCalculator.status(it, transactions, period, daysLeft) }) / daysLeft
        }
        val expectedSpend = (target.todayIndex until target.days.size).map { index ->
            val day = target.days[index]
            (if (index == target.todayIndex) day.left else day.allocation).coerceAtLeast(0.0) + reservedPerDay
        }

        val chosenAccount = chosen?.takeIf { it in accounts }
        val spendingAccount = chosenAccount ?: CashFlowCalculator.busiestSpendingAccount(transactions, period, accounts)
        val forecast = CashFlowCalculator.forecast(
            period, today, adjustedBalances(balances, rollovers, transactions, today),
            transactions, dueDays, spendingAccount, expectedSpend
        )
        val bills = transactions
            .filter { it.category == TransactionCategory.FIXED_COST && it.account in accounts }
            .map { tx ->
                val dueDay = dueDays[CashFlowCalculator.billKey(tx.account, tx.info)]
                BillRow(tx.account, tx.info.trim(), -abs(tx.amount), dueDay, CashFlowCalculator.dueDate(dueDay, period))
            }
            .sortedWith(compareBy<BillRow> { it.dueDate }.thenBy { it.info.lowercase() })

        return CashFlowSnapshot(
            forecast = forecast.takeIf { accounts.isNotEmpty() },
            accounts = accounts,
            spendingAccountIsAuto = chosenAccount == null,
            spendPerDay = if (expectedSpend.isEmpty()) 0.0 else expectedSpend.average(),
            nextPayday = period.nextPayday,
            bills = bills
        )
    }

    /** Balances as Home shows them: future recurring income taken out, carry-over added. */
    private fun adjustedBalances(
        balances: List<AccountBalance>,
        rollovers: List<BalanceRollover>,
        transactions: List<Transaction>,
        today: Date
    ): Map<String, Double> {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
        val futureIncome = transactions
            .filter { tx ->
                tx.category == TransactionCategory.RECURRING_INCOME &&
                    runCatching { fmt.parse(tx.date) }.getOrNull()?.after(today) == true
            }
            .groupBy { it.account }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        return balances.associate { balance ->
            val rollover = rollovers.find { it.account == balance.account }?.rolloverAmount ?: 0.0
            balance.account to balance.remainingBalance - (futureIncome[balance.account] ?: 0.0) + rollover
        }
    }
}
