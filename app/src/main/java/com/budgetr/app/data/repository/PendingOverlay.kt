package com.budgetr.app.data.repository

import com.budgetr.app.data.model.PendingChange
import com.budgetr.app.data.model.PendingKind
import com.budgetr.app.data.model.Transaction

/** Shows queued changes on top of the copy of the Sheet, so they appear straight away. */
object PendingOverlay {

    /** [sheet] with [changes] applied in the order they were made. Queued rows are marked
     *  pending; queued adds get a negative row number (minus the queue ID) until they're synced. */
    fun apply(sheet: List<Transaction>, changes: List<PendingChange>): List<Transaction> {
        val result = sheet.toMutableList()
        changes.sortedBy { it.id }.forEach { change ->
            val tx = change.transaction
            when (change.kind) {
                // Already in the Sheet (sent, but the queue entry wasn't cleared yet): don't show it twice
                PendingKind.ADD -> if (result.none { it.account == tx.account && it.id != null && it.id == tx.id }) {
                    result.add(tx.copy(rowIndex = -change.id.toInt(), pending = true))
                }
                PendingKind.UPDATE -> {
                    val index = result.indexOfFirst { matches(it, tx) }
                    if (index >= 0) result[index] = tx.copy(rowIndex = result[index].rowIndex, pending = true)
                }
                PendingKind.DELETE -> result.removeAll { matches(it, tx) }
            }
        }
        return result
    }

    /** Same row: by hidden ID when there is one, otherwise by account and row number. */
    private fun matches(row: Transaction, target: Transaction): Boolean =
        row.account == target.account && (if (target.id != null) row.id == target.id else row.rowIndex == target.rowIndex)
}
