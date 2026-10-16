package com.budgetr.app.ui.screens.plan

import com.budgetr.app.util.UserMessages
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.repository.AnnualFundRepository
import com.budgetr.app.data.repository.BudgetRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.data.repository.DailyTargetRepository
import com.budgetr.app.util.AnnualFundCalculator
import com.budgetr.app.util.DailyTargetCalculator
import com.budgetr.app.util.HistoryCalculator
import com.budgetr.app.util.PayPeriodCalculator
import com.budgetr.app.util.PaydayPlanCalculator
import com.budgetr.app.util.PaydayPlanTotals
import com.budgetr.app.util.PreferencesManager
import com.budgetr.app.util.SpendTags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanLineUi(
    val tag: String,
    val amountInput: String,
    val hadBudget: Boolean,
    /** Average spend per pay period from history, when there is some. */
    val average: Double?
)

data class PaydayPlanUiState(
    val isLoading: Boolean = true,
    val incomeInput: String = "",
    val fixedCosts: Double = 0.0,
    val goalContributions: Double = 0.0,
    val debtMinimums: Double = 0.0,
    /** Extra on top of minimums chosen in the debt payoff planner (included in [debtMinimums]). */
    val debtExtra: Double = 0.0,
    /** Net set aside for yearly bills this payday; negative when a fund is paying its bill. */
    val annualFunds: Double = 0.0,
    val includeGoals: Boolean = true,
    val includeDebts: Boolean = true,
    val lines: List<PlanLineUi> = emptyList(),
    val knownTags: List<String> = SpendTags.DEFAULTS,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
) {
    val totals: PaydayPlanTotals
        get() = PaydayPlanTotals(
            income = PaydayPlanCalculator.parseAmount(incomeInput) ?: 0.0,
            fixedCosts = fixedCosts,
            goals = if (includeGoals) goalContributions else 0.0,
            debts = if (includeDebts) debtMinimums else 0.0,
            assigned = lines.sumOf { PaydayPlanCalculator.parseAmount(it.amountInput)?.coerceAtLeast(0.0) ?: 0.0 },
            annualFunds = annualFunds
        )

    /** Tags that could still be added to the plan. */
    val addableTags: List<String>
        get() = knownTags.filter { tag -> lines.none { it.tag.equals(tag, ignoreCase = true) } }
}

