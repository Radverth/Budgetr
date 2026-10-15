package com.budgetr.app.data.repository

import com.budgetr.app.data.model.PendingChange
import com.budgetr.app.data.model.PendingKind
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingOverlayTest {

    private fun tx(row: Int, id: String?, info: String, amount: Double = -5.0, account: String = "Monzo") =
        Transaction(row, "01/10/2026", info, amount, TransactionCategory.ONE_OFF_COST, account, id = id)

    private val sheet = listOf(tx(2, "a", "Tesco"), tx(3, "b", "Bus"))

    @Test fun `queued add appears as pending with a temporary negative row`() {
        val result = PendingOverlay.apply(sheet, listOf(PendingChange(7, PendingKind.ADD, tx(0, "c", "Coffee"))))
        val added = result.last()
        assertEquals("Coffee", added.info)
        assertEquals(-7, added.rowIndex)
        assertTrue(added.pending)
        assertFalse(result.first().pending)
    }

    @Test fun `an add that already reached the Sheet isn't shown twice`() {
        val result = PendingOverlay.apply(sheet, listOf(PendingChange(1, PendingKind.ADD, tx(0, "b", "Bus"))))
        assertEquals(2, result.size)
    }

    @Test fun `queued edit replaces the row by ID and keeps its place`() {
        val result = PendingOverlay.apply(sheet, listOf(PendingChange(1, PendingKind.UPDATE, tx(99, "b", "Train", -8.0))))
        assertEquals("Train", result[1].info)
        assertEquals(3, result[1].rowIndex)
        assertTrue(result[1].pending)
    }

    @Test fun `queued delete hides the row`() {
        val result = PendingOverlay.apply(sheet, listOf(PendingChange(1, PendingKind.DELETE, tx(2, "a", "Tesco"))))
        assertEquals(listOf("Bus"), result.map { it.info })
    }

    @Test fun `changes apply in the order they were made`() {
        val result = PendingOverlay.apply(
            sheet,
            listOf(
                PendingChange(2, PendingKind.UPDATE, tx(0, "c", "Coffee and cake")),
                PendingChange(1, PendingKind.ADD, tx(0, "c", "Coffee"))
            )
        )
        assertEquals("Coffee and cake", result.last().info)
    }

    @Test fun `rows without an ID match on account and row number`() {
        val old = listOf(tx(2, null, "Tesco"), tx(2, null, "Rent", account = "Halifax"))
        val result = PendingOverlay.apply(old, listOf(PendingChange(1, PendingKind.DELETE, tx(2, null, "Tesco"))))
        assertEquals(listOf("Rent"), result.map { it.info })
    }
}
