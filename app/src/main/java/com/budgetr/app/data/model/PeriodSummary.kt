package com.budgetr.app.data.model

import com.budgetr.app.util.TagSpend

/** What happened in one finished pay period, saved to the Sheet's "History" tab at payday
 *  before that period's one-off costs are cleared. Dates are dd/MM/yyyy, end inclusive. */
data class PeriodSummary(
    val periodStart: String,
    val periodEnd: String,
    val income: Double,
    val fixedCosts: Double,
    val oneOffCosts: Double,
    val endBalance: Double,
    /** One-off spend per spending category; a null tag is untagged spend. */
    val byTag: List<TagSpend>
)
