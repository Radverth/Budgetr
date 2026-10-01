package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory

data class TagSpend(val tag: String?, val amount: Double)

/** Pure helpers for the optional spending category ("tag") on one-off costs, e.g. Groceries.
 *  Tags are free text stored in the Sheet, so any tag already used counts as known. */
object SpendTags {
    val DEFAULTS = listOf("Groceries", "Eating out", "Transport", "Shopping", "Bills", "Fun", "Health", "Other")

    /** Trims [raw] and treats blank as no tag. */
    fun normalise(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    /** The defaults followed by any other tags in use, ignoring case when de-duplicating. */
    fun knownTags(transactions: List<Transaction>): List<String> {
        val seen = DEFAULTS.map { it.lowercase() }.toMutableSet()
        val extra = transactions.mapNotNull { normalise(it.tag) }
            .filter { seen.add(it.lowercase()) }
            .sortedBy { it.lowercase() }
        return DEFAULTS + extra
    }

    /** Description (trimmed, lower case) → the tag last used for it, so the add form can
     *  pre-fill "Tesco" as Groceries. The newest row (highest sheet row) wins. */
    fun suggestionsByInfo(transactions: List<Transaction>): Map<String, String> = buildMap {
        // The cache returns newest first, so sort oldest first and let later rows overwrite
        transactions.sortedBy { it.rowIndex }.forEach { tx ->
            val tag = normalise(tx.tag) ?: return@forEach
            val key = tx.info.trim().lowercase()
            if (key.isNotEmpty()) put(key, tag)
        }
    }

    /** One-off spend grouped by tag (ignoring case), largest first. Untagged spend is grouped
     *  under a null tag. Given a [period], only rows dated inside it count. */
    fun oneOffSpendByTag(transactions: List<Transaction>, period: PayPeriod? = null): List<TagSpend> = transactions
        .filter { it.category == TransactionCategory.ONE_OFF_COST && isDatedInPeriod(it, period) }
        .groupBy { normalise(it.tag)?.lowercase() }
        .map { (key, txs) ->
            val tag = key?.let { canonical(txs.firstNotNullOf { tx -> normalise(tx.tag) }) }
            TagSpend(tag, txs.sumOf { kotlin.math.abs(it.amount) })
        }
        .sortedByDescending { it.amount }

    /** A default's own spelling if [tag] matches one regardless of case, else [tag] as given. */
    private fun canonical(tag: String): String = DEFAULTS.firstOrNull { it.equals(tag, ignoreCase = true) } ?: tag
}
