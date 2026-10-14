package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickAddSuggestionsTest {
    private fun tx(info: String, date: String, account: String = "Current", tag: String? = null) =
        Transaction(2, date, info, -5.0, TransactionCategory.ONE_OFF_COST, account, tag = tag)

    @Test fun `recent descriptions are distinct and carry the newest account and category`() {
        val old = tx(" coffee ", "30/09/2026")
        val latest = tx("Coffee", "01/10/2026", "Savings", "Eating out")
        val result = QuickAddSuggestions.from(listOf(old, latest), listOf("Current", "Savings"))
        assertEquals(listOf(latest), result)
    }

    @Test fun `frequent descriptions remain available alongside three recent ones`() {
        val frequent = (1..4).map { tx("Bus", "0$it/09/2026") }
        val recent = listOf(tx("Lunch", "14/10/2026"), tx("Book", "13/10/2026"), tx("Train", "12/10/2026"))
        assertEquals(listOf("Lunch", "Book", "Train", "Bus"),
            QuickAddSuggestions.from(frequent + recent, listOf("Current")).map { it.info })
    }

    @Test fun `blank descriptions deleted accounts and transfers are excluded`() {
        val rows = listOf(tx(" ", "01/10/2026"), tx("Old", "01/10/2026", "Deleted"),
            tx("Transfer", "01/10/2026").copy(category = TransactionCategory.TRANSFER))
        assertTrue(QuickAddSuggestions.from(rows, listOf("Current")).isEmpty())
    }
}
