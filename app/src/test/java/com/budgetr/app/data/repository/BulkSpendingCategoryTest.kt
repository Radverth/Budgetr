package com.budgetr.app.data.repository

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkSpendingCategoryTest {
    private val first = Transaction(2, "01/10/2026", "Shop", -12.34,
        TransactionCategory.ONE_OFF_COST, "Tom's account", tag = "Other")

    @Test fun `writes only selected category cells and header with escaped account names`() {
        val updates = bulkSpendingCategoryValues(listOf(first, first.copy(rowIndex = 8)), " Groceries ")
        assertEquals(listOf("'Tom''s account'!G1", "'Tom''s account'!G2", "'Tom''s account'!G8"), updates.map { it.range })
        assertEquals(listOf(listOf("Tag")), updates.first().values)
        assertTrue(updates.drop(1).all { it.values == listOf(listOf("Groceries")) })
    }

    @Test fun `duplicates are updated once and blank removes the category`() {
        val updates = bulkSpendingCategoryValues(listOf(first, first), "  ")
        assertEquals(2, updates.size)
        assertEquals(listOf(listOf("")), updates.last().values)
    }

    @Test fun `empty selection does not write anything`() {
        assertTrue(bulkSpendingCategoryValues(emptyList(), "Fun").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects income rather than converting it to spending`() {
        bulkSpendingCategoryValues(listOf(first.copy(category = TransactionCategory.INCOME)), "Fun")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects header and unsaved rows`() {
        bulkSpendingCategoryValues(listOf(first.copy(rowIndex = 1)), "Fun")
    }

    @Test fun `same row number in different accounts stays separate`() {
        val updates = bulkSpendingCategoryValues(listOf(first, first.copy(account = "Savings")), "=custom")
        assertEquals(4, updates.size)
        assertEquals(listOf(listOf("=custom")), updates.last().values)
    }
}
