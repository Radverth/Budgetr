package com.budgetr.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local cache of one row of the Sheet's "History" tab. */
@Entity(tableName = "period_summaries")
data class PeriodSummaryEntity(
    @PrimaryKey val periodStart: String,
    val periodEnd: String,
    val income: Double,
    val fixedCosts: Double,
    val oneOffCosts: Double,
    val endBalance: Double,
    /** Encoded as in the Sheet, e.g. "Groceries=120.50; Untagged=10.00". */
    val tagTotals: String
)
