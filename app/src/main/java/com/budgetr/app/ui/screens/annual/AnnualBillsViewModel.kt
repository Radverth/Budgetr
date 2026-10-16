package com.budgetr.app.ui.screens.annual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.repository.AnnualFundRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.AnnualBillStatus
import com.budgetr.app.util.AnnualFundCalculator
import com.budgetr.app.util.PayPeriodCalculator
import com.budgetr.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnnualBillsUiState(
    val isLoading: Boolean = true,
    val bills: List<AnnualBillStatus> = emptyList(),
    val showAccount: Boolean = false,
    /** A bill waiting for "stop saving" to be confirmed, since its saved money is let go. */
    val confirmStop: AnnualBillStatus? = null
) {
    val setAsideNow: Double get() = bills.filter { it.saving }.sumOf { it.setAsideNow }
    val savedTotal: Double get() = bills.sumOf { it.fund?.saved ?: 0.0 }
}

@HiltViewModel
class AnnualBillsViewModel @Inject constructor(
    private val sheetsRepository: SheetsRepository,
    private val annualFundRepository: AnnualFundRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnnualBillsUiState())
    val uiState: StateFlow<AnnualBillsUiState> = _uiState.asStateFlow()

    private fun period() = PayPeriodCalculator.current(prefs.getPayDay())

    init {
        viewModelScope.launch {
            combine(sheetsRepository.getAllTransactions(), annualFundRepository.getFunds()) { transactions, funds ->
                val bills = AnnualFundCalculator.statuses(transactions, funds, period())
                bills to (bills.map { it.bill.account }.distinct().size > 1)
            }.collect { (bills, showAccount) ->
                _uiState.update { it.copy(isLoading = false, bills = bills, showAccount = showAccount) }
            }
        }
    }

    fun setSaving(status: AnnualBillStatus, saving: Boolean) {
        if (!saving && (status.fund?.saved ?: 0.0) > 0.0 && _uiState.value.confirmStop == null) {
            _uiState.update { it.copy(confirmStop = status) }
            return
        }
        _uiState.update { it.copy(confirmStop = null) }
        viewModelScope.launch { annualFundRepository.setSaving(status.bill, saving, period()) }
    }

    fun dismissStop() = _uiState.update { it.copy(confirmStop = null) }

    fun confirmStop() {
        val status = _uiState.value.confirmStop ?: return
        _uiState.update { it.copy(confirmStop = null) }
        viewModelScope.launch { annualFundRepository.setSaving(status.bill, false, period()) }
    }
}
