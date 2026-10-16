package com.budgetr.app.ui.screens.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.model.Debt
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.DebtPlanner
import com.budgetr.app.util.PayoffPlan
import com.budgetr.app.util.PayoffStrategy
import com.budgetr.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

data class DebtPlannerUiState(
    val isLoading: Boolean = true,
    val debts: List<Debt> = emptyList(),
    val strategy: PayoffStrategy = PayoffStrategy.AVALANCHE,
    val extra: Double = 0.0,
    val sliderMax: Double = 200.0,
    val plan: PayoffPlan? = null,
    /** The same extra with the other strategy, to compare. */
    val otherPlan: PayoffPlan? = null,
    val minimumsOnly: PayoffPlan? = null
) {
    val totalOwed: Double get() = debts.sumOf { it.balance.coerceAtLeast(0.0) }
    val totalMinimums: Double get() = debts.filter { it.balance > 0 }.sumOf { it.minPayment }
}

@HiltViewModel
class DebtPlannerViewModel @Inject constructor(
    private val sheetsRepository: SheetsRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DebtPlannerUiState(
            strategy = prefs.getDebtStrategy()?.let { name -> PayoffStrategy.entries.firstOrNull { it.name == name } } ?: PayoffStrategy.AVALANCHE,
            extra = prefs.getDebtExtra()
        )
    )
    val uiState: StateFlow<DebtPlannerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sheetsRepository.getDebts().collect { debts ->
                val owed = debts.filter { it.balance > 0.005 }
                val minimums = owed.sumOf { it.minPayment }
                // Room to try up to three times the minimums, in £50 steps, but never less than £200
                val max = (ceil(minimums * 3 / 50) * 50).coerceIn(200.0, 2000.0)
                _uiState.update { recalc(it.copy(isLoading = false, debts = owed, sliderMax = max, extra = it.extra.coerceAtMost(max))) }
            }
        }
    }

    fun setStrategy(strategy: PayoffStrategy) {
        prefs.setDebtStrategy(strategy.name)
        _uiState.update { recalc(it.copy(strategy = strategy)) }
    }

    /** Live while dragging; rounded to £5. */
    fun setExtra(amount: Double) = _uiState.update { recalc(it.copy(extra = (Math.round(amount / 5) * 5).toDouble().coerceAtLeast(0.0))) }

    fun saveExtra() = prefs.setDebtExtra(_uiState.value.extra)

    private fun recalc(state: DebtPlannerUiState): DebtPlannerUiState {
        if (state.debts.isEmpty()) return state.copy(plan = null, otherPlan = null, minimumsOnly = null)
        val other = if (state.strategy == PayoffStrategy.AVALANCHE) PayoffStrategy.SNOWBALL else PayoffStrategy.AVALANCHE
        return state.copy(
            plan = DebtPlanner.plan(state.debts, state.extra, state.strategy),
            otherPlan = DebtPlanner.plan(state.debts, state.extra, other),
            minimumsOnly = DebtPlanner.minimumsOnly(state.debts)
        )
    }
}
