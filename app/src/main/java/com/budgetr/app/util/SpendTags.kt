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
     *  pre-fill "Tesco" as Groceries. Later rows in [transactions] win. */
    fun suggestionsByInfo(transactions: List<Transaction>): Map<String, String> = buildMap {
        transactions.forEach { tx ->
            val tag = normalise(tx.tag) ?: return@forEach
            val key = tx.info.trim().lowercase()
            if (key.isNotEmpty()) put(key, tag)
        }
    }

    /** One-off spend grouped by tag, largest first. Untagged spend is grouped under a null tag. */
    fun oneOffSpendByTag(transactions: List<Transaction>): List<TagSpend> = transactions
        .filter { it.category == TransactionCategory.ONE_OFF_COST }
        .groupBy { normalise(it.tag)?.let { tag -> canonical(tag) } }
        .map { (tag, txs) -> TagSpend(tag, txs.sumOf { kotlin.math.abs(it.amount) }) }
        .sortedByDescending { it.amount }

    /** Matches [tag] to a default regardless of case, so "groceries" and "Groceries" group together. */
    private fun canonical(tag: String): String = DEFAULTS.firstOrNull { it.equals(tag, ignoreCase = true) } ?: tag
}
