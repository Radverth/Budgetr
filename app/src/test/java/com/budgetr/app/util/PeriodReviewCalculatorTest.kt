package com.budgetr.app.util

import com.budgetr.app.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PeriodReviewCalculatorTest {
    private fun date(s: String) = SimpleDateFormat("dd/MM/yyyy", Locale.UK).parse(s)!!
    private val period get() = PayPeriod(date("01/10/2026"), date("01/11/2026"))
    private fun tx(amount: Double, date: String = "01/10/2026", tag: String? = null) =
        Transaction(2, date, "Cost", -amount, TransactionCategory.ONE_OFF_COST, "Bank", tag = tag)
    private fun plan(pot: Double = 100.0) = ReviewPlan("01/10/2026", pot)

    @Test fun `spending compares with saved pot in both directions`() {
        val under = PeriodReviewCalculator.calculate(period, listOf(tx(80.0)), plan(), emptyList(), 20.0)
        assertEquals(20.0, under.planned!! - under.spending, 0.001)
        val over = PeriodReviewCalculator.calculate(period, listOf(tx(100.0)), plan(80.0), emptyList(), -20.0)
        assertEquals(-20.0, over.planned!! - over.spending, 0.001)
        assertEquals(-2000L, over.endBalancePence)
    }
    @Test fun `period excludes future and previous costs and includes zero spend days`() {
        val result = PeriodReviewCalculator.calculate(period, listOf(tx(1.0), tx(100.0, "30/09/2026"), tx(100.0, "01/11/2026")), plan(), emptyList(), 0.0)
        assertEquals(1.0, result.spending, 0.001)
        assertEquals(31, result.daysUnderTarget)
        assertEquals("31/10/2026", result.periodEnd)
    }
    @Test fun `category plan includes carry but unbudgeted spending has no variance`() {
        val result = PeriodReviewCalculator.calculate(period, listOf(tx(80.0, tag = "Food"), tx(12.0, tag = "Fun")),
            plan().copy(envelopes = listOf(ReviewEnvelope("Food", 100.0, 20.0, true))), emptyList(), 60.0)
        assertEquals(40.0, result.categories.first { it.tag == "Food" }.variance!!, 0.001)
        assertNull(result.categories.first { it.tag == "Fun" }.variance)
        assertEquals(4000L, result.reservedCarryPence)
    }
    @Test fun `missing or mismatched plan never invents comparisons`() {
        for (plan in listOf(null, plan().copy(periodStart = "01/09/2026"))) {
            val result = PeriodReviewCalculator.calculate(period, listOf(tx(20.0)), plan, emptyList(), 0.0)
            assertEquals(20.0, result.spending, 0.001)
            assertNull(result.planned)
            assertNull(result.daysUnderTarget)
        }
    }
    @Test fun `daily results use transfers and budget inclusion`() {
        val short = PayPeriod(date("01/10/2026"), date("03/10/2026"))
        val transactions = listOf(tx(15.0), tx(90.0, tag = "Food"))
        val p = plan(20.0).copy(envelopes = listOf(ReviewEnvelope("Food", 100.0, 0.0, false)), countBudgeted = false)
        val transfers = listOf(DailyTransfer(periodStart = "01/10/2026", createdOn = "01/10/2026", amount = 5.0))
        assertEquals(2, PeriodReviewCalculator.calculate(short, transactions, p, transfers, 0.0).daysUnderTarget)
        assertEquals(1, PeriodReviewCalculator.calculate(short, transactions, p, emptyList(), 0.0).daysUnderTarget)
        assertEquals(0, PeriodReviewCalculator.calculate(short, transactions, p.copy(countBudgeted = true), transfers, 0.0).daysUnderTarget)
    }
    @Test fun `clock change retains all calendar days`() {
        val old = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            assertEquals(31, PeriodReviewCalculator.calculate(period, emptyList(), plan(), emptyList(), 0.0).totalDays)
        } finally { TimeZone.setDefault(old) }
    }
    @Test fun `eligibility excludes reserves and caps at available cash`() {
        assertEquals(5000L, PeriodReviewCalculator.eligibleLeftover(10000, 3000, 5000))
        assertEquals(7000L, PeriodReviewCalculator.eligibleLeftover(10000, 3000, 9000))
        assertEquals(0L, PeriodReviewCalculator.eligibleLeftover(-100, 0, 500))
        assertEquals(0L, PeriodReviewCalculator.eligibleLeftover(10000, 3000, -500))
        assertEquals(500L, PeriodReviewCalculator.eligibleLeftover(1000, -300, 500))
    }
}
