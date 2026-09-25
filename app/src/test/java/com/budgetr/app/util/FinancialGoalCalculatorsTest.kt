package com.budgetr.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavingsGoalCalculatorTest {

    @Test
    fun `progress is 0 when nothing saved`() {
        assertEquals(0f, SavingsGoalCalculator.progress(0.0, 500.0), 0.0001f)
    }

    @Test
    fun `progress is 1 when target reached`() {
        assertEquals(1f, SavingsGoalCalculator.progress(500.0, 500.0), 0.0001f)
    }

    @Test
    fun `progress is clamped at 1 when saved exceeds target`() {
        assertEquals(1f, SavingsGoalCalculator.progress(600.0, 500.0), 0.0001f)
    }

    @Test
    fun `progress is 0 for a non-positive target`() {
        assertEquals(0f, SavingsGoalCalculator.progress(100.0, 0.0), 0.0001f)
    }

    @Test
    fun `suggested monthly contribution splits the remainder evenly`() {
        val result = SavingsGoalCalculator.suggestedMonthlyContribution(saved = 200.0, target = 1400.0, monthsRemaining = 12)
        assertEquals(100.0, result)
    }

    @Test
    fun `suggested monthly contribution is null once the goal is met`() {
        assertNull(SavingsGoalCalculator.suggestedMonthlyContribution(saved = 500.0, target = 500.0, monthsRemaining = 6))
    }

    @Test
    fun `suggested monthly contribution is null with no time remaining`() {
        assertNull(SavingsGoalCalculator.suggestedMonthlyContribution(saved = 0.0, target = 500.0, monthsRemaining = 0))
    }
}

class DebtPayoffCalculatorTest {

    @Test
    fun `zero balance pays off immediately`() {
        assertEquals(0, DebtPayoffCalculator.monthsToPayOff(balance = 0.0, aprPercent = 20.0, monthlyPayment = 50.0))
    }

    @Test
    fun `zero-interest debt pays off in balance divided by payment, rounded up`() {
        // 500 at 0% APR, 100 a month => 5 exact months
        assertEquals(5, DebtPayoffCalculator.monthsToPayOff(balance = 500.0, aprPercent = 0.0, monthlyPayment = 100.0))
    }

    @Test
    fun `payment that does not cover monthly interest never pays it off`() {
        // 1000 balance at 24% APR accrues 20 a month in interest; a 15 payment can't keep up
        assertNull(DebtPayoffCalculator.monthsToPayOff(balance = 1000.0, aprPercent = 24.0, monthlyPayment = 15.0))
    }

    @Test
    fun `zero or negative payment never pays off a positive balance`() {
        assertNull(DebtPayoffCalculator.monthsToPayOff(balance = 100.0, aprPercent = 10.0, monthlyPayment = 0.0))
    }

    @Test
    fun `total interest paid is the difference between payments made and principal`() {
        val months = DebtPayoffCalculator.monthsToPayOff(balance = 500.0, aprPercent = 0.0, monthlyPayment = 100.0)!!
        val interest = DebtPayoffCalculator.totalInterestPaid(balance = 500.0, aprPercent = 0.0, monthlyPayment = 100.0)
        assertEquals(0.0, interest!!, 0.001)
        assertEquals(5, months)
    }
}
