package com.budgetr.app.util

import com.budgetr.app.data.model.Debt
import com.budgetr.app.data.model.Envelope
import com.budgetr.app.data.model.SavingsGoal
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class PaydayPlanCalculatorTest {

    private val today = SimpleDateFormat("dd/MM/yyyy", Locale.UK).parse("01/10/2026")!!

    private fun tx(amount: Double, category: TransactionCategory, activeMonths: List<Int>? = null) =
        Transaction(rowIndex = 0, date = "25/09/2026", info = "", amount = amount, category = category, account = "A", activeMonths = activeMonths)

    @Test
    fun `income includes salary and recurring income`() {
        val income = PaydayPlanCalculator.income(
            listOf(tx(2000.0, TransactionCategory.SALARY), tx(100.0, TransactionCategory.RECURRING_INCOME), tx(-50.0, TransactionCategory.ONE_OFF_COST))
        )
        assertEquals(2100.0, income, 0.001)
    }

    @Test
    fun `fixed costs follow active months`() {
        val fixed = PaydayPlanCalculator.fixedCosts(
            listOf(tx(-900.0, TransactionCategory.FIXED_COST), tx(-120.0, TransactionCategory.FIXED_COST, activeMonths = listOf(3))),
            today
        )
        assertEquals(900.0, fixed, 0.001)
    }

    @Test
    fun `goal contributions skip goals without a date or already met`() {
        val goals = listOf(
            SavingsGoal("Holiday", targetAmount = 1200.0, savedAmount = 200.0, targetDate = "01/02/2027"),
            SavingsGoal("Someday", targetAmount = 500.0, savedAmount = 0.0, targetDate = null),
            SavingsGoal("Done", targetAmount = 100.0, savedAmount = 100.0, targetDate = "01/12/2026")
        )
        // Oct → Feb is 4 months, £1,000 left
        assertEquals(250.0, PaydayPlanCalculator.goalContributions(goals, today), 0.001)
    }

    @Test
    fun `debt minimums ignore paid-off debts`() {
        val debts = listOf(Debt("Card", 500.0, 20.0, 25.0), Debt("Old loan", 0.0, 5.0, 100.0))
        assertEquals(25.0, PaydayPlanCalculator.debtMinimums(debts), 0.001)
    }

    @Test
    fun `amounts allow pound signs and commas but blank is not zero`() {
        assertEquals(1200.0, PaydayPlanCalculator.parseAmount(" £1,200 ")!!, 0.001)
        assertEquals(0.0, PaydayPlanCalculator.parseAmount("0")!!, 0.001)
        assertEquals(null, PaydayPlanCalculator.parseAmount(""))
        assertEquals(null, PaydayPlanCalculator.parseAmount("abc"))
    }

    @Test
    fun `left to assign subtracts everything`() {
        val totals = PaydayPlanTotals(income = 2000.0, fixedCosts = 900.0, goals = 250.0, debts = 50.0, assigned = 600.0)
        assertEquals(800.0, totals.toAssign, 0.001)
        assertEquals(200.0, totals.leftToAssign, 0.001)
    }

    @Test
    fun `initial lines keep budgets and add suggestions for the rest`() {
        val lines = PaydayPlanCalculator.initialLines(
            envelopes = listOf(Envelope("Groceries", 200.0)),
            insights = listOf(
                TagInsight("groceries", 150.0, 150.0, 0.0, 150.0),
                TagInsight("Fun", 38.0, 40.0, 0.0, 40.0),
                TagInsight("Pets", 0.0, null, 12.0, null)
            )
        )
        assertEquals(
            listOf(PlanLine("Groceries", 200.0, hadBudget = true), PlanLine("Fun", 40.0, hadBudget = false)),
            lines
        )
    }
}
