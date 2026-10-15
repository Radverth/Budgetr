package com.budgetr.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.api.DriveFile
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.util.PayPeriodCalculator
import com.budgetr.app.util.PreferencesManager
import com.budgetr.app.util.SetupCalculator
import com.budgetr.app.util.SetupFixedCost
import com.budgetr.app.util.SetupSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

enum class OnboardingStep { WELCOME, CHOOSE, PAYDAY, ACCOUNTS, INCOME, FIXED_COSTS, SUMMARY, LOADING, DONE }

/** The guided setup steps for a new sheet, in order. */
val SETUP_STEPS = listOf(
    OnboardingStep.PAYDAY, OnboardingStep.ACCOUNTS, OnboardingStep.INCOME, OnboardingStep.FIXED_COSTS, OnboardingStep.SUMMARY
)

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val isLoading: Boolean = false,
    val availableSheets: List<DriveFile> = emptyList(),
    val showSheetPicker: Boolean = false,
    val isLoadingSheets: Boolean = false,
    val templateName: String = "My Budgetr",
    val error: String? = null,
    val createdSheetName: String? = null,
    // Guided setup for a new sheet
    val payDay: Int = 26,
    val accounts: List<String> = listOf("Current account"),
    val newAccountInput: String = "",
    val accountError: String? = null,
    val incomeInput: String = "",
    val incomeAccount: String? = null,
    val fixedCosts: List<SetupFixedCost> = emptyList(),
    /** Daily amount the finished setup gives, shown on the last screen. */
    val dailyAmount: Double? = null,
    /** Set if the sheet was made but some details couldn't be saved into it. */
    val setupWarning: String? = null
) {
    val summary: SetupSummary get() = SetupCalculator.summary(incomeInput, fixedCosts, payDay)
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: SheetsRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun goToChoose() = _uiState.update { it.copy(step = OnboardingStep.CHOOSE) }

    fun setTemplateName(name: String) = _uiState.update { it.copy(templateName = name) }

    /** "Create a new template" now starts the guided setup; the sheet is made at the end. */
    fun createTemplate(onComplete: () -> Unit) = _uiState.update {
        it.copy(step = OnboardingStep.PAYDAY, payDay = prefs.getPayDay(), error = null)
    }

    fun goBack() = _uiState.update { state ->
        val index = SETUP_STEPS.indexOf(state.step)
        state.copy(step = if (index <= 0) OnboardingStep.CHOOSE else SETUP_STEPS[index - 1])
    }

    fun goNext() = _uiState.update { state ->
        val index = SETUP_STEPS.indexOf(state.step)
        if (index < 0 || index == SETUP_STEPS.lastIndex) state else state.copy(step = SETUP_STEPS[index + 1])
    }

    fun setPayDay(day: Int) = _uiState.update { it.copy(payDay = day.coerceIn(1, 31)) }

    fun setNewAccountInput(value: String) = _uiState.update { it.copy(newAccountInput = value, accountError = null) }

    fun addAccount(name: String = _uiState.value.newAccountInput) {
        val state = _uiState.value
        val problem = SetupCalculator.accountNameProblem(name, state.accounts)
        if (problem != null) {
            _uiState.update { it.copy(accountError = problem) }
            return
        }
        _uiState.update { it.copy(accounts = it.accounts + name.trim(), newAccountInput = "", accountError = null) }
    }

    /** At least one account is always kept; costs paid from a removed account move to the first one. */
    fun removeAccount(name: String) = _uiState.update { state ->
        if (state.accounts.size <= 1) return@update state
        val remaining = state.accounts - name
        state.copy(
            accounts = remaining,
            incomeAccount = state.incomeAccount.takeIf { it != name },
            fixedCosts = state.fixedCosts.map { if (it.account == name) it.copy(account = remaining.first()) else it }
        )
    }

    fun setIncome(value: String) = _uiState.update { it.copy(incomeInput = value) }

    fun setIncomeAccount(account: String) = _uiState.update { it.copy(incomeAccount = account) }

    fun addFixedCost(name: String = "") = _uiState.update { state ->
        val account = state.incomeAccount ?: state.accounts.first()
        state.copy(fixedCosts = state.fixedCosts + SetupFixedCost(name, "", account))
    }

    fun updateFixedCost(index: Int, cost: SetupFixedCost) = _uiState.update { state ->
        state.copy(fixedCosts = state.fixedCosts.mapIndexed { i, c -> if (i == index) cost else c })
    }

    fun removeFixedCost(index: Int) = _uiState.update { state ->
        state.copy(fixedCosts = state.fixedCosts.filterIndexed { i, _ -> i != index })
    }

    /**
     * Makes the sheet with the chosen accounts, then saves payday, the salary and fixed costs
     * (dated at the start of this pay period, as the app does after each payday) and sets this
     * period's daily spend target. If the sheet is made but a later save fails, setup still
     * finishes and says what to add by hand, rather than making a second sheet on retry.
     */
    fun finishSetup() {
        val state = _uiState.value
        val name = state.templateName.ifBlank { "My Budgetr" }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, step = OnboardingStep.LOADING) }
            val spreadsheetId = try {
                repository.createSpreadsheetTemplate(name, state.accounts)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, step = OnboardingStep.SUMMARY, error = "Couldn't create the sheet: ${e.message}")
                }
                return@launch
            }
            prefs.setSpreadsheetId(spreadsheetId)
            prefs.setSpreadsheetName(name)
            prefs.setPayDay(state.payDay)

            val periodStart = SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(PayPeriodCalculator.current(state.payDay).start)
            prefs.setLastPayPeriodStart(periodStart)
            val summary = state.summary
            val warning = try {
                if (summary.income > 0) {
                    repository.addTransaction(
                        Transaction(0, periodStart, "Salary", summary.income, TransactionCategory.SALARY, state.incomeAccount ?: state.accounts.first())
                    )
                }
                SetupCalculator.validFixedCosts(state.fixedCosts).forEach { (cost, amount) ->
                    repository.addTransaction(
                        Transaction(0, periodStart, cost.name.trim(), -amount, TransactionCategory.FIXED_COST, cost.account)
                    )
                }
                null
            } catch (e: Exception) {
                "Your sheet is ready, but some of your income or fixed costs didn't save. Add any missing ones in Transactions."
            }
            if (summary.income > 0) prefs.setDailyPot(periodStart, summary.pot)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    step = OnboardingStep.DONE,
                    createdSheetName = name,
                    dailyAmount = summary.perDay.takeIf { summary.income > 0 },
                    setupWarning = warning
                )
            }
        }
    }

    fun loadExistingSheets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSheets = true, showSheetPicker = true, error = null) }
            try {
                val sheets = repository.listSpreadsheets()
                _uiState.update { it.copy(availableSheets = sheets, isLoadingSheets = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingSheets = false,
                        showSheetPicker = false,
                        error = "Could not load sheets. Sign out and back in if this is your first time."
                    )
                }
            }
        }
    }

    fun selectExistingSheet(id: String, name: String, onComplete: () -> Unit) {
        prefs.setSpreadsheetId(id)
        prefs.setSpreadsheetName(name)
        _uiState.update { it.copy(showSheetPicker = false, step = OnboardingStep.DONE, createdSheetName = name) }
    }

    fun dismissSheetPicker() = _uiState.update { it.copy(showSheetPicker = false) }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
