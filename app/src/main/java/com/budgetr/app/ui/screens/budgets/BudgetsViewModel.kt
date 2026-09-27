package com.budgetr.app.ui.screens.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.data.repository.BudgetRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.spendForCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val showDialog: Boolean = false,
    val editingCategory: TransactionCategory? = null,
    val limitInput: String = "",
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val sheetsRepository: SheetsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetsUiState())
    val uiState: StateFlow<BudgetsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                budgetRepository.getCategoryBudgets(),
                sheetsRepository.getAllTransactions()
            ) { budgets, transactions ->
                val limitsByCategory = budgets.associate { it.category to it.limit }
                CAPPABLE_CATEGORIES.map { category ->
                    BudgetCapUiItem(
                        category = category,
                        limit = limitsByCategory[category],
                        spend = spendForCategory(transactions, category)
                    )
                }
            }.collect { items ->
                _uiState.update { it.copy(isLoading = false, items = items) }
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

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSuccessMessage() = _uiState.update { it.copy(successMessage = null) }
}
