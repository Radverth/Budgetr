package com.budgetr.app.data.local.entity

import androidx.room.Entity

/** The day of the month a fixed cost actually leaves the bank, for the cash-flow forecast.
 *  Keyed by account + description (trimmed, lower case), since fixed costs keep their row and
 *  description from one pay period to the next. Device-local, saved in the phone backup. */
@Entity(tableName = "bill_due_days", primaryKeys = ["account", "infoKey"])
data class BillDueDayEntity(
    val account: String,
    val infoKey: String,
    val dueDay: Int
)
