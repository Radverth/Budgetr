package com.budgetr.app.data.model

data class SavingsGoal(
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double,
    /** dd/MM/yyyy. Null means no deadline. */
    val targetDate: String?,
    val rowIndex: Int = 0
)
