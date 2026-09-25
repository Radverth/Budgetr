package com.budgetr.app.ui.screens.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.model.SortOrder
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class TransactionsUiState(
    val isRefreshing: Boolean = false,
    val accounts: List<String> = emptyList(),
    val selectedAccount: String? = null,
    val transactions: List<Transaction> = emptyList(),
    val categoryFilter: TransactionCategory? = null,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val error: String? = null,
    val transactionToDelete: Transaction? = null,
    val transactionToEdit: Transaction? = null,
    val showAddSheet: Boolean = false,
    val addSaveCount: Int = 0,
    val payDay: Int = 26
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: SheetsRepository,
    private val prefs: PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialAccount = savedStateHandle.get<String>("tabName")

    private val _uiState = MutableStateFlow(
        TransactionsUiState(selectedAccount = initialAccount, payDay = prefs.getPayDay())
    )
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private val selectedAccountFlow = MutableStateFlow(initialAccount)
    private val categoryFilterFlow = MutableStateFlow<TransactionCategory?>(null)
    private val searchQueryFlow = MutableStateFlow("")
    private val sortOrderFlow = MutableStateFlow(SortOrder.DATE_DESC)

    init {
        // Accounts are dynamic (user-managed), so there's no fixed default. Once the real
        // account list arrives, fall back to the first one if nothing valid is selected yet
        // (e.g. no account was passed via navigation, or the selected one got deleted).
        viewModelScope.launch {
            repository.getAccountBalances().collect { balances ->
                val names = balances.map { it.account }
                _uiState.update { it.copy(accounts = names) }
                val current = selectedAccountFlow.value
                if (names.isNotEmpty() && (current == null || current !in names)) {
                    selectAccount(names.first())
                }
            }
        }

        refresh()

        viewModelScope.launch {
            selectedAccountFlow.flatMapLatest { account ->
                if (account == null) {
                    flowOf(emptyList())
                } else {
                    combine(
                        repository.getTransactions(account),
                        categoryFilterFlow,
                        searchQueryFlow,
                        sortOrderFlow
                    ) { transactions, filter, query, sort ->
                        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
                        var result = transactions
                            // Hide fixed costs restricted to other months
                            .filter { tx ->
                                tx.category != TransactionCategory.FIXED_COST ||
                                tx.activeMonths == null ||
                                tx.activeMonths.contains(currentMonth)
                            }
                        if (filter != null) result = result.filter { it.category == filter }
                        if (query.isNotBlank()) {
                            result = result.filter {
                                it.info.contains(query, ignoreCase = true) ||
                                it.date.contains(query, ignoreCase = true)
                            }
                        }
                        when (sort) {
                            SortOrder.DATE_DESC -> result.sortedByDescending { dateFmt.parseToEpoch(it.date) }
                            SortOrder.DATE_ASC -> result.sortedBy { dateFmt.parseToEpoch(it.date) }
                            SortOrder.AMOUNT_DESC -> result.sortedByDescending { kotlin.math.abs(it.amount) }
                            SortOrder.AMOUNT_ASC -> result.sortedBy { kotlin.math.abs(it.amount) }
                            SortOrder.CATEGORY_ASC -> result.sortedBy { it.category.displayName }
                        }
                    }
                }
            }.collect { filtered ->
                _uiState.update { it.copy(transactions = filtered) }
            }
        }
    }

    fun selectAccount(account: String) {
        selectedAccountFlow.value = account
        _uiState.update { it.copy(selectedAccount = account) }
        refresh(account)
    }

    fun setCategoryFilter(category: TransactionCategory?) {
        categoryFilterFlow.value = category
        _uiState.update { it.copy(categoryFilter = category) }
    }

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSortOrder(order: SortOrder) {
        sortOrderFlow.value = order
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun refresh(account: String? = _uiState.value.selectedAccount) {
        if (account == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                repository.refreshTransactions(account)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun showAddSheet() = _uiState.update { it.copy(showAddSheet = true, transactionToEdit = null) }
    fun showEditSheet(transaction: Transaction) = _uiState.update { it.copy(transactionToEdit = transaction, showAddSheet = true) }
    fun dismissSheet() = _uiState.update { it.copy(showAddSheet = false, transactionToEdit = null) }

    fun confirmDelete(transaction: Transaction) = _uiState.update { it.copy(transactionToDelete = transaction) }
    fun dismissDelete() = _uiState.update { it.copy(transactionToDelete = null) }

    fun saveTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                if (transaction.rowIndex > 0) {
                    val original = _uiState.value.transactionToEdit
                    if (original != null && original.account != transaction.account) {
                        // Account changed: remove from old account, append to new account
                        repository.deleteTransaction(original)
                        repository.addTransaction(transaction.copy(rowIndex = 0))
                    } else {
                        repository.updateTransaction(transaction)
                    }
                    _uiState.update { it.copy(showAddSheet = false, transactionToEdit = null) }
                } else {
                    repository.addTransaction(transaction)
                    _uiState.update { it.copy(addSaveCount = it.addSaveCount + 1) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun saveTransfer(source: Transaction, destination: Transaction) {
        viewModelScope.launch {
            try {
                repository.addTransaction(source)
                repository.addTransaction(destination)
                _uiState.update { it.copy(addSaveCount = it.addSaveCount + 1) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteTransaction() {
        val transaction = _uiState.value.transactionToDelete ?: return
        viewModelScope.launch {
            try {
                repository.deleteTransaction(transaction)
                _uiState.update { it.copy(transactionToDelete = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(transactionToDelete = null, error = e.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

private fun SimpleDateFormat.parseToEpoch(date: String): Long =
    runCatching { parse(date)?.time }.getOrNull() ?: 0L
