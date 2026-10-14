package com.budgetr.app.util

import com.budgetr.app.data.model.DailyTransfer
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class DailyTargetCalculatorTest {

    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
    private fun day(s: String) = fmt.parse(s)!!

    // 10 days, £50 → £5 a day
    private val period = PayPeriod(day("01/10/2026"), day("11/10/2026"))

    private fun spend(date: String, amount: Double, category: TransactionCategory = TransactionCategory.ONE_OFF_COST, tag: String? = null) =
        Transaction(rowIndex = 0, date = date, info = "", amount = -amount, category = category, account = "A", tag = tag)

    private fun calc(
        today: String,
        transactions: List<Transaction> = emptyList(),
        transfers: List<DailyTransfer> = emptyList()
    ) = DailyTargetCalculator.calculate(period, 50.0, transactions, transfers, day(today))

    @Test
    fun `first day target is the base`() {
        val result = calc("01/10/2026")
        assertEquals(5.0, result.base, 0.001)
        assertEquals(5.0, result.today.target, 0.001)
        assertEquals(10, result.daysLeft)
    }

    @Test
    fun `spending 4 of 5 makes the next day 6`() {
        val result = calc("02/10/2026", listOf(spend("01/10/2026", 4.0)))
        assertEquals(6.0, result.today.target, 0.001)
        assertEquals(1.0, result.today.carriedIn, 0.001)
    }

    @Test
    fun `overspending lowers the next day`() {
        val result = calc("02/10/2026", listOf(spend("01/10/2026", 8.0)))
        assertEquals(2.0, result.today.target, 0.001)
    }

    @Test
    fun `three days without spending gives four times the base`() {
        assertEquals(20.0, calc("04/10/2026").today.target, 0.001)
    }

    @Test
    fun `spending today is taken off what's left today`() {
        val result = calc("01/10/2026", listOf(spend("01/10/2026", 3.0), spend("01/10/2026", 4.5)))
        assertEquals(-2.5, result.leftToday, 0.001)
        assertTrue(result.isOverToday)
        assertEquals(2.5, result.tomorrowIfNoMoreSpent!!, 0.001)
    }

    @Test
    fun `only one-off costs count and outside dates are ignored`() {
        val result = calc(
            "02/10/2026",
            listOf(
                spend("01/10/2026", 900.0, TransactionCategory.FIXED_COST),
                spend("01/10/2026", 50.0, TransactionCategory.TRANSFER),
                spend("30/09/2026", 20.0),
                spend("11/10/2026", 20.0)
            )
        )
        assertEquals(10.0, result.today.target, 0.001)
    }

    @Test
    fun `excluded categories don't count`() {
        val result = DailyTargetCalculator.calculate(
            period, 50.0, listOf(spend("01/10/2026", 4.0, tag = "Groceries")), emptyList(), day("02/10/2026")
        ) { it.tag != "Groceries" }
        assertEquals(10.0, result.today.target, 0.001)
    }

    @Test
    fun `borrowing spread over later days`() {
        val transfer = DailyTransfer(periodStart = "01/10/2026", createdOn = "01/10/2026", amount = 9.0)
        val result = calc("01/10/2026", transfers = listOf(transfer))
        assertEquals(14.0, result.today.target, 0.001)
        assertEquals(4.0, result.days[1].allocation, 0.001)
        assertEquals(50.0, result.days.sumOf { it.allocation }, 0.001)
    }

    @Test
    fun `borrowing from one day`() {
        val transfer = DailyTransfer(periodStart = "01/10/2026", createdOn = "01/10/2026", amount = 5.0, targetDay = "03/10/2026")
        val result = calc("01/10/2026", transfers = listOf(transfer))
        assertEquals(10.0, result.today.target, 0.001)
        assertEquals(0.0, result.days[2].allocation, 0.001)
        assertEquals(5.0, result.days[1].allocation, 0.001)
    }

    @Test
    fun `paying forward to one day`() {
        val transfer = DailyTransfer(periodStart = "01/10/2026", createdOn = "01/10/2026", amount = -3.0, targetDay = "05/10/2026")
        val result = calc("01/10/2026", transfers = listOf(transfer))
        assertEquals(2.0, result.today.target, 0.001)
        assertEquals(8.0, result.days[4].allocation, 0.001)
    }

    @Test
    fun `transfers that would lose money are ignored`() {
        val transfers = listOf(
            DailyTransfer(periodStart = "01/10/2026", createdOn = "10/10/2026", amount = 5.0),
            DailyTransfer(periodStart = "01/10/2026", createdOn = "05/10/2026", amount = 5.0, targetDay = "04/10/2026")
        )
        val result = calc("01/10/2026", transfers = transfers)
        assertTrue(result.days.all { kotlin.math.abs(it.allocation - 5.0) < 0.001 })
    }

    @Test
    fun `last day has no tomorrow and nothing to borrow`() {
        val result = calc("10/10/2026")
        assertNull(result.tomorrowIfNoMoreSpent)
        assertEquals(0.0, result.borrowable, 0.001)
        assertEquals(0.0, result.payableForward, 0.001)
        assertEquals(1, result.daysLeft)
        assertEquals(50.0, result.today.target, 0.001)
    }

    @Test
    fun `borrowable is what later days still hold`() {
        val result = calc("08/10/2026", listOf(spend("08/10/2026", 1.0)))
        assertEquals(10.0, result.borrowable, 0.001)
        assertEquals(39.0, result.payableForward, 0.001)
        assertFalse(result.isOverToday)
    }

    @Test
    fun `a period across the October clock change still has whole days`() {
        val dst = PayPeriod(day("20/10/2026"), day("30/10/2026"))
        val result = DailyTargetCalculator.calculate(dst, 100.0, emptyList(), emptyList(), day("28/10/2026"))
        assertEquals(10, result.days.size)
        assertEquals(8, result.todayIndex)
        assertEquals(90.0, result.today.target, 0.001)
    }
}
