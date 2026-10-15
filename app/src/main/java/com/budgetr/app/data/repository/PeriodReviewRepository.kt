package com.budgetr.app.data.repository

import com.budgetr.app.data.local.BudgetrDatabase
import com.budgetr.app.data.model.*
import com.budgetr.app.util.*
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class ReviewSnapshot(val review: PeriodReview, val balances: Map<String, Double>, val carry: Map<String, Double>, val complete: Boolean = false)

@Singleton
class PeriodReviewRepository @Inject constructor(private val db: BudgetrDatabase, private val prefs: PreferencesManager) {
    suspend fun savePlan(plan: ReviewPlan) {}
    suspend fun captureBeforeRollover(period: PayPeriod, transactions: List<Transaction>, balances: Map<String, Double>): ReviewSnapshot =
        ReviewSnapshot(PeriodReviewCalculator.calculate(period, transactions, null, emptyList(), 0.0), emptyMap(), emptyMap())
    suspend fun completeRollover(start: String) {}
    suspend fun get(start: String): ReviewSnapshot? = null
    fun observeReviews(): Flow<List<ReviewSnapshot>> = flowOf(emptyList())
}
