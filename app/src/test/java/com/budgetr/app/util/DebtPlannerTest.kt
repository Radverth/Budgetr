package com.budgetr.app.util

import com.budgetr.app.data.model.Debt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtPlannerTest {

    // A small, cheap loan and a big, expensive card, so the two strategies disagree
    private val loan = Debt("Loan", balance = 500.0, aprPercent = 5.0, minPayment = 25.0)
    private val card = Debt("Card", balance = 2000.0, aprPercent = 20.0, minPayment = 50.0)

    @Test
    fun `one debt matches the existing payoff calculator`() {
        val debt = Debt("Card", 1000.0, 12.0, 100.0)
        val plan = DebtPlanner.plan(listOf(debt), 0.0, PayoffStrategy.AVALANCHE)
        assertEquals(DebtPayoffCalculator.monthsToPayOff(1000.0, 12.0, 100.0), plan.months)
        // Exact interest: the last month only pays what's left, so it's less than 11 × £100 − £1,000
        assertEquals(58.98, plan.totalInterest, 0.01)
        assertEquals(1000.0, plan.balances.first(), 0.001)
        assertEquals(0.0, plan.balances.last(), 0.001)
    }

    @Test
    fun `avalanche clears the dearest debt first and pays least interest`() {
        val avalanche = DebtPlanner.plan(listOf(loan, card), 100.0, PayoffStrategy.AVALANCHE)
        val snowball = DebtPlanner.plan(listOf(loan, card), 100.0, PayoffStrategy.SNOWBALL)
        assertEquals(listOf("Card", "Loan"), avalanche.payoffs.map { it.name })
        assertEquals(listOf("Loan", "Card"), snowball.payoffs.map { it.name })
        assertTrue(avalanche.totalInterest < snowball.totalInterest)
        assertTrue(snowball.payoffs.first().month < avalanche.payoffs.first().month)
    }

    @Test
    fun `extra money and rollover beat minimums alone`() {
        val baseline = DebtPlanner.minimumsOnly(listOf(loan, card))
        val plan = DebtPlanner.plan(listOf(loan, card), 100.0, PayoffStrategy.AVALANCHE)
        assertTrue(plan.months!! < baseline.months!!)
        assertTrue(plan.totalInterest < baseline.totalInterest)
        // Rollover alone (no extra) still finishes sooner than each debt on its own minimum
        assertTrue(DebtPlanner.plan(listOf(loan, card), 0.0, PayoffStrategy.AVALANCHE).months!! < baseline.months!!)
    }

    @Test
    fun `payments that don't beat the interest never clear the debt`() {
        val stuck = Debt("Store card", 5000.0, 30.0, 100.0) // £125 interest a month
        assertNull(DebtPlanner.minimumsOnly(listOf(stuck)).months)
        assertNull(DebtPlanner.plan(listOf(stuck), 20.0, PayoffStrategy.SNOWBALL).months)
        assertTrue(DebtPlanner.plan(listOf(stuck), 100.0, PayoffStrategy.SNOWBALL).months != null)
        // £125 interest against £100 paid: £30 is the first £5 step that shrinks it
        assertEquals(30.0, DebtPlanner.extraNeeded(listOf(stuck), PayoffStrategy.SNOWBALL)!!, 0.001)
    }

    @Test
    fun `nothing owed means nothing to plan`() {
        val plan = DebtPlanner.plan(listOf(Debt("Paid", 0.0, 10.0, 20.0)), 50.0, PayoffStrategy.AVALANCHE)
        assertEquals(0, plan.months)
        assertTrue(plan.payoffs.isEmpty())
    }
}
