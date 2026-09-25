package com.budgetr.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey val name: String,
    val balance: Double,
    val aprPercent: Double,
    val minPayment: Double,
    val rowIndex: Int
)
