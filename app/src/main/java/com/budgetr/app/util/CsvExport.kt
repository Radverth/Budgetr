package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import java.util.Locale

/** Turns transactions into a CSV file that opens cleanly in Excel, Numbers or Google Sheets. */
object CsvExport {
    val HEADER = listOf("Date", "Description", "Amount", "Type", "Spending category", "Account")

    fun build(transactions: List<Transaction>): String = buildString {
        appendRow(HEADER)
        transactions.forEach { tx ->
            appendRow(
                listOf(
                    tx.date,
                    tx.info,
                    String.format(Locale.ROOT, "%.2f", tx.amount),
                    tx.category.displayName,
                    tx.tag.orEmpty(),
                    tx.account
                )
            )
        }
    }

    /** RFC 4180: quote a field holding a comma, quote or line break, doubling inner quotes. A
     *  leading = + - or @ gets an apostrophe so spreadsheet apps don't run it as a formula. */
    fun escape(field: String): String {
        val safe = if (field.isNotEmpty() && field[0] in "=+@" ) "'$field" else field
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun StringBuilder.appendRow(fields: List<String>) {
        append(fields.joinToString(",") { escape(it) })
        append("\r\n")
    }
}
