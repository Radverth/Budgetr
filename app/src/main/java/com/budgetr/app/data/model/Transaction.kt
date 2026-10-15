package com.budgetr.app.data.model

data class Transaction(
    val rowIndex: Int,          // 1-based row index in the sheet (for updates/deletes)
    val date: String,           // DD/MM/YYYY
    val info: String,
    val amount: Double,
    val category: TransactionCategory,
    val account: String,
    /** Months (1–12) this fixed cost is active in. Null means every month. */
    val activeMonths: List<Int>? = null,
    /** Optional spending category for one-off costs, e.g. "Groceries". Sheet column G. */
    val tag: String? = null,
    /** Hidden ID in Sheet column H, used to find this row even if the Sheet is re-ordered. */
    val id: String? = null,
    /** Saved on the phone but not yet in the Google Sheet. */
    val pending: Boolean = false
)
