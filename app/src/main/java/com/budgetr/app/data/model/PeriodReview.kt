package com.budgetr.app.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReviewPlan(val periodStart: String, val pot: Double?, val envelopes: List<ReviewEnvelope> = emptyList(), val countBudgeted: Boolean = true)

@JsonClass(generateAdapter = true)
data class ReviewEnvelope(val tag: String, val limit: Double, val carriedOver: Double, val rollover: Boolean) {
    fun envelope() = Envelope(tag, limit, rollover, carriedOver)
}

@JsonClass(generateAdapter = true)
data class ReviewCategory(val tag: String, val spent: Double, val planned: Double?) {
    val variance: Double? get() = planned?.minus(spent)
}

@JsonClass(generateAdapter = true)
data class PeriodReview(
    val periodStart: String,
    val periodEnd: String,
    val nextPeriodStart: String,
    val spending: Double,
    val fixedCosts: Double,
    val planned: Double?,
    val daysUnderTarget: Int?,
    val totalDays: Int?,
    val categories: List<ReviewCategory>,
    val endBalancePence: Long,
    val reservedCarryPence: Long
)

/** Where a review's leftover money goes. */
sealed class ReviewDestination {
    /** Leave it in the accounts' carry-over for the new period (no money moves). */
    data object NextPeriod : ReviewDestination()
    data class Goal(val name: String) : ReviewDestination()
    data class Category(val tag: String) : ReviewDestination()
}

/**
 * The one leftover decision for a review. Saved before any remote write (incomplete) so a lost
 * response can be finished later against the same [expectedGoalPence]/[desiredGoalPence],
 * never by adding the amount to the goal again.
 */
@JsonClass(generateAdapter = true)
data class ReviewReceipt(
    val operationId: String,
    /** "next", "goal" or "category". */
    val kind: String,
    val target: String? = null,
    val amountPence: Long = 0,
    val expectedGoalPence: Long? = null,
    val desiredGoalPence: Long? = null,
    val complete: Boolean = false
) {
    val destination: ReviewDestination
        get() = when (kind) {
            KIND_GOAL -> ReviewDestination.Goal(target.orEmpty())
            KIND_CATEGORY -> ReviewDestination.Category(target.orEmpty())
            else -> ReviewDestination.NextPeriod
        }

    fun matches(destination: ReviewDestination, amount: Long): Boolean =
        this.destination == destination && (destination == ReviewDestination.NextPeriod || amountPence == amount)

    companion object {
        const val KIND_NEXT = "next"
        const val KIND_GOAL = "goal"
        const val KIND_CATEGORY = "category"

        fun of(destination: ReviewDestination, amount: Long, operationId: String): ReviewReceipt = when (destination) {
            ReviewDestination.NextPeriod -> ReviewReceipt(operationId, KIND_NEXT)
            is ReviewDestination.Goal -> ReviewReceipt(operationId, KIND_GOAL, destination.name, amount)
            is ReviewDestination.Category -> ReviewReceipt(operationId, KIND_CATEGORY, destination.tag, amount)
        }
    }
}
