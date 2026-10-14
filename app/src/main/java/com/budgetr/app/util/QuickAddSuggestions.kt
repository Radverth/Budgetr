package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Three recent descriptions, then frequent ones, with at most six chips in the form. */
object QuickAddSuggestions {
    fun from(transactions: List<Transaction>, accounts: List<String>): List<Transaction> {
        val format = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
        val newest = transactions
            .filter { it.account in accounts && it.info.isNotBlank() &&
                it.category != TransactionCategory.TRANSFER && it.category != TransactionCategory.UNKNOWN }
            .sortedWith(compareByDescending<Transaction> {
                runCatching { LocalDate.parse(it.date, format).toEpochDay() }.getOrDefault(Long.MIN_VALUE)
            }.thenByDescending { it.rowIndex })
        val groups = newest.groupBy { it.info.trim().lowercase(Locale.ROOT) }
        val recent = groups.values.map { it.first() }
        val frequent = groups.values.sortedByDescending { it.size }.map { it.first() }
        return (recent.take(3) + frequent).distinctBy { it.info.trim().lowercase(Locale.ROOT) }
            .take(6).map { it.copy(info = it.info.trim()) }
    }
}
