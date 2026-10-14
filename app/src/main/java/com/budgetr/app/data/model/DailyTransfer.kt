package com.budgetr.app.data.model

/** Money moved between days of the daily spend target. Positive [amount] borrows from later
 *  days into [createdOn]; negative pays [createdOn]'s money forward to later days. Dates are
 *  dd/MM/yyyy. A null [targetDay] spreads the other side evenly over every day after
 *  [createdOn] in the period; otherwise it all comes from (or goes to) that one day. */
data class DailyTransfer(
    val id: Long = 0,
    val periodStart: String,
    val createdOn: String,
    val amount: Double,
    val targetDay: String? = null
)
