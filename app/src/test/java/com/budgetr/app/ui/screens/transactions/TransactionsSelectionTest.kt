package com.budgetr.app.ui.screens.transactions

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionsSelectionTest {
    private val first = Transaction(2, "01/10/2026", "Shop", -12.0, TransactionCategory.ONE_OFF_COST, "Current")
    private val second = first.copy(rowIndex = 3, info = "Bus", amount = -3.0)
    private val state = TransactionsUiState(selectedAccount = "Current", transactions = listOf(first, second),
        isSelecting = true, selectedRows = setOf(2, 3))

    @Test fun `selection never moves to a different transaction occupying the same sheet row`() {
        val refreshed = state.withTransactions(listOf(second.copy(rowIndex = 2)))
        assertTrue(refreshed.selectedRows.isEmpty())
    }

    @Test fun `sorting keeps the selected transactions`() {
        assertEquals(setOf(2, 3), state.withTransactions(listOf(second, first)).selectedRows)
    }

    @Test fun `removed or changed rows leave selection but unchanged rows remain`() {
        assertEquals(setOf(2), state.withTransactions(listOf(first, second.copy(amount = -9.0))).selectedRows)
        assertEquals(setOf(2), state.withTransactions(listOf(first)).selectedRows)
    }

    @Test fun `selecting current account preserves cached rows before an offline refresh`() {
        assertEquals(state.transactions, state.withAccount("Current").transactions)
    }

    @Test fun `changing accounts clears old rows and selections`() {
        val changed = state.withAccount("Savings")
        assertTrue(changed.transactions.isEmpty())
        assertTrue(changed.selectedRows.isEmpty())
        assertEquals(false, changed.isSelecting)
    }
}
