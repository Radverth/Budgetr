package com.budgetr.app.util

import java.util.Date

/** One fixed cost typed in during first-run setup. */
data class SetupFixedCost(val name: String, val amountInput: String, val account: String)

data class SetupSummary(
    val income: Double,
    val fixedCosts: Double,
    /** Money for non-fixed spending this pay period, never below £0. */
    val pot: Double,
    val days: Int,
    val perDay: Double
)

/** Pure checks and sums behind the guided first-run setup. */
object SetupCalculator {

    /** Characters Google Sheets won't allow in a tab name. */
    private val forbidden = Regex("""[:\\/?*\[\]]""")

    /** Why [name] can't be an account (each one becomes a tab in the Sheet), or null if it's fine. */
    fun accountNameProblem(name: String, existing: List<String>): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Give the account a name"
            trimmed.length > 40 -> "Keep it under 40 characters"
            forbidden.containsMatchIn(trimmed) -> "Names can't contain : \\ / ? * [ or ]"
            trimmed.equals("Cover Sheet", ignoreCase = true) -> "That name is used by the Sheet itself"
            existing.any { it.equals(trimmed, ignoreCase = true) } -> "You already have an account called that"
            else -> null
        }
    }

    /** What setup will give: the pot after fixed costs, spread over the days of the current pay period. */
    fun summary(incomeInput: String, fixedCosts: List<SetupFixedCost>, payDay: Int, today: Date = Date()): SetupSummary {
        val income = PaydayPlanCalculator.parseAmount(incomeInput)?.coerceAtLeast(0.0) ?: 0.0
        val fixed = fixedCosts.sumOf { PaydayPlanCalculator.parseAmount(it.amountInput)?.coerceAtLeast(0.0) ?: 0.0 }
        val pot = (income - fixed).coerceAtLeast(0.0)
        val period = PayPeriodCalculator.current(payDay, today)
        val days = DailyTargetCalculator.dayIndex(period, period.nextPayday).coerceAtLeast(1)
        return SetupSummary(income = income, fixedCosts = fixed, pot = pot, days = days, perDay = pot / days)
    }

    /** Fixed costs worth saving: a name and an amount above £0. */
    fun validFixedCosts(fixedCosts: List<SetupFixedCost>): List<Pair<SetupFixedCost, Double>> = fixedCosts.mapNotNull { cost ->
        val amount = PaydayPlanCalculator.parseAmount(cost.amountInput) ?: return@mapNotNull null
        if (cost.name.isBlank() || amount <= 0) null else cost to amount
    }
}
