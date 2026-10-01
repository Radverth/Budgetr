package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

private fun date(s: String) = SimpleDateFormat("dd/MM/yyyy", Locale.UK).parse(s)!!

class BudgetCapCalculatorTest {

    @Test
    fun `percent used is 0 for a non-positive limit`() {
        assertEquals(0f, BudgetCapCalculator.percentUsed(50.0, 0.0), 0.0001f)
    }

    @Test
    fun `percent used is half at half the limit`() {
        assertEquals(0.5f, BudgetCapCalculator.percentUsed(50.0, 100.0), 0.0001f)
    }

    @Test
    fun `not approaching below 80 percent`() {
        assertFalse(BudgetCapCalculator.isApproachingLimit(79.0, 100.0))
    }

    @Test
    fun `approaching between 80 and 100 percent inclusive`() {
        assertTrue(BudgetCapCalculator.isApproachingLimit(80.0, 100.0))
        assertTrue(BudgetCapCalculator.isApproachingLimit(100.0, 100.0))
    }

    @Test
    fun `over limit only once spend exceeds it`() {
        assertFalse(BudgetCapCalculator.isOverLimit(100.0, 100.0))
        assertTrue(BudgetCapCalculator.isOverLimit(100.01, 100.0))
    }
}

class NoSpendStreakCalculatorTest {

    @Test
    fun `streak counts days since the most recent spend`() {
        val streak = NoSpendStreakCalculator.currentStreakDays(
            spendDates = listOf("01/01/2026", "05/01/2026"),
            periodStart = date("01/01/2026"),
            today = date("10/01/2026")
        )
        assertEquals(5, streak)
    }

    @Test
    fun `streak falls back to period start with no spend yet`() {
        val streak = NoSpendStreakCalculator.currentStreakDays(
            spendDates = emptyList(),
            periodStart = date("01/01/2026"),
            today = date("04/01/2026")
        )
        assertEquals(3, streak)
    }

    @Test
    fun `unparseable dates are ignored`() {
        val streak = NoSpendStreakCalculator.currentStreakDays(
            spendDates = listOf("not-a-date"),
            periodStart = date("01/01/2026"),
            today = date("04/01/2026")
        )
        assertEquals(3, streak)
    }
}

class SpendingTrendCalculatorTest {

    private fun oneOff(date: String, amount: Double) = Transaction(
        rowIndex = 1, date = date, info = "Coffee", amount = amount,
        category = TransactionCategory.ONE_OFF_COST, account = "Main"
    )

    @Test
    fun `returns null with no spend in either window`() {
        assertNull(SpendingTrendCalculator.weekOverWeek(emptyList(), today = date("15/01/2026")))
    }

    @Test
    fun `buckets spend into this week and last week`() {
        val today = date("15/01/2026")
        val transactions = listOf(
            oneOff("14/01/2026", 20.0), // this week
            oneOff("10/01/2026", 10.0), // this week
            oneOff("05/01/2026", 15.0), // last week
            oneOff("20/12/2025", 999.0) // too old, excluded
        )
        val trend = SpendingTrendCalculator.weekOverWeek(transactions, today)!!
        assertEquals(30.0, trend.thisWeek, 0.001)
        assertEquals(15.0, trend.lastWeek, 0.001)
    }

    @Test
    fun `percent change is null with no prior week spend`() {
        val trend = SpendingTrend(thisWeek = 40.0, lastWeek = 0.0)
        assertNull(trend.percentChange)
    }

    @Test
    fun `percent change reflects an increase`() {
        val trend = SpendingTrend(thisWeek = 150.0, lastWeek = 100.0)
        assertEquals(50f, trend.percentChange!!, 0.001f)
    }
}

class RecurringCostReviewCalculatorTest {

    private val now = date("01/06/2026").time
    private val dayMillis = 24L * 60 * 60 * 1000

    @Test
    fun `not due before the threshold age`() {
        val firstSeen = now - 89 * dayMillis
        assertFalse(RecurringCostReviewCalculator.isDueForReview(firstSeen, dismissedUntil = null, now = now))
    }

    @Test
    fun `due once it reaches the threshold age`() {
        val firstSeen = now - 90 * dayMillis
        assertTrue(RecurringCostReviewCalculator.isDueForReview(firstSeen, dismissedUntil = null, now = now))
    }

    @Test
    fun `suppressed while a dismissal is still active`() {
        val firstSeen = now - 365 * dayMillis
        val dismissedUntil = now + dayMillis
        assertFalse(RecurringCostReviewCalculator.isDueForReview(firstSeen, dismissedUntil, now = now))
    }

    @Test
    fun `due again once a dismissal expires`() {
        val firstSeen = now - 365 * dayMillis
        val dismissedUntil = now - dayMillis
        assertTrue(RecurringCostReviewCalculator.isDueForReview(firstSeen, dismissedUntil, now = now))
    }
}

class SpendForCategoryTest {

    private fun tx(date: String, amount: Double, category: TransactionCategory = TransactionCategory.ONE_OFF_COST) =
        Transaction(rowIndex = 0, date = date, info = "", amount = amount, category = category, account = "A")

    private val period = PayPeriod(start = date("25/09/2026"), nextPayday = date("26/10/2026"))

    @Test
    fun `one-off costs outside the pay period are not counted`() {
        val spend = spendForCategory(
            listOf(tx("24/09/2026", -10.0), tx("25/09/2026", -20.0), tx("25/10/2026", -5.0), tx("26/10/2026", -40.0)),
            TransactionCategory.ONE_OFF_COST,
            period = period
        )
        assertEquals(25.0, spend, 0.001)
    }

    @Test
    fun `unreadable dates still count`() {
        val spend = spendForCategory(listOf(tx("", -10.0)), TransactionCategory.ONE_OFF_COST, period = period)
        assertEquals(10.0, spend, 0.001)
    }

    @Test
    fun `fixed costs ignore the period dates`() {
        val spend = spendForCategory(
            listOf(tx("01/01/2026", -50.0, TransactionCategory.FIXED_COST)),
            TransactionCategory.FIXED_COST,
            period = period
        )
        assertEquals(50.0, spend, 0.001)
    }
}
