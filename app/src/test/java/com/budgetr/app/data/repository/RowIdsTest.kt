package com.budgetr.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RowIdsTest {

    private val column = listOf("ID", "a", "b", "c")

    @Test fun `the expected row is used when it still holds the ID`() {
        assertEquals(3, RowIds.resolve(column, "b", expectedRow = 3))
    }

    @Test fun `a row that moved is found by its ID`() {
        // Someone sorted the Sheet: "b" is now on row 4
        assertEquals(4, RowIds.resolve(listOf("ID", "a", "c", "b"), "b", expectedRow = 3))
    }

    @Test fun `a row that was removed is not found`() {
        assertNull(RowIds.resolve(listOf("ID", "a", "c"), "b", expectedRow = 3))
    }

    @Test fun `rows from before IDs existed trust the row number`() {
        assertEquals(5, RowIds.resolve(column, null, expectedRow = 5))
    }

    @Test fun `stray spaces around an ID don't matter`() {
        assertEquals(2, RowIds.resolve(listOf("ID", " a "), "a", expectedRow = 9))
    }

    @Test fun `rows missing an ID are listed, blank rows and the header are not`() {
        val rows = listOf(
            listOf("Date", "Info", "Amount", "Category", "RoundedAmount", "ActiveMonths", "Tag", "ID"),
            listOf("01/10/2026", "Tesco", "-5", "One Off Cost", "-5", "", "", "x1"),
            listOf("02/10/2026", "Bus", "-2", "One Off Cost"),
            emptyList(),
            listOf("03/10/2026", "Rent", "-900", "Fixed Cost", "-900", "", "", " ")
        )
        assertEquals(listOf(3, 5), RowIds.rowsMissingIds(rows))
    }

    @Test fun `new IDs are unique`() {
        assertNotEquals(RowIds.newId(), RowIds.newId())
    }
}
