package com.budgetr.app.util

/** Pure calculations behind the savings goal and debt payoff screens. */
object SavingsGoalCalculator {

    /** Progress toward the goal, clamped to [0, 1]. Returns 0f if target is not positive. */
    fun progress(saved: Double, target: Double): Float {
        if (target <= 0) return 0f
        return (saved / target).toFloat().coerceIn(0f, 1f)
    }

    /**
     * How much to save per month to hit [target] by [monthsRemaining], given [saved] so far.
     * Returns null if there's nothing left to save or no time remaining to compute a rate.
     */
    fun suggestedMonthlyContribution(saved: Double, target: Double, monthsRemaining: Int): Double? {
        val remaining = target - saved
        if (remaining <= 0 || monthsRemaining <= 0) return null
        return remaining / monthsRemaining
    }
}

object DebtPayoffCalculator {

    private const val MAX_MONTHS = 1200 // 100 years — a sane cap against pathological inputs

    /**
     * Months to pay off [balance] at [monthlyPayment] with a fixed [aprPercent] annual rate,
     * compounding monthly. Returns null if the payment doesn't cover a month's interest (the
     * balance would never shrink) or if there's nothing owed.
     */
    fun monthsToPayOff(balance: Double, aprPercent: Double, monthlyPayment: Double): Int? {
        if (balance <= 0) return 0
        if (monthlyPayment <= 0) return null
        val monthlyRate = aprPercent / 100.0 / 12.0

        if (monthlyPayment <= balance * monthlyRate) return null

        var remaining = balance
        var months = 0
        while (remaining > 0 && months < MAX_MONTHS) {
            val interest = remaining * monthlyRate
            remaining = remaining + interest - monthlyPayment
            months++
        }
        return if (remaining > 0) null else months
    }

    /** Total interest paid over the payoff period computed by [monthsToPayOff], or null if it never pays off. */
    fun totalInterestPaid(balance: Double, aprPercent: Double, monthlyPayment: Double): Double? {
        val months = monthsToPayOff(balance, aprPercent, monthlyPayment) ?: return null
        if (months == 0) return 0.0
        return (monthlyPayment * months) - balance
    }
}
