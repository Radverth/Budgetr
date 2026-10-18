package com.budgetr.app.ui.screens.budgethub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.repository.AnnualFundRepository
import com.budgetr.app.data.repository.BillDueDayRepository
import com.budgetr.app.data.repository.BudgetRepository
import com.budgetr.app.data.repository.CashFlowRepository
import com.budgetr.app.data.repository.CategoryRuleRepository
import com.budgetr.app.data.repository.DailyTargetRepository
import com.budgetr.app.data.repository.DailyTargetState
import com.budgetr.app.data.repository.PeriodReviewRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.AnnualFundCalculator
import com.budgetr.app.util.BillsCalendar
import com.budgetr.app.util.CalendarEventKind
import com.budgetr.app.util.EnvelopeCalculator
import com.budgetr.app.util.PayPeriodCalculator
import com.budgetr.app.util.ForecastWarning
import com.budgetr.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/** Summaries shown on the Budget tab's rows. */
data class BudgetHubUiState(
    val daily: DailyTargetState? = null,
    val budgetCount: Int = 0,
    val budgetsOver: Int = 0,
    val paydayPlanPending: Boolean = false,
    val nextPayday: Date? = null,
    val historyPeriods: Int = 0,
    val ruleCount: Int = 0,
    /** The first account forecast to dip below £0 before payday, if any. */
    val forecastWarning: ForecastWarning? = null,
    val forecastReady: Boolean = false,
    val reviewCount: Int = 0,
    /** The latest review has no leftover decision yet. */
    val reviewReady: Boolean = false,
    /** Yearly bills in the Sheet, how many are being saved for, and this payday's set-aside. */
    val annualBills: Int = 0,
    val annualSaving: Int = 0,
    val annualSetAside: Double = 0.0,
    /** e.g. "Next: Council tax on Tue 20 Oct". */
    val nextBill: String? = null,
    val message: String? = null
)

@HiltViewModel
class BudgetHubViewModel @Inject constructor(
    private val dailyTargetRepository: DailyTargetRepository,
    private val budgetRepository: BudgetRepository,
    private val sheetsRepository: SheetsRepository,
    private val categoryRuleRepository: CategoryRuleRepository,
    private val cashFlowRepository: CashFlowRepository,
    private val periodReviewRepository: PeriodReviewRepository,
    private val annualFundRepository: AnnualFundRepository,
    private val billDueDayRepository: BillDueDayRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetHubUiState(paydayPlanPending = prefs.isPaydayPlanPending()))
    val uiState: StateFlow<BudgetHubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                dailyTargetRepository.observe(),
                budgetRepository.getEnvelopes(),
                sheetsRepository.getAllTransactions(),
                sheetsRepository.getPeriodSummaries()
            ) { daily, envelopes, transactions, history ->
                val statuses = envelopes.map {
                    EnvelopeCalculator.status(it, transactions, daily.period, daily.target.daysLeft)
                }
                _uiState.value.copy(
                    daily = daily,
                    budgetCount = envelopes.size,
                    budgetsOver = statuses.count { it.isOver },
                    nextPayday = daily.period.nextPayday,
                    historyPeriods = history.size,
                    paydayPlanPending = prefs.isPaydayPlanPending()
                )
            }.collect { state -> _uiState.update { state.copy(message = it.message, ruleCount = it.ruleCount, forecastWarning = it.forecastWarning, forecastReady = it.forecastReady, reviewCount = it.reviewCount, reviewReady = it.reviewReady, annualBills = it.annualBills, annualSaving = it.annualSaving, annualSetAside = it.annualSetAside, nextBill = it.nextBill) } }
        }
        viewModelScope.launch {
            categoryRuleRepository.getRules().collect { rules -> _uiState.update { it.copy(ruleCount = rules.size) } }
        }
        viewModelScope.launch {
            combine(periodReviewRepository.observeReviews(), periodReviewRepository.observeReceipts()) { reviews, receipts ->
                val latest = reviews.firstOrNull()?.review
                reviews.size to (latest != null && latest.nextPeriodStart == prefs.getLastPayPeriodStart() && receipts[latest.periodStart] == null)
            }.collect { (count, ready) -> _uiState.update { it.copy(reviewCount = count, reviewReady = ready) } }
        }
        viewModelScope.launch {
            combine(sheetsRepository.getAllTransactions(), annualFundRepository.getFunds()) { transactions, funds ->
                AnnualFundCalculator.statuses(transactions, funds, PayPeriodCalculator.current(prefs.getPayDay()))
            }.collect { bills ->
                _uiState.update {
                    it.copy(
                        annualBills = bills.size,
                        annualSaving = bills.count { b -> b.saving },
                        annualSetAside = bills.filter { b -> b.saving }.sumOf { b -> b.setAsideNow }
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(sheetsRepository.getAllTransactions(), billDueDayRepository.getDueDays()) { transactions, dueDays ->
                val now = Calendar.getInstance()
                val today = now.time
                val next = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, 1) }
                (BillsCalendar.events(now.get(Calendar.YEAR), now.get(Calendar.MONTH), transactions, dueDays, prefs.getPayDay()) +
                    BillsCalendar.events(next.get(Calendar.YEAR), next.get(Calendar.MONTH), transactions, dueDays, prefs.getPayDay()))
                    .firstOrNull { it.kind == CalendarEventKind.BILL && it.date.after(today) }
                    ?.let { "Next: ${it.label} on ${SimpleDateFormat("EEE d MMM", Locale.UK).format(it.date)}" }
            }.collect { text -> _uiState.update { it.copy(nextBill = text) } }
        }
        viewModelScope.launch {
            cashFlowRepository.observe().collect { snapshot ->
                _uiState.update {
                    it.copy(forecastWarning = snapshot.forecast?.warnings?.firstOrNull(), forecastReady = snapshot.forecast != null)
                }
            }
        }
    }

    /** The plan screen can clear the payday flag while this tab is in the back stack. */
    fun refreshPaydayFlag() = _uiState.update { it.copy(paydayPlanPending = prefs.isPaydayPlanPending()) }

    fun addTransfer(amount: Double, targetDay: Date?) {
        val state = _uiState.value.daily ?: return
        viewModelScope.launch {
            dailyTargetRepository.addTransfer(state, amount, targetDay)
            _uiState.update { it.copy(message = if (amount > 0) "Borrowed from later days" else "Paid forward to later days") }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
