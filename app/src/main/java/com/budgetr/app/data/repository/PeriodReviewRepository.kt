package com.budgetr.app.data.repository

import androidx.room.withTransaction
import com.budgetr.app.data.local.BudgetrDatabase
import com.budgetr.app.data.local.entity.PeriodReviewEntity
import com.budgetr.app.data.model.*
import com.budgetr.app.util.*
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class ReviewSnapshot(
    val review: PeriodReview,
    val balances: Map<String, Double>,
    val carry: Map<String, Double>,
    val complete: Boolean = false,
    val income: Double = 0.0
)

@Singleton
class PeriodReviewRepository @Inject constructor(private val db: BudgetrDatabase, private val prefs: PreferencesManager) {
    /** Serializes payday and leftover decisions with new transaction writes. */
    val operationMutex = Mutex()
    private val dao get() = db.periodReviewDao()
    private val moshi = Moshi.Builder().build()
    private val planAdapter = moshi.adapter(ReviewPlan::class.java)
    private val snapshotAdapter = moshi.adapter(ReviewSnapshot::class.java)
    private fun sheet() = requireNotNull(prefs.getSpreadsheetId()) { "Choose a spreadsheet first" }

    suspend fun savePlan(plan: ReviewPlan) = db.withTransaction {
        val id = sheet()
        val row = dao.get(id, plan.periodStart) ?: PeriodReviewEntity(id, plan.periodStart)
        if (row.snapshotJson == null) dao.put(row.copy(planJson = planAdapter.toJson(plan)))
    }

    suspend fun captureBeforeRollover(period: PayPeriod, transactions: List<Transaction>, balances: Map<String, Double>): ReviewSnapshot = db.withTransaction {
        val id = sheet()
        val start = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.UK).format(period.start)
        val row = dao.get(id, start) ?: PeriodReviewEntity(id, start)
        row.snapshotJson?.let { return@withTransaction snapshotAdapter.fromJson(it)!!.copy(complete = row.complete) }
        val envelopes = db.envelopeDao().getAllSync().map { ReviewEnvelope(it.tag, it.limitAmount, it.carriedOver, it.rollover) }
        // A multi-month catch-up has no single monthly plan to compare with.
        val savedPlan = row.planJson?.let { planAdapter.fromJson(it) }
            ?.takeIf { DailyTargetCalculator.dayIndex(period, period.nextPayday) <= 35 }
        val plan = savedPlan?.copy(envelopes = envelopes, countBudgeted = prefs.isDailyTargetCountingBudgeted())
        val transfers = db.dailyTransferDao().getForPeriodSync(start).map { DailyTransfer(it.id, it.periodStart, it.createdOn, it.amount, it.targetDay) }
        val carry = envelopes.associate { it.tag to EnvelopeCalculator.nextCarry(it.envelope(), EnvelopeCalculator.spent(transactions, it.tag, period)) }
        val summary = HistoryCalculator.summarise(transactions, period, balances.values.sum())
        val review = PeriodReviewCalculator.calculate(period, transactions, plan, transfers, balances.values.sum())
            .copy(reservedCarryPence = PeriodReviewCalculator.pence(carry.values.sum()))
        val snapshot = ReviewSnapshot(review, balances, carry, income = summary.income)
        dao.put(row.copy(snapshotJson = snapshotAdapter.toJson(snapshot)))
        snapshot
    }

    suspend fun completeRollover(start: String) = db.withTransaction {
        val row = dao.get(sheet(), start) ?: error("Review snapshot missing")
        dao.put(row.copy(complete = true))
    }

    suspend fun get(start: String): ReviewSnapshot? = dao.get(sheet(), start)?.let { row ->
        row.snapshotJson?.let { snapshotAdapter.fromJson(it)?.copy(complete = row.complete) }
    }

    fun observeReviews(): Flow<List<ReviewSnapshot>> = dao.observe(sheet()).map { rows ->
        rows.filter { it.spreadsheetId == prefs.getSpreadsheetId() && it.complete }.mapNotNull { row ->
            row.snapshotJson?.let { snapshotAdapter.fromJson(it)?.copy(complete = true) }
        }.sortedByDescending { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.UK).parse(it.review.periodStart)?.time ?: 0 }
    }
}
