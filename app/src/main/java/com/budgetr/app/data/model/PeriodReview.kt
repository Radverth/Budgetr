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
