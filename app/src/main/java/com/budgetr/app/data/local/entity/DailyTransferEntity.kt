package com.budgetr.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A borrow or pay-forward in the daily spend target. Device-local, like category budgets. */
@Entity(tableName = "daily_transfers")
data class DailyTransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodStart: String,
    val createdOn: String,
    val amount: Double,
    val targetDay: String?
)
