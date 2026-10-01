package com.budgetr.app.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.data.repository.BudgetRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.HistoryCalculator
import com.budgetr.app.util.PayPeriodCalculator
import com.budgetr.app.util.PreferencesManager
import com.budgetr.app.util.SpendTags
import com.budgetr.app.util.TagInsight
import com.budgetr.app.util.spendForCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** One bar in the spending chart. [isCurrent] marks the period still in progress. */
data class PeriodBar(
    val label: String,
    val rangeText: String,
    val oneOffSpend: Double,
    val isCurrent: Boolean
)

data class TagInsightUiItem(
    val insight: TagInsight,
    /** The category's current budget limit, if it has one. */
    val budgetLimit: Double?
)

data class InsightsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val bars: List<PeriodBar> = emptyList(),
    val historyCount: Int = 0,
    val averageOneOff: Double = 0.0,
    val averageLeftAtPayday: Double = 0.0,
    val tags: List<TagInsightUiItem> = emptyList(),
    val nextPayday: Date? = null,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val sheetsRepository: SheetsRepository,
    private val budgetRepository: BudgetRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sheetsRepository.getPeriodSummaries(),
                sheetsRepository.getAllTransactions(),
                budgetRepository.getEnvelopes()
            ) { summaries, transactions, envelopes ->
                val history = HistoryCalculator.latest(summaries, limit = 6)
                val period = PayPeriodCalculator.current(prefs.getPayDay())
                val parse = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
                val short = SimpleDateFormat("d MMM", Locale.UK)
                fun shortDate(text: String) = runCatching { short.format(parse.parse(text)!!) }.getOrDefault(text)

                val bars = history.map {
                    PeriodBar(
                        label = shortDate(it.periodStart),
                        rangeText = "${shortDate(it.periodStart)} – ${shortDate(it.periodEnd)}",
                        oneOffSpend = it.oneOffCosts,
                        isCurrent = false
                    )
                } + PeriodBar(
                    label = short.format(period.start),
                    rangeText = "${short.format(period.start)} – today",
                    oneOffSpend = spendForCategory(transactions, TransactionCategory.ONE_OFF_COST, period = period),
                    isCurrent = true
                )

                val limitsByTag = envelopes.associate { it.tag.lowercase() to it.limit }
                val tags = HistoryCalculator.tagInsights(history, SpendTags.oneOffSpendByTag(transactions, period))
                    .map { TagInsightUiItem(it, limitsByTag[it.tag.lowercase()]) }

                InsightsUiState(
                    isLoading = false,
                    bars = bars,
                    historyCount = history.size,
                    averageOneOff = history.map { it.oneOffCosts }.average().takeIf { !it.isNaN() } ?: 0.0,
                    averageLeftAtPayday = history.map { it.endBalance }.average().takeIf { !it.isNaN() } ?: 0.0,
                    tags = tags,
                    nextPayday = period.nextPayday
                )
            }.collect { computed ->
                _uiState.update {
                    computed.copy(isRefreshing = it.isRefreshing, error = it.error, successMessage = it.successMessage)
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                sheetsRepository.refreshPeriodSummaries()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** Creates a budget for [tag] at the suggested limit. Rollover stays off; it can be
     *  turned on from the Budgets screen. */
    fun applySuggestion(tag: String, limit: Double) {
        viewModelScope.launch {
            try {
                budgetRepository.setEnvelope(tag, limit, rollover = false)
                _uiState.update { it.copy(successMessage = "$tag budget set") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSuccessMessage() = _uiState.update { it.copy(successMessage = null) }
}
