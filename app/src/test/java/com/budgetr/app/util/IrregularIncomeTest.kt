package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class IrregularIncomeTest {

    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
    private fun d(s: String) = fmt.parse(s)!!
    private val period = PayPeriod(d("01/10/2026"), d("01/11/2026")) // 31 days

    private fun income(amount: Double, date: String, category: TransactionCategory = TransactionCategory.INCOME) =
        Transaction(2, date, "Client", amount, category, "Monzo")

    private val transactions = listOf(
        income(400.0, "01/10/2026"),
        income(800.0, "11/10/2026"),
        income(210.0, "20/10/2026"),
        income(999.0, "30/09/2026"), // last period
        Transaction(3, "05/10/2026", "Coffee", -3.0, TransactionCategory.ONE_OFF_COST, "Monzo")
    )

    @Test
    fun `commitments come off the first money in`() {
        val plan = IrregularIncome.plan(period, transactions, commitments = 500.0, today = d("05/10/2026"))
        assertEquals(400.0, plan.received, 0.001)
        assertEquals(0.0, plan.available, 0.001)
        assertEquals(0.0, plan.allocations.sum(), 0.001)
    }

    @Test
    fun `a payment is spread over the days left from the day it arrived`() {
        val plan = IrregularIncome.plan(period, transactions, commitments = 500.0, today = d("15/10/2026"))
        assertEquals(1200.0, plan.received, 0.001) // the 20th hasn't arrived yet
        assertEquals(700.0, plan.available, 0.001)
        assertEquals(0.0, plan.allocations[9], 0.001)
        assertEquals(700.0 / 21, plan.allocations[10], 0.001)
        assertEquals(700.0 / 21, plan.allocations[30], 0.001)
        assertEquals(700.0, plan.allocations.sum(), 0.001)
    }

    @Test
    fun `later payments add to the days after them`() {
        val plan = IrregularIncome.plan(period, transactions, commitments = 500.0, today = d("25/10/2026"))
        assertEquals(910.0, plan.available, 0.001)
        assertEquals(700.0 / 21, plan.allocations[18], 0.001)
        assertEquals(700.0 / 21 + 210.0 / 12, plan.allocations[19], 0.001)
        assertEquals(910.0, plan.allocations.sum(), 0.001)
    }

    @Test
    fun `the daily target uses the shares and carries what's unspent`() {
        val plan = IrregularIncome.plan(period, transactions, commitments = 500.0, today = d("15/10/2026"))
        val target = DailyTargetCalculator.calculate(period, plan.available, transactions, emptyList(), d("15/10/2026"), allocations = plan.allocations)
        assertEquals(700.0 / 21, target.base, 0.001)
        // Five days of £33.33 from the 11th, less the £3 coffee bought before any money arrived
        assertEquals(5 * 700.0 / 21 - 3.0, target.leftToday, 0.001)
    }
}
