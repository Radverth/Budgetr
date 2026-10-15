package com.budgetr.app.util

import com.budgetr.app.data.model.*

object PeriodReviewCalculator {
    fun calculate(period: PayPeriod, transactions: List<Transaction>, plan: ReviewPlan?, transfers: List<DailyTransfer>, endBalance: Double): PeriodReview =
        PeriodReview("", "", "", 0.0, 0.0, null, null, null, emptyList(), 0, 0)
    fun eligibleLeftover(savedBalance: Long, reservedCarry: Long, availableNow: Long): Long = 0
}
