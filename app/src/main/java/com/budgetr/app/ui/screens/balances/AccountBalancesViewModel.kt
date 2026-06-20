package com.budgetr.app.ui.screens.balances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.model.AccountBalance
import com.budgetr.app.data.model.BalanceRollover
import com.budgetr.app.data.model.SheetTab
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.data.repository.SheetsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private data class BalanceSummaryData(
    val balances: List<AccountBalance>,
    val rollovers: List<BalanceRollover>,
    val totalAvailable: Double,
    val totalIncome: Double,
    val totalOutgoings: Double
)

data class AccountBalancesUiState(
    val isRefreshing: Boolean = false,
    val balances: List<AccountBalance> = emptyList(),
    val rollovers: List<BalanceRollover> = emptyList(),
    val totalAvailable: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalOutgoings: Double = 0.0,
    val error: String? = null,
    val successMessage: String? = null,
    // Rename dialog
    val renameAccount: AccountBalance? = null,
    val renameText: String = "",
    val isRenaming: Boolean = false,
    // Add account dialog
    val showAddAccount: Boolean = false,
    val newAccountName: String = "",
    val isAddingAccount: Boolean = false,
    // Delete account dialog
    val deleteAccount: AccountBalance? = null,
    val isDeletingAccount: Boolean = false,
    // Rollover edit dialog
    val rolloverEditAccount: String? = null,
    val rolloverEditText: String = "",
    val isRecordingRollover: Boolean = false
)

