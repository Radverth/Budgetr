package com.budgetr.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.BuildConfig
import com.budgetr.app.data.model.AccountBalance
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.data.repository.BudgetRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.AuthManager
import com.budgetr.app.util.BudgetCapCalculator
import com.budgetr.app.util.NoSpendStreakCalculator
import com.budgetr.app.util.PayPeriodCalculator
import com.budgetr.app.util.PreferencesManager
import com.budgetr.app.util.RecurringCostReviewCalculator
import com.budgetr.app.util.SafeToSpend
import com.budgetr.app.util.SafeToSpendCalculator
import com.budgetr.app.util.SavingsGoalCalculator
import com.budgetr.app.util.SpendingTrend
import com.budgetr.app.util.SpendingTrendCalculator
import com.budgetr.app.util.UpdateChecker
import com.budgetr.app.util.spendForCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class BudgetAlertUiItem(
    val category: TransactionCategory,
    val spend: Double,
    val limit: Double,
    val isOver: Boolean
)

data class RecurringReviewUiItem(
    val account: String,
    val info: String,
    val amount: Double,
    val ageDays: Int
)

data class GoalSuggestionUiItem(
    val goalName: String,
    val category: TransactionCategory,
    val underspendAmount: Double
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val accountBalances: List<AccountBalance> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalOutgoings: Double = 0.0,
    val totalFixedCosts: Double = 0.0,
    val totalOneOffCosts: Double = 0.0,
    val totalAvailable: Double = 0.0,
    val safeToSpend: SafeToSpend? = null,
    val error: String? = null,
    val successMessage: String? = null,
    val userName: String? = null,
    val updateAvailable: Boolean = false,
    val updateVersion: String = "",
    val updateUrl: String = "",
    val goalsCount: Int = 0,
    val avgGoalProgress: Float = 0f,
    val debtCount: Int = 0,
    val totalDebt: Double = 0.0,
    val budgetAlerts: List<BudgetAlertUiItem> = emptyList(),
    val noSpendStreakDays: Int? = null,
    val spendingTrend: SpendingTrend? = null,
    val recurringReviews: List<RecurringReviewUiItem> = emptyList(),
    val goalSuggestions: List<GoalSuggestionUiItem> = emptyList()
)

private data class SpendingControlsData(
    val alerts: List<BudgetAlertUiItem>,
    val streakDays: Int?,
    val trend: SpendingTrend?,
    val reviews: List<RecurringReviewUiItem>,
    val suggestions: List<GoalSuggestionUiItem>
)

