package com.budgetr.app.data.repository

import com.budgetr.app.data.api.ValueRange
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.util.SpendTags

/** Updates only column G; never rewrites amounts, dates or transaction types. */
internal fun bulkSpendingCategoryValues(transactions: List<Transaction>, tag: String?): List<ValueRange> {
    require(transactions.all { it.category == TransactionCategory.ONE_OFF_COST && it.rowIndex > 1 }) {
        "Only saved one-off costs can have spending categories assigned."
    }
    val value = SpendTags.normalise(tag) ?: ""
    return transactions.distinctBy { it.account to it.rowIndex }.groupBy { it.account }.flatMap { (account, rows) ->
        val sheet = "'${account.replace("'", "''")}'"
        listOf(ValueRange(range = "$sheet!G1", values = listOf(listOf("Tag")))) +
            rows.map { ValueRange(range = "$sheet!G${it.rowIndex}", values = listOf(listOf(value))) }
    }
}
