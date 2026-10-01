package com.budgetr.app.ui.screens.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.data.repository.BudgetRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.EnvelopeCalculator
import com.budgetr.app.util.EnvelopeStatus
import com.budgetr.app.util.PayPeriod
import com.budgetr.app.util.PayPeriodCalculator
import com.budgetr.app.util.PreferencesManager
import com.budgetr.app.util.SpendTags
import com.budgetr.app.util.spendForCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/** Categories a spending cap can meaningfully apply to — income/transfer categories are excluded. */
val CAPPABLE_CATEGORIES = listOf(TransactionCategory.ONE_OFF_COST, TransactionCategory.FIXED_COST)

data class BudgetCapUiItem(
    val category: TransactionCategory,
    val limit: Double?,
    val spend: Double
)

data class BudgetsUiState(
    val isLoading: Boolean = true,
    val items: List<BudgetCapUiItem> = emptyList(),
    /** Next payday, when caps reset, and how many days away it is. */
    val resetDate: Date? = null,
    val resetDays: Int = 0,
    val envelopes: List<EnvelopeStatus> = emptyList(),
    val knownTags: List<String> = SpendTags.DEFAULTS,
    val envelopeDialog: EnvelopeDialogState? = null,
    val showDialog: Boolean = false,
    val editingCategory: TransactionCategory? = null,
    val limitInput: String = "",
    val error: String? = null,
    val successMessage: String? = null
)

/** Add/edit envelope dialog. [originalTag] is null when adding a new one. */
data class EnvelopeDialogState(
    val originalTag: String? = null,
    val tag: String = "",
    val limitInput: String = "",
    val rollover: Boolean = false
)

private data class BudgetsData(
    val items: List<BudgetCapUiItem>,
    val envelopes: List<EnvelopeStatus>,
    val knownTags: List<String>,
    val period: PayPeriod
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val sheetsRepository: SheetsRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetsUiState())
    val uiState: StateFlow<BudgetsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                budgetRepository.getCategoryBudgets(),
                sheetsRepository.getAllTransactions(),
                budgetRepository.getEnvelopes()
            ) { budgets, transactions, envelopes ->
                val period = PayPeriodCalculator.current(prefs.getPayDay())
                val daysLeft = period.daysUntilPayday()
                val limitsByCategory = budgets.associate { it.category to it.limit }
                val items = CAPPABLE_CATEGORIES.map { category ->
                    BudgetCapUiItem(
                        category = category,
                        limit = limitsByCategory[category],
                        spend = spendForCategory(transactions, category, period = period)
                    )
                }
                BudgetsData(
                    items = items,
                    envelopes = envelopes.map { EnvelopeCalculator.status(it, transactions, period, daysLeft) },
                    knownTags = SpendTags.knownTags(transactions),
                    period = period
                )
            }.collect { data ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = data.items,
                        resetDate = data.period.nextPayday,
                        resetDays = data.period.daysUntilPayday(),
                        envelopes = data.envelopes,
                        knownTags = data.knownTags
                    )
                }
            }
        }
    }

    fun showEditDialog(category: TransactionCategory) {
        val current = _uiState.value.items.find { it.category == category }?.limit
        _uiState.update {
            it.copy(showDialog = true, editingCategory = category, limitInput = current?.toString() ?: "")
        }
    }

    fun setLimitInput(value: String) = _uiState.update { it.copy(limitInput = value) }

    fun dismissDialog() = _uiState.update { it.copy(showDialog = false, editingCategory = null) }

    fun confirmDialog() {
        val state = _uiState.value
        val category = state.editingCategory ?: return
        viewModelScope.launch {
            try {
                val limit = state.limitInput.toDoubleOrNull()
                if (limit == null || limit <= 0) {
                    budgetRepository.clearCategoryBudget(category)
                } else {
                    budgetRepository.setCategoryBudget(category, limit)
                }
                _uiState.update {
                    it.copy(showDialog = false, editingCategory = null, successMessage = "${category.displayName} budget updated")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearCap(category: TransactionCategory) {
        viewModelScope.launch {
            budgetRepository.clearCategoryBudget(category)
        }
    }

    fun showAddEnvelope() {
        val used = _uiState.value.envelopes.map { it.envelope.tag.lowercase() }.toSet()
        val firstFree = _uiState.value.knownTags.firstOrNull { it.lowercase() !in used } ?: ""
        _uiState.update { it.copy(envelopeDialog = EnvelopeDialogState(tag = firstFree)) }
    }

    fun showEditEnvelope(tag: String) {
        val envelope = _uiState.value.envelopes.find { it.envelope.tag == tag }?.envelope ?: return
        _uiState.update {
            it.copy(
                envelopeDialog = EnvelopeDialogState(
                    originalTag = envelope.tag,
                    tag = envelope.tag,
                    limitInput = envelope.limit.toString(),
                    rollover = envelope.rollover
                )
            )
        }
    }

    fun updateEnvelopeDialog(transform: (EnvelopeDialogState) -> EnvelopeDialogState) =
        _uiState.update { state -> state.copy(envelopeDialog = state.envelopeDialog?.let(transform)) }

    fun dismissEnvelopeDialog() = _uiState.update { it.copy(envelopeDialog = null) }

    fun confirmEnvelopeDialog() {
        val dialog = _uiState.value.envelopeDialog ?: return
        val tag = SpendTags.normalise(dialog.tag) ?: return
        val limit = dialog.limitInput.toDoubleOrNull()?.takeIf { it > 0 } ?: return
        viewModelScope.launch {
            try {
                if (dialog.originalTag != null && !dialog.originalTag.equals(tag, ignoreCase = true)) {
                    budgetRepository.deleteEnvelope(dialog.originalTag)
                }
                budgetRepository.setEnvelope(tag, limit, dialog.rollover)
                _uiState.update { it.copy(envelopeDialog = null, successMessage = "$tag budget saved") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteEnvelope(tag: String) {
        viewModelScope.launch {
            budgetRepository.deleteEnvelope(tag)
            _uiState.update { it.copy(envelopeDialog = null, successMessage = "$tag budget removed") }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSuccessMessage() = _uiState.update { it.copy(successMessage = null) }
}
