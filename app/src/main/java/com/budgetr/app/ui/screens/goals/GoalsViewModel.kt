package com.budgetr.app.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.model.Debt
import com.budgetr.app.data.model.SavingsGoal
import com.budgetr.app.data.repository.SheetsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalsUiState(
    val isRefreshing: Boolean = false,
    val goals: List<SavingsGoal> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
    // Goal dialog (shared by add/edit — editingGoal null means adding new)
    val showGoalDialog: Boolean = false,
    val editingGoal: SavingsGoal? = null,
    val goalNameInput: String = "",
    val goalTargetInput: String = "",
    val goalSavedInput: String = "",
    val goalDateInput: String = "",
    val isSavingGoal: Boolean = false,
    val goalToDelete: SavingsGoal? = null,
    // Debt dialog (shared by add/edit — editingDebt null means adding new)
    val showDebtDialog: Boolean = false,
    val editingDebt: Debt? = null,
    val debtNameInput: String = "",
    val debtBalanceInput: String = "",
    val debtAprInput: String = "",
    val debtMinPaymentInput: String = "",
    val isSavingDebt: Boolean = false,
    val debtToDelete: Debt? = null
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: SheetsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.getSavingsGoals(), repository.getDebts()) { goals, debts -> goals to debts }
                .collect { (goals, debts) ->
                    _uiState.update { it.copy(goals = goals, debts = debts) }
                }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                repository.refreshSavingsGoals()
                repository.refreshDebts()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // --- Savings goal dialog ---

    fun showAddGoalDialog() = _uiState.update {
        it.copy(showGoalDialog = true, editingGoal = null, goalNameInput = "", goalTargetInput = "", goalSavedInput = "", goalDateInput = "")
    }

    fun showEditGoalDialog(goal: SavingsGoal) = _uiState.update {
        it.copy(
            showGoalDialog = true,
            editingGoal = goal,
            goalNameInput = goal.name,
            goalTargetInput = goal.targetAmount.toString(),
            goalSavedInput = goal.savedAmount.toString(),
            goalDateInput = goal.targetDate ?: ""
        )
    }

    fun dismissGoalDialog() = _uiState.update { it.copy(showGoalDialog = false, editingGoal = null) }

    fun setGoalNameInput(value: String) = _uiState.update { it.copy(goalNameInput = value) }
    fun setGoalTargetInput(value: String) = _uiState.update { it.copy(goalTargetInput = value) }
    fun setGoalSavedInput(value: String) = _uiState.update { it.copy(goalSavedInput = value) }
    fun setGoalDateInput(value: String) = _uiState.update { it.copy(goalDateInput = value) }

    fun confirmGoalDialog() {
        val state = _uiState.value
        val name = state.goalNameInput.trim()
        val target = state.goalTargetInput.toDoubleOrNull() ?: return
        val saved = state.goalSavedInput.toDoubleOrNull() ?: 0.0
        val date = state.goalDateInput.trim().ifBlank { null }
        if (name.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingGoal = true) }
            try {
                val editing = state.editingGoal
                if (editing != null) {
                    repository.updateSavingsGoal(editing.copy(name = name, targetAmount = target, savedAmount = saved, targetDate = date))
                } else {
                    repository.addSavingsGoal(SavingsGoal(name = name, targetAmount = target, savedAmount = saved, targetDate = date))
                }
                _uiState.update {
                    it.copy(isSavingGoal = false, showGoalDialog = false, editingGoal = null, successMessage = "\"$name\" saved")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSavingGoal = false, error = e.message) }
            }
        }
    }

    fun showDeleteGoalDialog(goal: SavingsGoal) = _uiState.update { it.copy(goalToDelete = goal) }
    fun dismissDeleteGoalDialog() = _uiState.update { it.copy(goalToDelete = null) }

    fun confirmDeleteGoal() {
        val goal = _uiState.value.goalToDelete ?: return
        viewModelScope.launch {
            try {
                repository.deleteSavingsGoal(goal.name)
                _uiState.update { it.copy(goalToDelete = null, successMessage = "\"${goal.name}\" deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(goalToDelete = null, error = e.message) }
            }
        }
    }

    // --- Debt dialog ---

    fun showAddDebtDialog() = _uiState.update {
        it.copy(showDebtDialog = true, editingDebt = null, debtNameInput = "", debtBalanceInput = "", debtAprInput = "", debtMinPaymentInput = "")
    }

    fun showEditDebtDialog(debt: Debt) = _uiState.update {
        it.copy(
            showDebtDialog = true,
            editingDebt = debt,
            debtNameInput = debt.name,
            debtBalanceInput = debt.balance.toString(),
            debtAprInput = debt.aprPercent.toString(),
            debtMinPaymentInput = debt.minPayment.toString()
        )
    }

    fun dismissDebtDialog() = _uiState.update { it.copy(showDebtDialog = false, editingDebt = null) }

    fun setDebtNameInput(value: String) = _uiState.update { it.copy(debtNameInput = value) }
    fun setDebtBalanceInput(value: String) = _uiState.update { it.copy(debtBalanceInput = value) }
    fun setDebtAprInput(value: String) = _uiState.update { it.copy(debtAprInput = value) }
    fun setDebtMinPaymentInput(value: String) = _uiState.update { it.copy(debtMinPaymentInput = value) }

    fun confirmDebtDialog() {
        val state = _uiState.value
        val name = state.debtNameInput.trim()
        val balance = state.debtBalanceInput.toDoubleOrNull() ?: return
        val apr = state.debtAprInput.toDoubleOrNull() ?: 0.0
        val minPayment = state.debtMinPaymentInput.toDoubleOrNull() ?: return
        if (name.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingDebt = true) }
            try {
                val editing = state.editingDebt
                if (editing != null) {
                    repository.updateDebt(editing.copy(name = name, balance = balance, aprPercent = apr, minPayment = minPayment))
                } else {
                    repository.addDebt(Debt(name = name, balance = balance, aprPercent = apr, minPayment = minPayment))
                }
                _uiState.update {
                    it.copy(isSavingDebt = false, showDebtDialog = false, editingDebt = null, successMessage = "\"$name\" saved")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSavingDebt = false, error = e.message) }
            }
        }
    }

    fun showDeleteDebtDialog(debt: Debt) = _uiState.update { it.copy(debtToDelete = debt) }
    fun dismissDeleteDebtDialog() = _uiState.update { it.copy(debtToDelete = null) }

    fun confirmDeleteDebt() {
        val debt = _uiState.value.debtToDelete ?: return
        viewModelScope.launch {
            try {
                repository.deleteDebt(debt.name)
                _uiState.update { it.copy(debtToDelete = null, successMessage = "\"${debt.name}\" deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(debtToDelete = null, error = e.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSuccessMessage() = _uiState.update { it.copy(successMessage = null) }
}
