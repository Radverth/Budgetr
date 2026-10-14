package com.budgetr.app.ui.screens.budgethub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.repository.BudgetRepository
import com.budgetr.app.data.repository.DailyTargetRepository
import com.budgetr.app.data.repository.DailyTargetState
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.EnvelopeCalculator
import com.budgetr.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/** Summaries shown on the Budget tab's rows. */
data class BudgetHubUiState(
    val daily: DailyTargetState? = null,
    val budgetCount: Int = 0,
    val budgetsOver: Int = 0,
    val paydayPlanPending: Boolean = false,
    val nextPayday: Date? = null,
    val historyPeriods: Int = 0,
    val message: String? = null
)

@HiltViewModel
class BudgetHubViewModel @Inject constructor(
    private val dailyTargetRepository: DailyTargetRepository,
    private val budgetRepository: BudgetRepository,
    private val sheetsRepository: SheetsRepository,
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
            }.collect { state -> _uiState.update { state.copy(message = it.message) } }
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
