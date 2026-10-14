package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction

/** Recent and frequent descriptions available for the quick-add form. */
object QuickAddSuggestions {
    fun from(transactions: List<Transaction>, accounts: List<String>): List<Transaction> = emptyList()
}
