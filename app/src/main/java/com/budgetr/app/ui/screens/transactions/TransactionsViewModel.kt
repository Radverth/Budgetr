package com.budgetr.app.ui.screens.transactions

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.model.Debt
import com.budgetr.app.data.model.SavingsGoal
import com.budgetr.app.data.model.SortOrder
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.data.repository.BudgetRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.BudgetAlertNotifier
import com.budgetr.app.util.BudgetCapCalculator
import com.budgetr.app.util.EnvelopeCalculator
import com.budgetr.app.util.PayPeriodCalculator
import com.budgetr.app.util.PreferencesManager
import com.budgetr.app.util.SpendTags
import com.budgetr.app.util.spendForCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val isSelecting: Boolean = false,
    val selectedRows: Set<Int> = emptySet(),
    val showBulkCategoryDialog: Boolean = false,
    val isAssigningCategory: Boolean = false,
    val bulkCategoryError: String? = null,
    val message: String? = null,
    val accounts: List<String> = emptyList(),
    val selectedAccount: String? = null,
    val transactions: List<Transaction> = emptyList(),
    val categoryFilter: TransactionCategory? = null,
    /** Spending category filter, only applied while viewing one-off costs. */
    val tagFilter: String? = null,
    val knownTags: List<String> = SpendTags.DEFAULTS,
    val tagSuggestions: Map<String, String> = emptyMap(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val error: String? = null,
    val transactionToDelete: Transaction? = null,
    val transactionToEdit: Transaction? = null,
    val showAddSheet: Boolean = false,
    val addSaveCount: Int = 0,
    val payDay: Int = 26,
    val debts: List<Debt> = emptyList(),
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val spendingPromptEnabled: Boolean = true,
    val spendingPromptThreshold: Double = 20.0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: SheetsRepository,
    private val budgetRepository: BudgetRepository,
    private val prefs: PreferencesManager,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialAccount = savedStateHandle.get<String>("tabName")

    private val _uiState = MutableStateFlow(
        TransactionsUiState(
            selectedAccount = initialAccount,
            payDay = prefs.getPayDay(),
            spendingPromptEnabled = prefs.isSpendingPromptEnabled(),
            spendingPromptThreshold = prefs.getSpendingPromptThreshold()
        )
    )
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private val selectedAccountFlow = MutableStateFlow(initialAccount)
    private val categoryFilterFlow = MutableStateFlow<TransactionCategory?>(null)
    private val tagFilterFlow = MutableStateFlow<String?>(null)
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
            repository.getAllTransactions().collect { all ->
                _uiState.update {
                    it.copy(knownTags = SpendTags.knownTags(all), tagSuggestions = SpendTags.suggestionsByInfo(all))
                }
            }
        }

        viewModelScope.launch {
            combine(repository.getDebts(), repository.getSavingsGoals()) { debts, goals -> debts to goals }
                .collect { (debts, goals) ->
                    _uiState.update { it.copy(debts = debts, savingsGoals = goals) }
                }
        }

        viewModelScope.launch {
            selectedAccountFlow.flatMapLatest { account ->
                if (account == null) {
                    flowOf(emptyList())
                } else {
                    val filters = combine(categoryFilterFlow, tagFilterFlow) { category, tag -> category to tag }
                    combine(
                        repository.getTransactions(account),
                        filters,
                        searchQueryFlow,
                        sortOrderFlow
                    ) { transactions, (filter, tagFilter), query, sort ->
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
                        if (filter == TransactionCategory.ONE_OFF_COST && tagFilter != null) {
                            result = result.filter { it.tag.equals(tagFilter, ignoreCase = true) }
                        }
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
                _uiState.update { state ->
                    state.copy(
                        transactions = filtered,
                        selectedRows = state.selectedRows.intersect(filtered.filter {
                            it.account == state.selectedAccount && it.category == TransactionCategory.ONE_OFF_COST
                        }.map { it.rowIndex }.toSet())
                    )
                }
            }
        }
    }

    fun selectAccount(account: String) {
        if (_uiState.value.isAssigningCategory) return
        selectedAccountFlow.value = account
        _uiState.update { it.copy(selectedAccount = account, transactions = emptyList(), selectedRows = emptySet(), isSelecting = false) }
        refresh(account)
    }

    fun setCategoryFilter(category: TransactionCategory?) {
        if (_uiState.value.isAssigningCategory) return
        categoryFilterFlow.value = category
        _uiState.update { it.copy(categoryFilter = category, selectedRows = emptySet(), isSelecting = false) }
        if (category != TransactionCategory.ONE_OFF_COST) setTagFilter(null)
    }

    fun setTagFilter(tag: String?) {
        if (_uiState.value.isAssigningCategory) return
        tagFilterFlow.value = tag
        _uiState.update { it.copy(tagFilter = tag, selectedRows = emptySet()) }
    }

    fun setSearchQuery(query: String) {
        if (_uiState.value.isAssigningCategory) return
        searchQueryFlow.value = query
        _uiState.update { it.copy(searchQuery = query, selectedRows = emptySet()) }
    }

    fun setSortOrder(order: SortOrder) {
        sortOrderFlow.value = order
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun refresh(account: String? = _uiState.value.selectedAccount) {
        if (account == null || _uiState.value.isAssigningCategory) return
        _uiState.update { it.copy(selectedRows = emptySet()) }
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

    fun startSelection() {
        if (_uiState.value.isRefreshing || _uiState.value.isAssigningCategory) return
        setCategoryFilter(TransactionCategory.ONE_OFF_COST)
        _uiState.update { it.copy(isSelecting = true, selectedRows = emptySet()) }
    }

    fun cancelSelection() {
        if (_uiState.value.isAssigningCategory) return
        _uiState.update { it.copy(isSelecting = false, selectedRows = emptySet(), showBulkCategoryDialog = false) }
    }

    fun toggleSelection(transaction: Transaction) {
        val state = _uiState.value
        if (!state.isSelecting || state.isAssigningCategory || state.isRefreshing ||
            transaction.account != state.selectedAccount || transaction.category != TransactionCategory.ONE_OFF_COST ||
            transaction.rowIndex <= 1) return
        _uiState.update {
            it.copy(selectedRows = if (transaction.rowIndex in it.selectedRows) it.selectedRows - transaction.rowIndex
                else it.selectedRows + transaction.rowIndex)
        }
    }

    fun selectAllShown() {
        val state = _uiState.value
        if (!state.isSelecting || state.isAssigningCategory || state.isRefreshing) return
        val rows = state.transactions.filter {
            it.account == state.selectedAccount && it.category == TransactionCategory.ONE_OFF_COST && it.rowIndex > 1
        }.map { it.rowIndex }.toSet()
        _uiState.update { it.copy(selectedRows = if (it.selectedRows == rows) emptySet() else rows) }
    }

    fun showBulkCategoryDialog() {
        if (_uiState.value.selectedRows.isEmpty() || _uiState.value.isRefreshing) return
        _uiState.update { it.copy(showBulkCategoryDialog = true, bulkCategoryError = null) }
    }

    fun dismissBulkCategoryDialog() {
        if (_uiState.value.isAssigningCategory) return
        _uiState.update { it.copy(showBulkCategoryDialog = false, bulkCategoryError = null) }
    }

    fun assignSpendingCategory(tag: String?) {
        val state = _uiState.value
        if (state.isAssigningCategory || state.isRefreshing) return
        val selected = state.transactions.filter {
            it.account == state.selectedAccount && it.rowIndex in state.selectedRows &&
                it.category == TransactionCategory.ONE_OFF_COST
        }
        if (selected.isEmpty()) return
        _uiState.update { it.copy(isAssigningCategory = true, bulkCategoryError = null) }
        viewModelScope.launch {
            try {
                repository.assignSpendingCategory(selected, tag)
                _uiState.update { it.copy(
                    isSelecting = false, selectedRows = emptySet(), showBulkCategoryDialog = false,
                    message = "Updated spending category for ${selected.size} transactions."
                ) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    bulkCategoryError = "Could not confirm the update. Close this dialog and refresh before retrying. ${e.message.orEmpty()}"
                ) }
            } finally {
                _uiState.update { it.copy(isAssigningCategory = false) }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun showAddSheet() = _uiState.update { it.copy(showAddSheet = true, transactionToEdit = null) }
    fun showEditSheet(transaction: Transaction) = _uiState.update { it.copy(transactionToEdit = transaction, showAddSheet = true) }
    fun dismissSheet() = _uiState.update { it.copy(showAddSheet = false, transactionToEdit = null) }

    fun confirmDelete(transaction: Transaction) = _uiState.update { it.copy(transactionToDelete = transaction) }
    fun dismissDelete() = _uiState.update { it.copy(transactionToDelete = null) }

    fun saveTransaction(transaction: Transaction, linkAction: TransactionLinkAction? = null) {
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
                    val budget = budgetRepository.getCategoryBudgets().first().find { it.category == transaction.category }
                    val period = PayPeriodCalculator.current(prefs.getPayDay())
                    val spendBefore = budget?.let { spendForCategory(repository.getAllTransactions().first(), it.category, period = period) }
                    val envelope = transaction.tag?.let { tag ->
                        budgetRepository.getEnvelopes().first().find { it.tag.equals(tag, ignoreCase = true) }
                    }
                    val envelopeWasOver = envelope?.let {
                        EnvelopeCalculator.status(it, repository.getAllTransactions().first(), period, 1).isOver
                    }

                    repository.addTransaction(transaction)

                    if (budget != null && spendBefore != null && !BudgetCapCalculator.isOverLimit(spendBefore, budget.limit)) {
                        val spendAfter = spendForCategory(repository.getAllTransactions().first(), budget.category, period = period)
                        if (BudgetCapCalculator.isOverLimit(spendAfter, budget.limit)) {
                            BudgetAlertNotifier.notifyOverBudget(context, budget.category, spendAfter, budget.limit)
                        }
                    }
                    if (envelope != null && envelopeWasOver == false) {
                        val after = EnvelopeCalculator.status(envelope, repository.getAllTransactions().first(), period, 1)
                        if (after.isOver) BudgetAlertNotifier.notifyEnvelopeOver(context, envelope.tag, after.spent, after.available)
                    }
                    linkAction?.let { applyLinkAction(it, kotlin.math.abs(transaction.amount)) }
                    _uiState.update { it.copy(addSaveCount = it.addSaveCount + 1) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Applies a one-time balance adjustment for a transaction linked to a debt or goal. Not
     *  reversible — editing or deleting the transaction afterwards won't undo it (see
     *  TransactionLinkAction). */
    private suspend fun applyLinkAction(action: TransactionLinkAction, amount: Double) {
        val delta = if (action.direction == LinkDirection.INCREASE) amount else -amount
        when (action.targetType) {
            LinkTargetType.DEBT -> {
                val debt = repository.getDebts().first().find { it.name == action.targetName } ?: return
                repository.updateDebt(debt.copy(balance = (debt.balance + delta).coerceAtLeast(0.0)))
            }
            LinkTargetType.GOAL -> {
                val goal = repository.getSavingsGoals().first().find { it.name == action.targetName } ?: return
                repository.updateSavingsGoal(goal.copy(savedAmount = (goal.savedAmount + delta).coerceAtLeast(0.0)))
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
