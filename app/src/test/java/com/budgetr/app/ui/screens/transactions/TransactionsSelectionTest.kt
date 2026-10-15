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
        isSelecting = true, selectedRows = setOf(RowKey("Current", 2), RowKey("Current", 3)))

    @Test fun `selection never moves to a different transaction occupying the same sheet row`() {
        val refreshed = state.withTransactions(listOf(second.copy(rowIndex = 2)))
        assertTrue(refreshed.selectedRows.isEmpty())
    }

    @Test fun `sorting keeps the selected transactions`() {
        assertEquals(setOf(RowKey("Current", 2), RowKey("Current", 3)), state.withTransactions(listOf(second, first)).selectedRows)
    }

    @Test fun `removed or changed rows leave selection but unchanged rows remain`() {
        assertEquals(setOf(RowKey("Current", 2)), state.withTransactions(listOf(first, second.copy(amount = -9.0))).selectedRows)
        assertEquals(setOf(RowKey("Current", 2)), state.withTransactions(listOf(first)).selectedRows)
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

    @Test fun `same row number in another account is a different selection`() {
        val other = first.copy(account = "Savings")
        val all = state.copy(selectedAccount = ALL_ACCOUNTS, transactions = listOf(first, other), selectedRows = setOf(first.key))
        assertEquals(setOf(RowKey("Current", 2)), all.withTransactions(listOf(first, other)).selectedRows)
    }

    @Test fun `a pending delete is hidden until it has really gone`() {
        val deleting = state.copy(pendingDeletes = setOf(first))
        assertEquals(listOf(second), deleting.visibleTransactions)
        assertTrue(deleting.withTransactions(listOf(second)).pendingDeletes.isEmpty())
    }

    @Test fun `All tab adds to the first account`() {
        val all = state.copy(selectedAccount = ALL_ACCOUNTS, accounts = listOf("Current", "Savings"))
        assertEquals("Current", all.accountForAdding)
    }
    @Test fun `removed account falls back to an available account when adding`() {
        assertEquals("Savings", state.copy(accounts = listOf("Savings")).accountForAdding)
    }

    @Test fun `no accounts cannot start an add`() {
        assertEquals(null, state.copy(accounts = emptyList()).accountForAdding)
    }
    @Test fun `open account tab wins, All tab and quick add use the last saved account`() {
        val remembered = state.copy(accounts = listOf("Current", "Savings"), lastAddAccount = "Savings")
        assertEquals("Current", remembered.accountForAdding)
        assertEquals("Savings", remembered.copy(selectedAccount = ALL_ACCOUNTS).accountForAdding)
        assertEquals("Savings", remembered.copy(preferLastAccount = true).accountForAdding)
        assertEquals("Current", remembered.copy(accounts = listOf("Current"), preferLastAccount = true).accountForAdding)
    }
}
