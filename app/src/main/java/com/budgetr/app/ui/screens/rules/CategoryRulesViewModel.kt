package com.budgetr.app.ui.screens.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.model.CategoryRule
import com.budgetr.app.data.repository.CategoryRuleRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.CategoryRules
import com.budgetr.app.util.SpendTags
import com.budgetr.app.util.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One rule, with how many past costs it would change. */
data class RuleRow(val rule: CategoryRule, val pastCount: Int, val pastRetagged: Int)

/** The add / edit dialog. [id] is null when adding. */
data class RuleDialogState(
    val id: Long? = null,
    val pattern: String = "",
    val tag: String = "",
    val error: String? = null
)

data class CategoryRulesUiState(
    val isLoading: Boolean = true,
    val rules: List<RuleRow> = emptyList(),
    val suggestions: List<CategoryRule> = emptyList(),
    val knownTags: List<String> = SpendTags.DEFAULTS,
    val dialog: RuleDialogState? = null,
    /** The rule waiting for "apply to past costs" to be confirmed. */
    val confirmApply: RuleRow? = null,
    val isApplying: Boolean = false,
    val message: String? = null,
    /** The rule just deleted, so the snackbar can offer Undo. */
    val deleted: CategoryRule? = null
)

@HiltViewModel
class CategoryRulesViewModel @Inject constructor(
    private val ruleRepository: CategoryRuleRepository,
    private val sheetsRepository: SheetsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryRulesUiState())
    val uiState: StateFlow<CategoryRulesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(ruleRepository.getRules(), sheetsRepository.getAllTransactions()) { rules, transactions ->
                val rows = rules.map { rule ->
                    val past = CategoryRules.pastMatches(rule, rules, transactions)
                    RuleRow(rule, past.size, past.count { SpendTags.normalise(it.tag) != null })
                }
                Triple(rows, CategoryRules.suggestions(transactions, rules), SpendTags.knownTags(transactions))
            }.collect { (rows, suggestions, tags) ->
                _uiState.update { it.copy(isLoading = false, rules = rows, suggestions = suggestions, knownTags = tags) }
            }
        }
    }

    fun showAdd() = _uiState.update { it.copy(dialog = RuleDialogState()) }

    fun showEdit(rule: CategoryRule) = _uiState.update { it.copy(dialog = RuleDialogState(rule.id, rule.pattern, rule.tag)) }

    fun updateDialog(change: (RuleDialogState) -> RuleDialogState) =
        _uiState.update { state -> state.copy(dialog = state.dialog?.let(change)) }

    fun dismissDialog() = _uiState.update { it.copy(dialog = null) }

    fun confirmDialog() {
        val dialog = _uiState.value.dialog ?: return
        val problem = CategoryRules.problem(dialog.pattern, dialog.tag, _uiState.value.rules.map { it.rule }, dialog.id)
        if (problem != null) {
            updateDialog { it.copy(error = problem) }
            return
        }
        viewModelScope.launch {
            ruleRepository.save(CategoryRule(dialog.id ?: 0, dialog.pattern, dialog.tag))
            _uiState.update { it.copy(dialog = null, message = if (dialog.id == null) "Rule added" else "Rule saved") }
        }
    }

    fun addSuggestion(rule: CategoryRule) {
        viewModelScope.launch {
            ruleRepository.save(rule)
            _uiState.update { it.copy(message = "Rule added: \"${rule.pattern}\" → ${rule.tag}") }
        }
    }

    fun delete(rule: CategoryRule) {
        viewModelScope.launch {
            ruleRepository.delete(rule.id)
            _uiState.update { it.copy(dialog = null, deleted = rule, message = "Rule deleted") }
        }
    }

    fun undoDelete() {
        val rule = _uiState.value.deleted ?: return
        viewModelScope.launch {
            ruleRepository.save(rule)
            _uiState.update { it.copy(deleted = null) }
        }
    }

    fun askApply(row: RuleRow) = _uiState.update { it.copy(confirmApply = row) }

    fun dismissApply() {
        if (!_uiState.value.isApplying) _uiState.update { it.copy(confirmApply = null) }
    }

    /** Sets this rule's category on the past one-off costs it decides. */
    fun applyToPast() {
        val row = _uiState.value.confirmApply ?: return
        if (_uiState.value.isApplying) return
        _uiState.update { it.copy(isApplying = true) }
        viewModelScope.launch {
            try {
                val rules = _uiState.value.rules.map { it.rule }
                val matches = CategoryRules.pastMatches(row.rule, rules, sheetsRepository.getAllTransactions().first())
                sheetsRepository.assignSpendingCategory(matches, row.rule.tag)
                val count = if (matches.size == 1) "1 past cost" else "${matches.size} past costs"
                _uiState.update { it.copy(confirmApply = null, message = "Set $count to ${row.rule.tag}") }
            } catch (e: Exception) {
                _uiState.update { it.copy(confirmApply = null, message = UserMessages.from(e)) }
            } finally {
                _uiState.update { it.copy(isApplying = false) }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null, deleted = null) }
}
