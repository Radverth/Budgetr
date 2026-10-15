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
import com.budgetr.app.data.repository.DailyTargetRepository
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

/** Pseudo-account for the "All" tab, which lists and searches every account at once. */
const val ALL_ACCOUNTS = "\u0000all"

/** A sheet row in a given account; row numbers alone repeat across accounts. */
data class RowKey(val account: String, val rowIndex: Int)

val Transaction.key: RowKey get() = RowKey(account, rowIndex)

/** Spent and target for one day of the daily spend target, for the list's day headers. */
data class DayTotals(val spent: Double, val target: Double)

data class TransactionsUiState(
    val isRefreshing: Boolean = false,
    val isSelecting: Boolean = false,
    val selectedRows: Set<RowKey> = emptySet(),
    val showBulkCategoryDialog: Boolean = false,
    val isAssigningCategory: Boolean = false,
    val bulkCategoryError: String? = null,
    val message: String? = null,
    val accounts: List<String> = emptyList(),
    val accountsLoaded: Boolean = false,
    val selectedAccount: String? = null,
    val lastAddAccount: String? = null,
    /** Quick add (widget, shortcut, tile) has no open account tab, so it starts on the last one used. */
    val preferLastAccount: Boolean = false,
    val descriptionHistory: List<Transaction> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val categoryFilter: TransactionCategory? = null,
    /** Spending category filter, only applied while viewing one-off costs. */
    val tagFilter: String? = null,
    val knownTags: List<String> = SpendTags.DEFAULTS,
    val tagSuggestions: Map<String, String> = emptyMap(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val error: String? = null,
    /** Deleted but not yet gone from the list; hidden straight away so the swipe feels instant. */
    val pendingDeletes: Set<Transaction> = emptySet(),
    /** Just deleted; the list shows an Undo snackbar for it. */
    val undoable: Transaction? = null,
    /** Inclusive start and end of the date filter, as epoch millis. */
    val dateRange: Pair<Long, Long>? = null,
    /** dd/MM/yyyy → that day's spend and target, when the daily spend target covers it. */
    val dayTotals: Map<String, DayTotals> = emptyMap(),
    val transactionToEdit: Transaction? = null,
    val showAddSheet: Boolean = false,
    val addSaveCount: Int = 0,
    val payDay: Int = 26,
    val debts: List<Debt> = emptyList(),
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val spendingPromptEnabled: Boolean = true,
    val spendingPromptThreshold: Double = 20.0
) {
    /** Keeps a selected row only while the exact same transaction is still in it. Deleting a
     *  row shifts the ones below up, so a row number alone could point at a different cost. */
    internal fun withTransactions(filtered: List<Transaction>): TransactionsUiState {
        val selectedBefore = transactions.filter { it.key in selectedRows }.associateBy { it.key }
        val stillSelected = filtered.filter {
            it.category == TransactionCategory.ONE_OFF_COST && selectedBefore[it.key] == it
        }.map { it.key }.toSet()
        return copy(
            transactions = filtered,
            selectedRows = stillSelected,
            // Once a deleted row has really gone, stop hiding it by hand
            pendingDeletes = pendingDeletes.filter { it in filtered }.toSet()
        )
    }

    /** Re-picking the current account keeps its cached rows (useful offline); a different
     *  account starts with an empty list and no selection. */
    internal fun withAccount(account: String): TransactionsUiState =
        if (account == selectedAccount) this
        else copy(selectedAccount = account, transactions = emptyList(), selectedRows = emptySet(), isSelecting = false)

    val isAllAccounts: Boolean get() = selectedAccount == ALL_ACCOUNTS

    /** Rows to show: everything except deletes still on their way to the Sheet. */
    val visibleTransactions: List<Transaction> get() = transactions.filter { it !in pendingDeletes }

    /** The open account tab wins, since that's the account on screen. The All tab and quick
     *  add use the last account saved to. Removed accounts fall back to the first one. */
    val accountForAdding: String? get() {
        val open = selectedAccount?.takeIf { it in accounts }
        val last = lastAddAccount?.takeIf { it in accounts }
        return (if (preferLastAccount) last ?: open else open ?: last) ?: accounts.firstOrNull()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: SheetsRepository,
    private val budgetRepository: BudgetRepository,
    private val prefs: PreferencesManager,
    private val dailyTargetRepository: DailyTargetRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialAccount = savedStateHandle.get<String>("tabName")

    private val _uiState = MutableStateFlow(
        TransactionsUiState(
            selectedAccount = initialAccount,
            lastAddAccount = prefs.getLastAddAccount(),
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
    private val dateRangeFlow = MutableStateFlow<Pair<Long, Long>?>(null)

    init {
        // Accounts are dynamic (user-managed), so there's no fixed default. Once the real
        // account list arrives, fall back to the first one if nothing valid is selected yet
        // (e.g. no account was passed via navigation, or the selected one got deleted).
        viewModelScope.launch {
            repository.getAccountBalances().collect { balances ->
                val names = balances.map { it.account }
                _uiState.update { it.copy(accounts = names, accountsLoaded = true) }
                val current = selectedAccountFlow.value
                if (names.isNotEmpty() && (current == null || (current !in names && current != ALL_ACCOUNTS))) {
                    selectAccount(names.first())
                }
            }
        }

        refresh()

        viewModelScope.launch {
            repository.getAllTransactions().collect { all ->
                _uiState.update {
                    it.copy(knownTags = SpendTags.knownTags(all), tagSuggestions = SpendTags.suggestionsByInfo(all),
                        descriptionHistory = all)
                }
            }
        }

        viewModelScope.launch {
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
            dailyTargetRepository.observe().collect { state ->
                val totals = state.target.days.associate { fmt.format(it.date) to DayTotals(it.spent, it.target) }
                _uiState.update { it.copy(dayTotals = totals) }
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
                    val filters = combine(categoryFilterFlow, tagFilterFlow, dateRangeFlow) { category, tag, range ->
                        Triple(category, tag, range)
                    }
                    val source = if (account == ALL_ACCOUNTS) repository.getAllTransactions() else repository.getTransactions(account)
                    combine(
                        source,
                        filters,
                        searchQueryFlow,
                        sortOrderFlow
                    ) { transactions, (filter, tagFilter, range), query, sort ->
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
                        if (range != null) {
                            result = result.filter { tx -> dateFmt.parseToEpoch(tx.date) in range.first..range.second }
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
                _uiState.update { it.withTransactions(filtered) }
            }
        }
    }

    fun selectAccount(account: String) {
        if (_uiState.value.isAssigningCategory) return
        selectedAccountFlow.value = account
        _uiState.update { it.withAccount(account) }
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

    /** Inclusive [start]..[end] (epoch millis, any time of day); null clears the filter. */
    fun setDateRange(start: Long?, end: Long?) {
        val range = if (start == null) null else {
            val endOfDay = java.util.Calendar.getInstance().apply {
                timeInMillis = end ?: start
                set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59); set(java.util.Calendar.MILLISECOND, 999)
            }.timeInMillis
            val startOfDay = java.util.Calendar.getInstance().apply {
                timeInMillis = start
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            startOfDay to endOfDay
        }
        dateRangeFlow.value = range
        _uiState.update { it.copy(dateRange = range, selectedRows = emptySet()) }
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
                if (account == ALL_ACCOUNTS) repository.refreshAllTransactions() else repository.refreshTransactions(account)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** Long-press: start selecting, with [transaction] ticked if it can have a category. */
    fun startSelectionWith(transaction: Transaction) {
        if (_uiState.value.isRefreshing || _uiState.value.isAssigningCategory) return
        val first = if (transaction.isCategorisable) setOf(transaction.key) else emptySet()
        _uiState.update { it.copy(isSelecting = true, selectedRows = first) }
    }

    /** Swipe right: set the category of just this one transaction. */
    fun categoriseOne(transaction: Transaction) {
        if (!transaction.isCategorisable || _uiState.value.isAssigningCategory) return
        _uiState.update { it.copy(selectedRows = setOf(transaction.key), showBulkCategoryDialog = true, bulkCategoryError = null) }
    }

    fun cancelSelection() {
        if (_uiState.value.isAssigningCategory) return
        _uiState.update { it.copy(isSelecting = false, selectedRows = emptySet(), showBulkCategoryDialog = false) }
    }

    fun toggleSelection(transaction: Transaction) {
        val state = _uiState.value
        if (!state.isSelecting || state.isAssigningCategory || state.isRefreshing || !transaction.isCategorisable) return
        _uiState.update {
            it.copy(selectedRows = if (transaction.key in it.selectedRows) it.selectedRows - transaction.key
                else it.selectedRows + transaction.key)
        }
    }

    fun selectAllShown() {
        val state = _uiState.value
        if (!state.isSelecting || state.isAssigningCategory || state.isRefreshing) return
        val rows = state.visibleTransactions.filter { it.isCategorisable }.map { it.key }.toSet()
        _uiState.update { it.copy(selectedRows = if (it.selectedRows == rows) emptySet() else rows) }
    }

    fun showBulkCategoryDialog() {
        if (_uiState.value.selectedRows.isEmpty() || _uiState.value.isRefreshing) return
        _uiState.update { it.copy(showBulkCategoryDialog = true, bulkCategoryError = null) }
    }

    fun dismissBulkCategoryDialog() {
        if (_uiState.value.isAssigningCategory) return
        // A swipe-right on one row isn't a selection the user made, so don't leave it ticked
        _uiState.update {
            it.copy(
                showBulkCategoryDialog = false,
                bulkCategoryError = null,
                selectedRows = if (it.isSelecting) it.selectedRows else emptySet()
            )
        }
    }

    fun assignSpendingCategory(tag: String?) {
        val state = _uiState.value
        if (state.isAssigningCategory || state.isRefreshing) return
        val selected = state.transactions.filter { it.key in state.selectedRows && it.isCategorisable }
        if (selected.isEmpty()) return
        _uiState.update { it.copy(isAssigningCategory = true, bulkCategoryError = null) }
        viewModelScope.launch {
            try {
                repository.assignSpendingCategory(selected, tag)
                _uiState.update { it.copy(
                    isSelecting = false, selectedRows = emptySet(), showBulkCategoryDialog = false,
                    message = "Updated spending category for ${transactionCount(selected.size)}."
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

    private fun transactionCount(n: Int) = if (n == 1) "1 transaction" else "$n transactions"

    fun preferLastAccountForAdding() = _uiState.update { it.copy(preferLastAccount = true) }

    fun showAddSheet() = _uiState.update {
        it.copy(showAddSheet = true, transactionToEdit = null, lastAddAccount = prefs.getLastAddAccount())
    }
    fun showEditSheet(transaction: Transaction) = _uiState.update { it.copy(transactionToEdit = transaction, showAddSheet = true) }
    fun dismissSheet() = _uiState.update { it.copy(showAddSheet = false, transactionToEdit = null) }

    /** Swipe left: delete straight away and offer Undo, which adds it back. */
    fun deleteWithUndo(transaction: Transaction) {
        _uiState.update { it.copy(pendingDeletes = it.pendingDeletes + transaction) }
        viewModelScope.launch {
            try {
                repository.deleteTransaction(transaction)
                _uiState.update { it.copy(undoable = transaction) }
            } catch (e: Exception) {
                _uiState.update { it.copy(pendingDeletes = it.pendingDeletes - transaction, error = e.message) }
            }
        }
    }

    /** Puts a deleted transaction back. It's added at the end of its account's sheet, with the
     *  same date, amount, category and spending category. */
    fun undoDelete(transaction: Transaction) {
        viewModelScope.launch {
            try {
                repository.addTransaction(transaction.copy(rowIndex = 0))
                _uiState.update { it.copy(message = "Restored \"${transaction.info}\"") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearUndo() = _uiState.update { it.copy(undoable = null) }

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
                    prefs.setLastAddAccount(transaction.account)
                    _uiState.update { it.copy(lastAddAccount = transaction.account) }

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
                prefs.setLastAddAccount(source.account)
                _uiState.update { it.copy(addSaveCount = it.addSaveCount + 1, lastAddAccount = source.account) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

private fun SimpleDateFormat.parseToEpoch(date: String): Long =
    runCatching { parse(date)?.time }.getOrNull() ?: 0L

/** Only saved one-off costs can have a spending category. */
val Transaction.isCategorisable: Boolean get() = category == TransactionCategory.ONE_OFF_COST && rowIndex > 1
