package com.budgetr.app.util

import com.budgetr.app.data.model.CategoryRule
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory

/** Pure helpers behind category rules: matching, checks, applying to past costs, and suggestions. */
object CategoryRules {

    /** Why a rule can't be saved, or null if it's fine. [editingId] is the rule being changed, if any. */
    fun problem(pattern: String, tag: String, existing: List<CategoryRule>, editingId: Long? = null): String? {
        val trimmed = pattern.trim()
        return when {
            trimmed.isEmpty() -> "Type a word from the description, e.g. TESCO"
            trimmed.length < 2 -> "Use at least 2 letters"
            SpendTags.normalise(tag) == null -> "Choose a spending category"
            existing.any { it.id != editingId && it.pattern.trim().equals(trimmed, ignoreCase = true) } ->
                "You already have a rule for \"$trimmed\""
            else -> null
        }
    }

    fun matches(info: String, rule: CategoryRule): Boolean {
        val pattern = rule.pattern.trim()
        return pattern.isNotEmpty() && info.contains(pattern, ignoreCase = true)
    }

    /** The rule that decides [info]'s category: the longest matching pattern, so
     *  "TESCO PETROL" beats "TESCO". Null when no rule matches. */
    fun winner(info: String, rules: List<CategoryRule>): CategoryRule? =
        rules.filter { matches(info, it) }.maxByOrNull { it.pattern.trim().length }

    fun tagFor(info: String, rules: List<CategoryRule>): String? = winner(info, rules)?.tag

    /** Saved one-off costs that [rule] decides and that don't already have its category. */
    fun pastMatches(rule: CategoryRule, rules: List<CategoryRule>, transactions: List<Transaction>): List<Transaction> =
        transactions.filter { tx ->
            tx.category == TransactionCategory.ONE_OFF_COST && tx.rowIndex > 1 && !tx.pending &&
                winner(tx.info, rules)?.let { it.id == rule.id && it.pattern == rule.pattern } == true &&
                !SpendTags.normalise(tx.tag).equals(rule.tag.trim(), ignoreCase = true)
        }

    /**
     * Rules worth offering, from descriptions already given the same category at least twice
     * and never a different one. Descriptions a rule already covers are left out.
     */
    fun suggestions(transactions: List<Transaction>, rules: List<CategoryRule>, limit: Int = 5): List<CategoryRule> =
        transactions
            .filter { it.category == TransactionCategory.ONE_OFF_COST && it.info.isNotBlank() && SpendTags.normalise(it.tag) != null }
            .groupBy { it.info.trim().lowercase() }
            .values
            .mapNotNull { rows ->
                val tags = rows.mapNotNull { SpendTags.normalise(it.tag) }
                if (rows.size < 2 || tags.distinctBy { it.lowercase() }.size != 1) return@mapNotNull null
                val newest = rows.maxBy { it.rowIndex }
                if (winner(newest.info, rules) != null) return@mapNotNull null
                rows.size to CategoryRule(pattern = newest.info.trim(), tag = SpendTags.normalise(newest.tag)!!)
            }
            .sortedByDescending { it.first }
            .take(limit)
            .map { it.second }
}
