package com.budgetr.app.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.repository.BillDueDayRepository
import com.budgetr.app.data.repository.BudgetRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.CashFlowCalculator
import com.budgetr.app.util.PreferencesManager
import com.budgetr.app.util.PriceChange
import com.budgetr.app.util.Subscription
import com.budgetr.app.util.SubscriptionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import kotlin.math.abs

data class SubscriptionsUiState(
    val isLoading: Boolean = true,
    val subscriptions: List<Subscription> = emptyList(),
    val showAccount: Boolean = false
) {
    val monthlyTotal: Double get() = subscriptions.sumOf { it.monthly }
    val yearlyTotal: Double get() = subscriptions.sumOf { it.yearly }
}

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    sheetsRepository: SheetsRepository,
    billDueDayRepository: BillDueDayRepository,
    budgetRepository: BudgetRepository,
    prefs: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sheetsRepository.getAllTransactions(),
                billDueDayRepository.getDueDays(),
                budgetRepository.getRecurringCostReviews()
            ) { transactions, dueDays, reviews ->
                // Price changes from the last 90 days
                val since = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                val changes = reviews.mapNotNull { r ->
                    val at = r.changedAt?.takeIf { it >= since } ?: return@mapNotNull null
                    val from = r.previousAmount ?: return@mapNotNull null
                    CashFlowCalculator.billKey(r.account, r.info) to PriceChange(abs(from), abs(r.lastAmount), Date(at))
                }.toMap()
                SubscriptionTracker.list(transactions, dueDays, prefs.getPayDay(), changes, Date())
            }.collect { list ->
                _uiState.update { it.copy(isLoading = false, subscriptions = list, showAccount = list.map { s -> s.bill.account }.distinct().size > 1) }
            }
        }
    }
}
