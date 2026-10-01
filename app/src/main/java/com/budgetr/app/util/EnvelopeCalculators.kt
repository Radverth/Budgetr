package com.budgetr.app.util

import com.budgetr.app.data.model.Envelope
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory

data class EnvelopeStatus(
    val envelope: Envelope,
    val spent: Double,
    val daysLeft: Int
) {
    /** This period's limit plus anything carried over from last period. */
    val available: Double get() = envelope.limit + envelope.carriedOver
    val left: Double get() = available - spent
    val perDayLeft: Double get() = left.coerceAtLeast(0.0) / daysLeft.coerceAtLeast(1)
    val percentUsed: Float get() = BudgetCapCalculator.percentUsed(spent, available)
    val isOver: Boolean get() = BudgetCapCalculator.isOverLimit(spent, available)
    val isApproaching: Boolean get() = BudgetCapCalculator.isApproachingLimit(spent, available)
}

/** Pure calculations behind per-category budgets ("envelopes") for tagged one-off costs. */
object EnvelopeCalculator {

    /** One-off spend tagged [tag] (any case). Given a [period], only rows dated inside it count. */
    fun spent(transactions: List<Transaction>, tag: String, period: PayPeriod? = null): Double =
        spendForCategory(
            transactions.filter { it.tag.equals(tag, ignoreCase = true) },
            TransactionCategory.ONE_OFF_COST,
            period = period
        )

    fun status(envelope: Envelope, transactions: List<Transaction>, period: PayPeriod, daysLeft: Int) =
        EnvelopeStatus(envelope, spent(transactions, envelope.tag, period), daysLeft)

    /** What carries into next period: unspent money for rollover envelopes, never negative,
     *  so overspending one period doesn't shrink the next. */
    fun nextCarry(envelope: Envelope, spent: Double): Double =
        if (envelope.rollover) (envelope.limit + envelope.carriedOver - spent).coerceAtLeast(0.0) else 0.0
}