private data class SummaryData(
    val balances: List<AccountBalance>,
    val totalAvailable: Double,
    val safeToSpend: SafeToSpend,
    val income: Double,
    val outgoings: Double,
    val fixedCosts: Double,
    val oneOffCosts: Double
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SheetsRepository,
    private val budgetRepository: BudgetRepository,
    private val authManager: AuthManager,
    private val updateChecker: UpdateChecker,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(userName = authManager.getUserName()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeData()
        observeGoalsAndDebts()
        observeSpendingControls()
        checkPayPeriod()
        refresh()
        checkForUpdate()
    }

    private fun observeSpendingControls() {
        viewModelScope.launch {
            val linksAndGoals = combine(
                budgetRepository.getGoalCategoryLinks(),
                repository.getSavingsGoals()
            ) { links, goals -> links to goals }

            combine(
                repository.getAllTransactions(),
                budgetRepository.getCategoryBudgets(),
                budgetRepository.getRecurringCostReviews(),
                linksAndGoals
            ) { allTx, budgets, reviews, (links, goals) ->
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

                val alerts = budgets.mapNotNull { budget ->
                    val spend = spendForCategory(allTx, budget.category, currentMonth)
                    val isOver = BudgetCapCalculator.isOverLimit(spend, budget.limit)
                    val isApproaching = BudgetCapCalculator.isApproachingLimit(spend, budget.limit)
                    if (isOver || isApproaching) BudgetAlertUiItem(budget.category, spend, budget.limit, isOver) else null
                }.sortedByDescending { it.isOver }

                val oneOffTx = allTx.filter { it.category == TransactionCategory.ONE_OFF_COST }
                val periodStart = prefs.getLastPayPeriodStart()?.let {
                    runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.UK).parse(it) }.getOrNull()
                }
                val streakDays = periodStart?.let {
                    NoSpendStreakCalculator.currentStreakDays(oneOffTx.map { tx -> tx.date }, it)
                }

                val trend = SpendingTrendCalculator.weekOverWeek(oneOffTx)

                val now = System.currentTimeMillis()
                val dueReviews = reviews
                    .filter { RecurringCostReviewCalculator.isDueForReview(it.firstSeenDate, it.dismissedUntil, now) }
                    .map {
                        RecurringReviewUiItem(
                            account = it.account,
                            info = it.info,
                            amount = it.lastAmount,
                            ageDays = ((now - it.firstSeenDate) / (24L * 60 * 60 * 1000)).toInt()
                        )
                    }
                    .sortedByDescending { it.ageDays }

                val goalsByName = goals.associateBy { it.name }
                val budgetsByCategory = budgets.associateBy { it.category }
                val suggestions = links.mapNotNull { link ->
                    goalsByName[link.goalName] ?: return@mapNotNull null
                    val budget = budgetsByCategory[link.category] ?: return@mapNotNull null
                    val spend = spendForCategory(allTx, link.category, currentMonth)
                    val underspend = budget.limit - spend
                    if (underspend > 1.0) GoalSuggestionUiItem(link.goalName, link.category, underspend) else null
                }

                SpendingControlsData(alerts, streakDays, trend, dueReviews, suggestions)
            }.collect { data ->
                _uiState.update {
                    it.copy(
                        budgetAlerts = data.alerts,
                        noSpendStreakDays = data.streakDays,
                        spendingTrend = data.trend,
                        recurringReviews = data.reviews,
                        goalSuggestions = data.suggestions
                    )
                }
            }
        }
    }

    fun dismissRecurringReview(item: RecurringReviewUiItem) {
        viewModelScope.launch {
            budgetRepository.dismissRecurringCostReview(item.account, item.info, RecurringCostReviewCalculator.DEFAULT_REVIEW_AGE_DAYS)
        }
    }

    fun addUnderspendToGoal(item: GoalSuggestionUiItem) {
        viewModelScope.launch {
            try {
                val goal = repository.getSavingsGoals().first().find { it.name == item.goalName } ?: return@launch
                repository.updateSavingsGoal(goal.copy(savedAmount = goal.savedAmount + item.underspendAmount))
                _uiState.update { it.copy(successMessage = "Added to \"${item.goalName}\"") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private suspend fun syncRecurringCostTracking() {
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        val activeFixedCosts = repository.getAllTransactions().first().filter {
            it.category == TransactionCategory.FIXED_COST &&
                (it.activeMonths == null || it.activeMonths.contains(currentMonth))
        }
        budgetRepository.syncRecurringCostTracking(activeFixedCosts)
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
                val daysLeft = PayPeriodCalculator.current(prefs.getPayDay(), today).daysUntilPayday(today)
                val safeToSpend = SafeToSpendCalculator.calculate(totalAvailable, daysLeft)
                SummaryData(adjustedBalances, totalAvailable, safeToSpend, income, outgoings, fixedCosts, oneOffCosts)
            }.collect { data ->
                _uiState.update {
                    it.copy(
                        accountBalances = data.balances,
                        totalIncome = data.income,
                        totalOutgoings = data.outgoings,
                        totalFixedCosts = data.fixedCosts,
                        totalOneOffCosts = data.oneOffCosts,
                        totalAvailable = data.totalAvailable,
                        safeToSpend = data.safeToSpend
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
                syncRecurringCostTracking()
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
