package com.budgetr.app.ui.screens.transactions

/** An optional, one-time action attached to a new transaction: adjust a debt balance or a
 *  savings goal's saved amount by the transaction's amount. Applied once at save time — not
 *  persisted, so editing/deleting the transaction later doesn't reverse it (see AddEditTransactionSheet). */
data class TransactionLinkAction(
    val targetType: LinkTargetType,
    val targetName: String,
    val direction: LinkDirection
)

enum class LinkTargetType { DEBT, GOAL }

/** For a debt: INCREASE = took on more debt, DECREASE = made a payment.
 *  For a goal: INCREASE = contributed, DECREASE = withdrew. */
enum class LinkDirection { INCREASE, DECREASE }
