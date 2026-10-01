package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpendTagsTest {

    private fun tx(info: String, amount: Double, tag: String?, category: TransactionCategory = TransactionCategory.ONE_OFF_COST) =
        Transaction(rowIndex = 0, date = "01/10/2026", info = info, amount = amount, category = category, account = "A", tag = tag)

    @Test
    fun `blank tags are treated as none`() {
        assertNull(SpendTags.normalise("  "))
        assertEquals("Gym", SpendTags.normalise(" Gym "))
    }

    @Test
    fun `known tags add custom ones after the defaults`() {
        val tags = SpendTags.knownTags(listOf(tx("a", -1.0, "Pets"), tx("b", -1.0, "groceries"), tx("c", -1.0, "pets")))
        assertEquals(SpendTags.DEFAULTS + "Pets", tags)
    }

    @Test
    fun `suggestions use the latest tag for a description`() {
        val suggestions = SpendTags.suggestionsByInfo(
            listOf(tx("Tesco", -5.0, "Shopping"), tx(" tesco ", -8.0, "Groceries"), tx("Bus", -2.0, null))
        )
        assertEquals(mapOf("tesco" to "Groceries"), suggestions)
    }

    @Test
    fun `spend by tag groups one-offs and ignores other categories`() {
        val result = SpendTags.oneOffSpendByTag(
            listOf(
                tx("Tesco", -30.0, "Groceries"),
                tx("Aldi", -20.0, "groceries"),
                tx("Cinema", -12.0, "Fun"),
                tx("Misc", -5.0, null),
                tx("Rent", -900.0, null, TransactionCategory.FIXED_COST)
            )
        )
        assertEquals(listOf(TagSpend("Groceries", 50.0), TagSpend("Fun", 12.0), TagSpend(null, 5.0)), result)
    }
}
