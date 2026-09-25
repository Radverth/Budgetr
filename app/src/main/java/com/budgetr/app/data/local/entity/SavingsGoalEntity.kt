package com.budgetr.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey val name: String,
    val targetAmount: Double,
    val savedAmount: Double,
    /** dd/MM/yyyy. Null means no deadline. */
    val targetDate: String?,
    val rowIndex: Int
)
