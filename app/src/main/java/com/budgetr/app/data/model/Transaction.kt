package com.budgetr.app.data.model

data class Transaction(
    val rowIndex: Int,          // 1-based row index in the sheet (for updates/deletes)
    val date: String,           // DD/MM/YYYY
    val info: String,
    val amount: Double,
    val category: TransactionCategory,
    val account: String,
    /** Months (1–12) this fixed cost is active in. Null means every month. */
    val activeMonths: List<Int>? = null
)
