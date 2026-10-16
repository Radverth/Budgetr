package com.budgetr.app.data.repository

import com.budgetr.app.data.model.DailyTransfer
import com.budgetr.app.data.model.Debt
import com.budgetr.app.data.model.SavingsGoal
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.util.DailyTarget
import com.budgetr.app.util.DailyTargetCalculator
import com.budgetr.app.util.PayPeriod
import com.budgetr.app.util.PayPeriodCalculator
import com.budgetr.app.util.PaydayPlanCalculator
import com.budgetr.app.util.PreferencesManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Everything a screen needs to show the daily spend target. */
data class DailyTargetState(
    val target: DailyTarget,
    val period: PayPeriod,
    val periodStart: String,
    val pot: Double,
    val potIsManual: Boolean,
    val transfers: List<DailyTransfer>
)

/** Builds the daily spend target from the Sheet cache and the on-device transfers, and fixes
 *  the period's pot the first time it can be worked out, so later edits to income or fixed
 *  costs don't quietly change the base mid-period. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DailyTargetRepository @Inject constructor(
    private val sheetsRepository: SheetsRepository,
    private val budgetRepository: BudgetRepository,
    private val prefs: PreferencesManager,
    private val reviews: PeriodReviewRepository
) {

    /** Re-emits each minute so the target moves on at midnight even if no data changes. */
    private val ticker: Flow<Date> = flow {
        while (true) {
            emit(Date())
            delay(60_000)
        }
    }

    /** Bumped when a daily-target setting changes, so screens update straight away. */
    private val settingsVersion = MutableStateFlow(0)

    fun notifySettingsChanged() = settingsVersion.update { it + 1 }

    fun observe(): Flow<DailyTargetState> {
        val periodStarts = ticker
            .map { PayPeriodCalculator.current(prefs.getPayDay(), it) }
            .distinctUntilChanged()

        return periodStarts.flatMapLatest { period ->
            val periodStart = format(period.start)
            val data = combine(
                sheetsRepository.getAllTransactions(),
                sheetsRepository.getSavingsGoals(),
                sheetsRepository.getDebts(),
                budgetRepository.getEnvelopes()
            ) { transactions, goals, debts, envelopes -> SourceData(transactions, goals, debts, envelopes.map { it.tag.lowercase() }.toSet()) }

            combine(data, budgetRepository.getDailyTransfers(periodStart), ticker, settingsVersion) { source, transfers, now, _ ->
                val manual = prefs.getDailyPotOverride()
                val pot = manual ?: lockedPot(periodStart, source)
                val countBudgeted = prefs.isDailyTargetCountingBudgeted()
                if (manual != null || prefs.getDailyPotPeriod() == periodStart) {
                    reviews.savePlan(com.budgetr.app.data.model.ReviewPlan(periodStart, pot, countBudgeted = countBudgeted))
                }
                val target = DailyTargetCalculator.calculate(
                    period = period,
                    pot = pot,
                    transactions = source.transactions,
                    transfers = transfers,
                    today = now
                ) { tx -> countBudgeted || tx.tag?.lowercase() !in source.budgetedTags }
                DailyTargetState(target, period, periodStart, pot, manual != null, transfers)
            }
        }
    }

    /** Borrows [amount] into today (positive) or pays it forward (negative), spread over the
     *  rest of the period, or against [targetDay] when given. */
    suspend fun addTransfer(state: DailyTargetState, amount: Double, targetDay: Date?) {
        budgetRepository.addDailyTransfer(
            DailyTransfer(
                periodStart = state.periodStart,
                createdOn = format(state.target.today.date),
                amount = amount,
                targetDay = targetDay?.let { format(it) }
            )
        )
    }

    suspend fun undoTransfer(id: Long) = budgetRepository.deleteDailyTransfer(id)

    /** Forget the fixed pot so it's worked out again from current income and fixed costs. */
    fun recalculatePot() {
        prefs.setDailyPot(null, 0.0)
        notifySettingsChanged()
    }

    /** Uses the pot fixed for this period, or works it out and fixes it. Nothing is fixed
     *  while there's no income in the cache yet (e.g. before the first sync), so an empty
     *  cache can't lock in £0 for the whole period. */
    private fun lockedPot(periodStart: String, source: SourceData): Double {
        if (prefs.getDailyPotPeriod() == periodStart) return prefs.getDailyPotAmount()
        val income = PaydayPlanCalculator.income(source.transactions)
        val pot = (income - PaydayPlanCalculator.fixedCosts(source.transactions) -
            (if (prefs.isPlanIncludeGoals()) PaydayPlanCalculator.goalContributions(source.goals) else 0.0) -
            (if (prefs.isPlanIncludeDebts()) PaydayPlanCalculator.debtMinimums(source.debts) else 0.0))
            .coerceAtLeast(0.0)
        if (income > 0) prefs.setDailyPot(periodStart, pot)
        return pot
    }

    private fun format(date: Date) = SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(date)

    private data class SourceData(
        val transactions: List<Transaction>,
        val goals: List<SavingsGoal>,
        val debts: List<Debt>,
        val budgetedTags: Set<String>
    )
}
