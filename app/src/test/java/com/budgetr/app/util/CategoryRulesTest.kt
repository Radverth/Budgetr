package com.budgetr.app.util

import com.budgetr.app.data.model.CategoryRule
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryRulesTest {

    private val tesco = CategoryRule(1, "TESCO", "Groceries")
    private val petrol = CategoryRule(2, "tesco petrol", "Transport")
    private val rules = listOf(tesco, petrol)

    private fun cost(row: Int, info: String, tag: String? = null, pending: Boolean = false, category: TransactionCategory = TransactionCategory.ONE_OFF_COST) =
        Transaction(rowIndex = row, date = "01/10/2026", info = info, amount = -10.0, category = category, account = "Current", tag = tag, pending = pending)

    @Test
    fun `matching ignores case and the longest pattern wins`() {
        assertEquals("Groceries", CategoryRules.tagFor("Tesco Express Leeds", rules))
        assertEquals("Transport", CategoryRules.tagFor("TESCO PETROL 1234", rules))
        assertNull(CategoryRules.tagFor("Sainsbury's", rules))
        assertNull(CategoryRules.tagFor("anything", listOf(CategoryRule(3, "  ", "Fun"))))
    }

    @Test
    fun `rules need a pattern, a category and no duplicate`() {
        assertNotNull(CategoryRules.problem(" ", "Groceries", rules))
        assertNotNull(CategoryRules.problem("a", "Groceries", rules))
        assertNotNull(CategoryRules.problem("Aldi", " ", rules))
        assertNotNull(CategoryRules.problem(" tesco ", "Fun", rules))
        assertNull(CategoryRules.problem("tesco", "Fun", rules, editingId = 1))
        assertNull(CategoryRules.problem("Aldi", "Groceries", rules))
    }

    @Test
    fun `applying to past costs only touches saved one-off costs this rule decides`() {
        val transactions = listOf(
            cost(2, "Tesco Express"),
            cost(3, "Tesco Metro", tag = "Fun"),
            cost(4, "Tesco", tag = "groceries"),
            cost(5, "Tesco Petrol"),
            cost(6, "Tesco", pending = true),
            cost(7, "Tesco Bank", category = TransactionCategory.FIXED_COST),
            cost(1, "Tesco header row")
        )
        assertEquals(listOf(2, 3), CategoryRules.pastMatches(tesco, rules, transactions).map { it.rowIndex })
        assertEquals(listOf(5), CategoryRules.pastMatches(petrol, rules, transactions).map { it.rowIndex })
    }

    @Test
    fun `suggestions come from descriptions always given the same category`() {
        val transactions = listOf(
            cost(2, "Greggs", "Eating out"),
            cost(3, "greggs ", "Eating out"),
            cost(4, "Greggs", "Eating out"),
            cost(5, "Boots", "Health"),
            cost(6, "Boots", "Shopping"),
            cost(7, "Netflix", "Fun"),
            cost(8, "Tesco", "Groceries"),
            cost(9, "Tesco", "Groceries"),
            cost(10, "Aldi", "Groceries"),
            cost(11, "Aldi", "Groceries")
        )
        val suggested = CategoryRules.suggestions(transactions, rules)
        assertEquals(listOf("Greggs", "Aldi"), suggested.map { it.pattern })
        assertEquals("Eating out", suggested.first().tag)
    }
}