@HiltViewModel
class AccountBalancesViewModel @Inject constructor(
    private val repository: SheetsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountBalancesUiState())
    val uiState: StateFlow<AccountBalancesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val balancesAndRollovers = combine(
                repository.getAccountBalances(),
                repository.getBalanceRollovers()
            ) { balances, rollovers -> Pair(balances, rollovers) }

            val allTransactions = combine(
                repository.getTransactions(SheetTab.MONZO),
                repository.getTransactions(SheetTab.HALIFAX_DEBIT),
                repository.getTransactions(SheetTab.HALIFAX_CREDIT)
            ) { monzoTx, halifaxDebitTx, halifaxCreditTx ->
                monzoTx + halifaxDebitTx + halifaxCreditTx
            }

            combine(balancesAndRollovers, allTransactions) { (balances, rollovers), allTx ->
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.time
                val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)

                // The Cover Sheet pre-calculates remainingBalance including ALL recurring income.
                // We subtract any recurring income that hasn't fallen due yet so the app shows
                // the correct available balance before those dates arrive.
                val futureRecurringByAccount = allTx
                    .filter {
                        it.category == TransactionCategory.RECURRING_INCOME &&
                        run {
                            val txDate = runCatching { dateFmt.parse(it.date) }.getOrNull()
                            txDate != null && txDate.after(today)
                        }
                    }
                    .groupBy { it.sheetTab.sheetName }
                    .mapValues { (_, txs) -> txs.sumOf { it.amount } }

                val adjustedBalances = balances.map { balance ->
                    val futureIncome = futureRecurringByAccount[balance.account] ?: 0.0
                    val rolloverAmount = rollovers.find { it.account == balance.account }?.rolloverAmount ?: 0.0
                    balance.copy(remainingBalance = balance.remainingBalance - futureIncome + rolloverAmount)
                }

                val income = allTx
                    .filter {
                        when (it.category) {
                            TransactionCategory.INCOME,
                            TransactionCategory.SALARY -> true
                            TransactionCategory.RECURRING_INCOME -> {
                                // Only count recurring income on or after its scheduled date
                                val txDate = runCatching { dateFmt.parse(it.date) }.getOrNull()
                                txDate != null && !txDate.after(today)
                            }
                            else -> false
                        }
                    }
                    .sumOf { it.amount }
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1–12
                val outgoings = allTx
                    .filter {
                        it.category != TransactionCategory.INCOME &&
                        it.category != TransactionCategory.SALARY &&
                        it.category != TransactionCategory.RECURRING_INCOME &&
                        it.category != TransactionCategory.TRANSFER &&
                        // Exclude fixed costs restricted to other months
                        (it.activeMonths == null || it.activeMonths.contains(currentMonth))
                    }
                    .sumOf { kotlin.math.abs(it.amount) }
                BalanceSummaryData(
                    balances = adjustedBalances,
                    rollovers = rollovers,
                    totalAvailable = adjustedBalances.sumOf { it.remainingBalance },
                    totalIncome = income,
                    totalOutgoings = outgoings
                )
            }.collect { data ->
                _uiState.update {
                    it.copy(
                        balances = data.balances,
                        rollovers = data.rollovers,
                        totalAvailable = data.totalAvailable,
                        totalIncome = data.totalIncome,
                        totalOutgoings = data.totalOutgoings
                    )
                }
            }
        }
        checkPayPeriod()
        refresh()
    }

    private fun checkPayPeriod() {
        viewModelScope.launch {
            try {
                val wasReset = repository.checkAndProcessNewPayPeriod()
                if (wasReset) {
                    _uiState.update { it.copy(successMessage = "New pay period started — balances rolled over and one-off costs cleared") }
                }
            } catch (_: Exception) {}
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                repository.refreshAccountBalances()
                repository.refreshTransactions(SheetTab.MONZO)
                repository.refreshTransactions(SheetTab.HALIFAX_DEBIT)
                repository.refreshTransactions(SheetTab.HALIFAX_CREDIT)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // --- Rename ---

    fun showRenameDialog(balance: AccountBalance) {
        _uiState.update { it.copy(renameAccount = balance, renameText = balance.account) }
    }

    fun setRenameText(text: String) = _uiState.update { it.copy(renameText = text) }

    fun dismissRenameDialog() = _uiState.update { it.copy(renameAccount = null, renameText = "") }

    fun confirmRename() {
        val account = _uiState.value.renameAccount ?: return
        val newName = _uiState.value.renameText.trim()
        if (newName.isBlank() || newName == account.account) {
            dismissRenameDialog()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRenaming = true) }
            try {
                repository.renameAccount(account.account, newName)
                _uiState.update { it.copy(renameAccount = null, renameText = "", isRenaming = false, successMessage = "Account renamed to \"$newName\"") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRenaming = false, error = e.message) }
            }
        }
    }

    // --- Add Account ---

    fun showAddAccountDialog() = _uiState.update { it.copy(showAddAccount = true, newAccountName = "") }

    fun setNewAccountName(name: String) = _uiState.update { it.copy(newAccountName = name) }

    fun dismissAddAccountDialog() = _uiState.update { it.copy(showAddAccount = false, newAccountName = "") }

    fun confirmAddAccount() {
        val name = _uiState.value.newAccountName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingAccount = true) }
            try {
                repository.addAccount(name)
                _uiState.update { it.copy(showAddAccount = false, newAccountName = "", isAddingAccount = false, successMessage = "\"$name\" account created") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAddingAccount = false, error = e.message) }
            }
        }
    }

    // --- Delete Account ---

    fun showDeleteAccountDialog(balance: AccountBalance) = _uiState.update { it.copy(deleteAccount = balance) }

    fun dismissDeleteAccountDialog() = _uiState.update { it.copy(deleteAccount = null) }

    fun confirmDeleteAccount() {
        val account = _uiState.value.deleteAccount ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingAccount = true) }
            try {
                repository.deleteAccount(account.account)
                _uiState.update { it.copy(deleteAccount = null, isDeletingAccount = false, successMessage = "\"${account.account}\" deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeletingAccount = false, error = e.message) }
            }
        }
    }

    // --- Rollover ---

    fun showRolloverEditDialog(account: String) {
        val existing = _uiState.value.rollovers.find { it.account == account }
        _uiState.update {
            it.copy(
                rolloverEditAccount = account,
                rolloverEditText = existing?.rolloverAmount?.let { amt -> if (amt == 0.0) "" else amt.toString() } ?: ""
            )
        }
    }

    fun setRolloverEditText(text: String) = _uiState.update { it.copy(rolloverEditText = text) }

    fun dismissRolloverEditDialog() = _uiState.update { it.copy(rolloverEditAccount = null, rolloverEditText = "") }

    fun confirmRolloverEdit() {
        val account = _uiState.value.rolloverEditAccount ?: return
        val amount = _uiState.value.rolloverEditText.toDoubleOrNull() ?: 0.0
        viewModelScope.launch {
            _uiState.update { it.copy(isRecordingRollover = true) }
            try {
                val today = SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(Date())
                repository.recordRollover(account, amount, today)
                _uiState.update { it.copy(rolloverEditAccount = null, rolloverEditText = "", isRecordingRollover = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecordingRollover = false, error = e.message) }
            }
        }
    }

    fun deleteRollover(account: String) {
        viewModelScope.launch {
            try {
                repository.deleteRollover(account)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Records current balance of every account as rollover for the next pay period. */
    fun recordAllRollovers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRecordingRollover = true) }
            try {
                val today = SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(Date())
                _uiState.value.balances.forEach { balance ->
                    repository.recordRollover(balance.account, balance.remainingBalance, today)
                }
                _uiState.update { it.copy(isRecordingRollover = false, successMessage = "Rollover recorded for ${_uiState.value.balances.size} accounts") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecordingRollover = false, error = e.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSuccessMessage() = _uiState.update { it.copy(successMessage = null) }
}