@HiltViewModel
class PaydayPlanViewModel @Inject constructor(
    private val sheetsRepository: SheetsRepository,
    private val budgetRepository: BudgetRepository,
    private val prefs: PreferencesManager,
    private val dailyTargetRepository: DailyTargetRepository,
    private val reviews: com.budgetr.app.data.repository.PeriodReviewRepository,
    private val annualFundRepository: AnnualFundRepository
) : ViewModel() {

    private val period = PayPeriodCalculator.current(prefs.getPayDay())
    private val periodDays = DailyTargetCalculator.dayIndex(period, period.nextPayday).coerceAtLeast(1)

    private val _uiState = MutableStateFlow(PaydayPlanUiState())
    val uiState: StateFlow<PaydayPlanUiState> = _uiState.asStateFlow()

    init {
        // A one-off snapshot rather than a live flow, so a background refresh can't overwrite
        // amounts the user is in the middle of editing.
        viewModelScope.launch {
            try {
                val transactions = sheetsRepository.getAllTransactions().first()
                annualFundRepository.processPeriod(transactions, period)
                val funds = AnnualFundCalculator.matching(annualFundRepository.getFunds().first(), transactions)
                val envelopes = budgetRepository.getEnvelopes().first()
                val history = HistoryCalculator.latest(sheetsRepository.getPeriodSummaries().first())
                val insights = HistoryCalculator.tagInsights(history, emptyList())
                val averages = insights.associate { it.tag.lowercase() to it.average }

                val lines = PaydayPlanCalculator.initialLines(envelopes, insights).map { line ->
                    PlanLineUi(
                        tag = line.tag,
                        amountInput = formatAmount(line.amount),
                        hadBudget = line.hadBudget,
                        average = averages[line.tag.lowercase()]?.takeIf { it > 0 }
                    )
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        incomeInput = formatAmount(PaydayPlanCalculator.income(transactions)),
                        fixedCosts = PaydayPlanCalculator.fixedCosts(transactions),
                        goalContributions = PaydayPlanCalculator.goalContributions(sheetsRepository.getSavingsGoals().first()),
                        debtMinimums = PaydayPlanCalculator.debtPayments(sheetsRepository.getDebts().first(), prefs.getDebtExtra()),
                        debtExtra = prefs.getDebtExtra(),
                        annualFunds = -AnnualFundCalculator.potAdjustment(funds, period),
                        includeGoals = prefs.isPlanIncludeGoals(),
                        includeDebts = prefs.isPlanIncludeDebts(),
                        lines = lines,
                        knownTags = SpendTags.knownTags(transactions)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = UserMessages.from(e)) }
            }
        }
    }

    fun setIncome(value: String) = _uiState.update { it.copy(incomeInput = value) }

    fun setIncludeGoals(include: Boolean) {
        prefs.setPlanIncludeGoals(include)
        _uiState.update { it.copy(includeGoals = include) }
    }

    fun setIncludeDebts(include: Boolean) {
        prefs.setPlanIncludeDebts(include)
        _uiState.update { it.copy(includeDebts = include) }
    }

    fun setLineAmount(tag: String, value: String) = _uiState.update { state ->
        state.copy(lines = state.lines.map { if (it.tag == tag) it.copy(amountInput = value) else it })
    }

    fun addLine(tag: String) = _uiState.update { state ->
        state.copy(lines = state.lines + PlanLineUi(tag, amountInput = "", hadBudget = false, average = null))
    }

    /** Puts whatever is unassigned into [tag], so the plan balances to £0. */
    fun assignRemainderTo(tag: String) = _uiState.update { state ->
        val left = state.totals.leftToAssign
        state.copy(lines = state.lines.map { line ->
            if (line.tag != tag) return@map line
            val current = PaydayPlanCalculator.parseAmount(line.amountInput) ?: 0.0
            line.copy(amountInput = formatAmount((current + left).coerceAtLeast(0.0)))
        })
    }

    /** Saves each category's amount as its budget for this pay period. £0 removes a budget. */
    fun save() {
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val existing = budgetRepository.getEnvelopes().first().associateBy { it.tag.lowercase() }
                state.lines.forEach { line ->
                    // Blank or unreadable leaves the budget as it is; only an explicit 0 removes it
                    val amount = PaydayPlanCalculator.parseAmount(line.amountInput) ?: return@forEach
                    val current = existing[line.tag.lowercase()]
                    if (amount > 0) {
                        budgetRepository.setEnvelope(line.tag, amount, rollover = current?.rollover ?: false)
                    } else if (current != null) {
                        budgetRepository.deleteEnvelope(current.tag)
                    }
                }
                prefs.setPaydayPlanPending(false)
                // The plan's money for non-fixed spending becomes this period's daily target pot,
                // unless an amount was typed in by hand in Settings
                if (prefs.getDailyPotOverride() == null) {
                    // Kept before yearly bill funds, which the daily target applies itself
                    prefs.setDailyPot(
                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.UK).format(period.start),
                        state.totals.toAssign.coerceAtLeast(0.0) + state.annualFunds
                    )
                    dailyTargetRepository.notifySettingsChanged()
                }
                val start = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.UK).format(period.start)
                reviews.savePlan(com.budgetr.app.data.model.ReviewPlan(start, prefs.getDailyPotOverride() ?: prefs.getDailyPotAmount(), countBudgeted = prefs.isDailyTargetCountingBudgeted()))
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = UserMessages.from(e)) }
            }
        }
    }

    /** The daily spend target base this plan would give. */
    fun dailyBase(totals: PaydayPlanTotals): Double = totals.toAssign.coerceAtLeast(0.0) / periodDays

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun formatAmount(value: Double): String =
        if (value == kotlin.math.floor(value)) value.toLong().toString() else String.format(java.util.Locale.ROOT, "%.2f", value)
}
