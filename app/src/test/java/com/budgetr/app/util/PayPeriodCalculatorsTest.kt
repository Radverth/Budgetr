package com.budgetr.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
private fun date(s: String) = fmt.parse(s)!!

class PayPeriodCalculatorTest {

    @Test
    fun `weekday payday is unchanged`() {
        // 26 Oct 2026 is a Monday
        assertEquals("26/10/2026", fmt.format(PayPeriodCalculator.effectivePayday(2026, Calendar.OCTOBER, 26)))
    }

    @Test
    fun `weekend payday moves back to Friday`() {
        // 26 Sep 2026 is a Saturday, 26 Jul 2026 is a Sunday
        assertEquals("25/09/2026", fmt.format(PayPeriodCalculator.effectivePayday(2026, Calendar.SEPTEMBER, 26)))
        assertEquals("24/07/2026", fmt.format(PayPeriodCalculator.effectivePayday(2026, Calendar.JULY, 26)))
    }

    @Test
    fun `payday past month end uses the last day`() {
        // 30 Apr 2026 is a Thursday
        assertEquals("30/04/2026", fmt.format(PayPeriodCalculator.effectivePayday(2026, Calendar.APRIL, 31)))
    }

    @Test
    fun `before this month's payday the period started last month`() {
        val period = PayPeriodCalculator.current(26, date("01/10/2026"))
        assertEquals("25/09/2026", fmt.format(period.start))
        assertEquals("26/10/2026", fmt.format(period.nextPayday))
    }

    @Test
    fun `on payday a new period starts`() {
        val period = PayPeriodCalculator.current(26, date("26/10/2026"))
        assertEquals("26/10/2026", fmt.format(period.start))
        assertEquals("26/11/2026", fmt.format(period.nextPayday))
    }

    @Test
    fun `early Friday payday starts the period before the configured day`() {
        // Payday 26 Sep 2026 is a Saturday, so it's paid on Friday 25th
        val period = PayPeriodCalculator.current(26, date("25/09/2026"))
        assertEquals("25/09/2026", fmt.format(period.start))
    }

    @Test
    fun `payday on the 1st that moves into the previous month`() {
        // 1 Mar 2026 is a Sunday, so it's paid on Friday 27 Feb
        val period = PayPeriodCalculator.current(1, date("28/02/2026"))
        assertEquals("27/02/2026", fmt.format(period.start))
        assertEquals("01/04/2026", fmt.format(period.nextPayday))
    }

    @Test
    fun `days until payday counts today`() {
        val period = PayPeriodCalculator.current(26, date("01/10/2026"))
        assertEquals(25, period.daysUntilPayday(date("01/10/2026")))
        assertEquals(1, period.daysUntilPayday(date("25/10/2026")))
    }
}

class SafeToSpendCalculatorTest {

    @Test
    fun `spreads money evenly over the days left`() {
        val result = SafeToSpendCalculator.calculate(available = 250.0, daysLeft = 10)
        assertEquals(25.0, result.perDay, 0.001)
        assertEquals(175.0, result.thisWeek, 0.001)
        assertFalse(result.isOverspent)
    }

    @Test
    fun `week total is capped at the days left`() {
        val result = SafeToSpendCalculator.calculate(available = 90.0, daysLeft = 3)
        assertEquals(30.0, result.perDay, 0.001)
        assertEquals(90.0, result.thisWeek, 0.001)
    }

    @Test
    fun `zero days left is treated as one`() {
        val result = SafeToSpendCalculator.calculate(available = 40.0, daysLeft = 0)
        assertEquals(40.0, result.perDay, 0.001)
        assertEquals(1, result.daysLeft)
    }

    @Test
    fun `negative balance is overspent`() {
        val result = SafeToSpendCalculator.calculate(available = -20.0, daysLeft = 5)
        assertTrue(result.isOverspent)
        assertEquals(-4.0, result.perDay, 0.001)
    }

    @Test
    fun `money left in category budgets is set aside`() {
        val result = SafeToSpendCalculator.calculate(available = 300.0, daysLeft = 10, reserved = 100.0)
        assertEquals(20.0, result.perDay, 0.001)
        assertEquals(100.0, result.reserved, 0.001)
        assertFalse(result.isOverBudgeted)
    }

    @Test
    fun `budgets needing more than is left means nothing else to spend`() {
        val result = SafeToSpendCalculator.calculate(available = 50.0, daysLeft = 10, reserved = 80.0)
        assertEquals(0.0, result.perDay, 0.001)
        assertTrue(result.isOverBudgeted)
        assertFalse(result.isOverspent)
    }
}
