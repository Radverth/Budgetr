package com.budgetr.app.ui.screens.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.repository.BillDueDayRepository
import com.budgetr.app.data.repository.BillRow
import com.budgetr.app.data.repository.CashFlowRepository
import com.budgetr.app.util.CashFlowForecast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class CashFlowUiState(
    val isLoading: Boolean = true,
    val forecast: CashFlowForecast? = null,
    val accounts: List<String> = emptyList(),
    val spendingAccountIsAuto: Boolean = true,
    val spendPerDay: Double = 0.0,
    val nextPayday: Date? = null,
    val bills: List<BillRow> = emptyList(),
    val editingBill: BillRow? = null
)

@HiltViewModel
class CashFlowViewModel @Inject constructor(
    private val cashFlowRepository: CashFlowRepository,
    private val billDueDayRepository: BillDueDayRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashFlowUiState())
    val uiState: StateFlow<CashFlowUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            cashFlowRepository.observe().collect { snapshot ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        forecast = snapshot.forecast,
                        accounts = snapshot.accounts,
                        spendingAccountIsAuto = snapshot.spendingAccountIsAuto,
                        spendPerDay = snapshot.spendPerDay,
                        nextPayday = snapshot.nextPayday,
                        bills = snapshot.bills
                    )
                }
            }
        }
    }

    fun setSpendingAccount(account: String?) = cashFlowRepository.setSpendingAccount(account)

    fun editBill(bill: BillRow) = _uiState.update { it.copy(editingBill = bill) }

    fun dismissBill() = _uiState.update { it.copy(editingBill = null) }

    /** Saves when [bill] leaves the bank; null puts it back to payday. */
    fun setDueDay(bill: BillRow, dueDay: Int?) {
        viewModelScope.launch {
            billDueDayRepository.setDueDay(bill.account, bill.info, dueDay)
            _uiState.update { it.copy(editingBill = null) }
        }
    }
}
