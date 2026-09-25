package com.budgetr.app.data.model

data class Debt(
    val name: String,
    val balance: Double,
    val aprPercent: Double,
    val minPayment: Double,
    val rowIndex: Int = 0
)
