package com.budgetr.app.data.model

enum class PendingKind { ADD, UPDATE, DELETE }

/** An add, edit or delete saved on the phone and waiting to be sent to the Google Sheet.
 *  [failedReason] is set when Google refused it, so it waits for Retry or Discard. */
data class PendingChange(
    val id: Long,
    val kind: PendingKind,
    val transaction: Transaction,
    val failedReason: String? = null
)
