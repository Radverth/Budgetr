package com.budgetr.app.util

import com.budgetr.app.data.model.Debt
import com.budgetr.app.data.model.Envelope
import com.budgetr.app.data.model.SavingsGoal
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import java.util.Calendar
import java.util.Date

/** One spending category in the payday plan. [hadBudget] means it already has an envelope. */
data class PlanLine(val tag: String, val amount: Double, val hadBudget: Boolean)

data class PaydayPlanTotals(
    val income: Double,
    val fixedCosts: Double,
    val goals: Double,
    val debts: Double,
    val assigned: Double
) {
    /** Money free for spending categories once bills, goals and debts are covered. */
    val toAssign: Double get() = income - fixedCosts - goals - debts
    /** Positive: still unassigned. Negative: more assigned than there is. */
    val leftToAssign: Double get() = toAssign - assigned
}

/** Pure calculations behind the payday plan ("give every pound a job"). */
object PaydayPlanCalculator {

    /** Income expected this pay period, including recurring income not yet received. */
    fun income(transactions: List<Transaction>): Double = transactions
        .filter {
            it.category == TransactionCategory.INCOME ||
                it.category == TransactionCategory.SALARY ||
                it.category == TransactionCategory.RECURRING_INCOME
        }
        .sumOf { it.amount }

    fun fixedCosts(transactions: List<Transaction>, today: Date = Date()): Double =
        spendForCategory(
            transactions,
            TransactionCategory.FIXED_COST,
            Calendar.getInstance().apply { time = today }.get(Calendar.MONTH) + 1
        )

    /** Monthly amount to stay on track for every goal that has a target date. */
    fun goalContributions(goals: List<SavingsGoal>, today: Date = Date()): Double = goals.sumOf { goal ->
        val months = goal.targetDate?.let { SavingsGoalCalculator.monthsUntil(it, today) } ?: return@sumOf 0.0
        SavingsGoalCalculator.suggestedMonthlyContribution(goal.savedAmount, goal.targetAmount, months) ?: 0.0
    }

    fun debtMinimums(debts: List<Debt>): Double = debts.filter { it.balance > 0 }.sumOf { it.minPayment }

    /** Existing budgets keep their limits. Categories with history but no budget are added
     *  at their suggested limit, so the plan starts close to how money is really spent. */
    fun initialLines(envelopes: List<Envelope>, insights: List<TagInsight>): List<PlanLine> {
        val budgeted = envelopes.map { PlanLine(it.tag, it.limit, hadBudget = true) }
        val budgetedTags = envelopes.map { it.tag.lowercase() }.toSet()
        val suggested = insights
            .filter { it.tag.lowercase() !in budgetedTags }
            .mapNotNull { insight -> insight.suggestedLimit?.let { PlanLine(insight.tag, it, hadBudget = false) } }
        return budgeted + suggested
    }
}
