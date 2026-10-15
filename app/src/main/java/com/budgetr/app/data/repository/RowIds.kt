package com.budgetr.app.data.repository

import java.util.UUID

/** Thrown when a transaction's row can't be found in the Sheet any more (deleted or changed by hand). */
class RowMovedException : IllegalStateException(
    "This transaction was changed or removed in your Google Sheet. Pull down to refresh, then try again."
)

/**
 * Each transaction row carries a hidden ID in column H, so the app can find the right row even
 * after the Sheet is sorted or edited by hand. Row numbers are only a starting guess.
 */
object RowIds {
    const val HEADER = "ID"

    /** Zero-based index of the ID column (H). */
    const val COLUMN_INDEX = 7

    fun newId(): String = UUID.randomUUID().toString()

    /**
     * The Sheet row (1-based) that holds [id], given column H read top to bottom (index 0 = row 1).
     * The [expectedRow] is checked first since it's almost always right. Without an ID (rows from
     * before IDs existed) the expected row is trusted, as before. Null if the ID is nowhere.
     */
    fun resolve(idColumn: List<String>, id: String?, expectedRow: Int): Int? {
        if (id.isNullOrBlank()) return expectedRow
        if (idColumn.getOrNull(expectedRow - 1)?.trim() == id) return expectedRow
        val index = idColumn.indexOfFirst { it.trim() == id }
        return if (index >= 0) index + 1 else null
    }

    /** 1-based rows below the header that have data but no ID yet. */
    fun rowsMissingIds(rows: List<List<String>>): List<Int> = rows.drop(1).mapIndexedNotNull { index, row ->
        if (row.isNotEmpty() && row.getOrElse(COLUMN_INDEX) { "" }.isBlank()) index + 2 else null
    }
}
