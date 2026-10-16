package com.budgetr.app.util

import com.budgetr.app.data.model.Debt

enum class PayoffStrategy(val label: String, val explanation: String) {
    AVALANCHE("Highest interest first", "Pays the least interest overall"),
    SNOWBALL("Smallest balance first", "Clears whole debts sooner, for quick wins")
}

/** When a debt is cleared, [month] months from now (1 = this coming month). */
data class DebtPayoff(val name: String, val month: Int, val interest: Double)

data class PayoffPlan(
    /** Months until every debt is cleared, or null if it never happens (payments don't beat the interest). */
    val months: Int?,
    val totalInterest: Double,
    /** Total owed now (index 0) and at the end of each month after. */
    val balances: List<Double>,
    /** Debts in the order they're cleared. */
    val payoffs: List<DebtPayoff>
)

/**
 * Pure month-by-month debt payoff simulation. Interest is added monthly (APR / 12), every debt
 * gets its minimum, then the extra money goes to one target debt chosen by [PayoffStrategy].
 * When a debt is cleared its minimum joins the extra (the "rollover"), which is what makes
 * snowball and avalanche faster than paying minimums alone.
 */
object DebtPlanner {

    const val MAX_MONTHS = 600

    fun plan(debts: List<Debt>, extraPerMonth: Double, strategy: PayoffStrategy): PayoffPlan =
        simulate(debts, extraPerMonth.coerceAtLeast(0.0), strategy, rollover = true)

    /** Each debt paying only its own minimum, for comparison. */
    fun minimumsOnly(debts: List<Debt>): PayoffPlan = simulate(debts, 0.0, PayoffStrategy.AVALANCHE, rollover = false)

    private fun simulate(debts: List<Debt>, extra: Double, strategy: PayoffStrategy, rollover: Boolean): PayoffPlan {
        val owed = debts.filter { it.balance > 0.005 }
        val balance = owed.map { it.balance }.toDoubleArray()
        val interest = DoubleArray(owed.size)
        val budget = owed.sumOf { it.minPayment.coerceAtLeast(0.0) } + extra
        val balances = mutableListOf(balance.sum())
        val payoffs = mutableListOf<DebtPayoff>()
        var month = 0

        while (balance.any { it > 0.005 } && month < MAX_MONTHS) {
            month++
            owed.indices.filter { balance[it] > 0.005 }.forEach { i ->
                val charge = balance[i] * owed[i].aprPercent.coerceAtLeast(0.0) / 1200
                balance[i] += charge
                interest[i] += charge
            }
            var left = budget
            owed.indices.filter { balance[it] > 0.005 }.forEach { i ->
                val pay = minOf(owed[i].minPayment.coerceAtLeast(0.0), balance[i])
                balance[i] -= pay
                left -= pay
            }
            if (rollover) {
                val order = owed.indices.filter { balance[it] > 0.005 }.sortedWith(
                    when (strategy) {
                        PayoffStrategy.AVALANCHE -> compareByDescending<Int> { owed[it].aprPercent }.thenBy { balance[it] }
                        PayoffStrategy.SNOWBALL -> compareBy<Int> { balance[it] }.thenByDescending { owed[it].aprPercent }
                    }
                )
                for (i in order) {
                    if (left <= 0.005) break
                    val pay = minOf(left, balance[i])
                    balance[i] -= pay
                    left -= pay
                }
            }
            owed.indices.forEach { i ->
                if (balance[i] <= 0.005 && payoffs.none { it.name == owed[i].name }) {
                    balance[i] = 0.0
                    payoffs += DebtPayoff(owed[i].name, month, interest[i])
                }
            }
            balances += balance.sum()
        }
        val cleared = balance.all { it <= 0.005 }
        return PayoffPlan(if (cleared) month else null, interest.sum(), balances, payoffs)
    }
}
