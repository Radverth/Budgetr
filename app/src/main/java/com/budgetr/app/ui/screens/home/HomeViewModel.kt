package com.budgetr.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.BuildConfig
import com.budgetr.app.data.model.AccountBalance
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.AuthManager
import com.budgetr.app.util.SavingsGoalCalculator
import com.budgetr.app.util.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val accountBalances: List<AccountBalance> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalOutgoings: Double = 0.0,
    val totalFixedCosts: Double = 0.0,
    val totalOneOffCosts: Double = 0.0,
    val totalAvailable: Double = 0.0,
    val error: String? = null,
    val successMessage: String? = null,
    val userName: String? = null,
    val updateAvailable: Boolean = false,
    val updateVersion: String = "",
    val updateUrl: String = "",
    val goalsCount: Int = 0,
    val avgGoalProgress: Float = 0f,
    val debtCount: Int = 0,
    val totalDebt: Double = 0.0
)

private data class SummaryData(
    val balances: List<AccountBalance>,
    val totalAvailable: Double,
    val income: Double,
    val outgoings: Double,
    val fixedCosts: Double,
    val oneOffCosts: Double
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SheetsRepository,
    private val authManager: AuthManager,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(userName = authManager.getUserName()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeData()
        observeGoalsAndDebts()
        checkPayPeriod()
        refresh()
        checkForUpdate()
    }

    /** Home is always the first screen shown, so the pay-period rollover check — which used to
     *  run only once the user visited the Accounts tab — lives here now. */
    private fun checkPayPeriod() {
        viewModelScope.launch {
            try {
                val wasReset = repository.checkAndProcessNewPayPeriod()
                if (wasReset) {
                    _uiState.update { it.copy(successMessage = "New pay period started — balances rolled over and one-off costs cleared") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Couldn't start the new pay period (${e.message ?: "unknown error"}). It will retry next time you open the app.") }
            }
        }
    }

    private fun observeGoalsAndDebts() {
        viewModelScope.launch {
            combine(repository.getSavingsGoals(), repository.getDebts()) { goals, debts -> goals to debts }
                .collect { (goals, debts) ->
                    val avgProgress = if (goals.isEmpty()) {
                        0f
                    } else {
                        goals.map { SavingsGoalCalculator.progress(it.savedAmount, it.targetAmount) }.average().toFloat()
                    }
                    _uiState.update {
                        it.copy(
                            goalsCount = goals.size,
                            avgGoalProgress = avgProgress,
                            debtCount = debts.size,
                            totalDebt = debts.sumOf { debt -> debt.balance }
                        )
                    }
                }
        }
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val result = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
            if (result.available) {
                _uiState.update { it.copy(updateAvailable = true, updateVersion = result.version, updateUrl = result.url) }
            }
        }
    }

    fun dismissUpdate() = _uiState.update { it.copy(updateAvailable = false) }

    private fun observeData() {
        viewModelScope.launch {
            val balancesAndRollovers = combine(
                repository.getAccountBalances(),
                repository.getBalanceRollovers()
            ) { balances, rollovers -> Pair(balances, rollovers) }

            val allTransactions = repository.getAllTransactions()

            combine(balancesAndRollovers, allTransactions) { (balances, rollovers), allTx ->
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.time
                val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)

                val futureRecurringByAccount = allTx
                    .filter {
                        it.category == TransactionCategory.RECURRING_INCOME &&
                        run {
                            val txDate = runCatching { dateFmt.parse(it.date) }.getOrNull()
                            txDate != null && txDate.after(today)
                        }
                    }
                    .groupBy { it.account }
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
                                val txDate = runCatching { dateFmt.parse(it.date) }.getOrNull()
                                txDate != null && !txDate.after(today)
                            }
                            else -> false
                        }
                    }
                    .sumOf { it.amount }
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                val fixedCosts = allTx
                    .filter {
                        it.category == TransactionCategory.FIXED_COST &&
                        (it.activeMonths == null || it.activeMonths.contains(currentMonth))
                    }
                    .sumOf { kotlin.math.abs(it.amount) }
                val oneOffCosts = allTx
                    .filter { it.category == TransactionCategory.ONE_OFF_COST }
                    .sumOf { kotlin.math.abs(it.amount) }
                val outgoings = allTx
                    .filter {
                        it.category != TransactionCategory.INCOME &&
                        it.category != TransactionCategory.SALARY &&
                        it.category != TransactionCategory.RECURRING_INCOME &&
                        it.category != TransactionCategory.TRANSFER &&
                        (it.activeMonths == null || it.activeMonths.contains(currentMonth))
                    }
                    .sumOf { kotlin.math.abs(it.amount) }
                val totalAvailable = adjustedBalances.sumOf { it.remainingBalance }
                SummaryData(adjustedBalances, totalAvailable, income, outgoings, fixedCosts, oneOffCosts)
            }.collect { data ->
                _uiState.update {
                    it.copy(
                        accountBalances = data.balances,
                        totalIncome = data.income,
                        totalOutgoings = data.outgoings,
                        totalFixedCosts = data.fixedCosts,
                        totalOneOffCosts = data.oneOffCosts,
                        totalAvailable = data.totalAvailable
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                repository.refreshAccountBalances()
                repository.refreshAllTransactions()
                repository.refreshSavingsGoals()
                repository.refreshDebts()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSuccessMessage() = _uiState.update { it.copy(successMessage = null) }
}
