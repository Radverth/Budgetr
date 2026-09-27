package com.budgetr.app.data.model

data class RecurringCostReview(
    val account: String,
    val info: String,
    val firstSeenDate: Long,
    val lastAmount: Double,
    val dismissedUntil: Long? = null
)
