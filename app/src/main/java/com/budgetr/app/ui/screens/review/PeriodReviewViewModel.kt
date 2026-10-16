package com.budgetr.app.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.model.PeriodReview
import com.budgetr.app.data.model.ReviewDestination
import com.budgetr.app.data.model.ReviewReceipt
import com.budgetr.app.data.repository.BudgetRepository
import com.budgetr.app.data.repository.PeriodReviewRepository
import com.budgetr.app.data.repository.ReviewAllocationRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PeriodReviewUiState(
    val isLoading: Boolean = true,
    /** Finished periods, newest first. */
    val reviews: List<PeriodReview> = emptyList(),
    val selectedStart: String? = null,
    val receipt: ReviewReceipt? = null,
    /** Leftover pennies that can still be given a job (0 once decided or for older periods). */
    val availablePence: Long = 0,
    val goals: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val selected: PeriodReview? get() = reviews.firstOrNull { it.periodStart == selectedStart } ?: reviews.firstOrNull()
    val isLatest: Boolean get() = selected != null && selected == reviews.firstOrNull()
}

@HiltViewModel
class PeriodReviewViewModel @Inject constructor(
    private val reviewRepository: PeriodReviewRepository,
    private val allocations: ReviewAllocationRepository,
    private val sheetsRepository: SheetsRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeriodReviewUiState())
    val uiState: StateFlow<PeriodReviewUiState> = _uiState.asStateFlow()
    private val selected = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            combine(
                reviewRepository.observeReviews(),
                reviewRepository.observeReceipts(),
                sheetsRepository.getSavingsGoals(),
                budgetRepository.getEnvelopes(),
                selected
            ) { reviews, receipts, goals, envelopes, chosen ->
                val review = reviews.firstOrNull { it.review.periodStart == chosen } ?: reviews.firstOrNull()
                val start = review?.review?.periodStart
                PeriodReviewUiState(
                    isLoading = false,
                    reviews = reviews.map { it.review },
                    selectedStart = start,
                    receipt = start?.let { receipts[it] },
                    availablePence = start?.let { runCatching { allocations.available(it) }.getOrDefault(0) } ?: 0,
                    goals = goals.map { it.name },
                    categories = envelopes.map { it.tag }
                )
            }.collect { state -> _uiState.update { state.copy(isSaving = it.isSaving, error = it.error) } }
        }
    }

    fun select(start: String) {
        selected.value = start
        _uiState.update { it.copy(error = null) }
    }

    fun allocate(destination: ReviewDestination, amountPence: Long) {
        val start = _uiState.value.selected?.periodStart ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                allocations.allocate(start, destination, amountPence)
            } catch (e: Exception) {
                // Our own checks explain themselves; anything else gets the usual plain-English message
                val message = if (e is IllegalStateException || e is IllegalArgumentException) e.message else UserMessages.from(e)
                _uiState.update { it.copy(error = message) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /** Finishes a choice whose Sheet write didn't confirm, using its original figures. */
    fun retry() {
        val receipt = _uiState.value.receipt ?: return
        allocate(receipt.destination, receipt.amountPence)
    }
}
