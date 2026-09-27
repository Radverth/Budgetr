package com.budgetr.app.data.local.entity

import androidx.room.Entity

/** Tracks how long a fixed-cost transaction has been seen, so Home can suggest reviewing
 *  subscriptions that haven't changed in a while. Keyed by account + description, since fixed
 *  costs are carried forward in place (same sheet row, date bumped) rather than re-appended
 *  each pay period. Device-local only. */
@Entity(tableName = "recurring_cost_reviews", primaryKeys = ["account", "info"])
data class RecurringCostReviewEntity(
    val account: String,
    val info: String,
    val firstSeenDate: Long,
    val lastAmount: Double,
    /** Epoch millis; review nudge is suppressed until after this date. Null means never dismissed. */
    val dismissedUntil: Long? = null
)
