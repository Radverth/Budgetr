package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class CashFlowCalculatorTest {

    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
    private fun d(text: String) = fmt.parse(text)!!

    // Payday 26: 26 Sep 2026 is a Saturday, so the period runs Fri 25 Sep to Mon 26 Oct
    private val period = PayPeriodCalculator.current(26, d("15/10/2026"))
    private val today = d("15/10/2026")

    private fun tx(account: String, info: String, amount: Double, category: TransactionCategory, date: String = "25/09/2026", months: List<Int>? = null) =
        Transaction(rowIndex = 2, date = date, info = info, amount = amount, category = category, account = account, activeMonths = months)

    @Test
    fun `due days fall inside the pay period`() {
        assertEquals(d("25/09/2026"), period.start)
        assertEquals(d("25/09/2026"), CashFlowCalculator.dueDate(null, period))
        assertEquals(d("10/10/2026"), CashFlowCalculator.dueDate(10, period))
        assertEquals(d("28/09/2026"), CashFlowCalculator.dueDate(28, period))
        assertEquals(d("25/09/2026"), CashFlowCalculator.dueDate(25, period))
        assertEquals(d("30/09/2026"), CashFlowCalculator.dueDate(31, period))
        // Payday 1: 1 Nov 2026 is a Sunday, so the period ends early on Fri 30 Oct
        val short = PayPeriodCalculator.current(1, d("15/10/2026"))
        assertEquals(d("29/10/2026"), CashFlowCalculator.dueDate(31, short))
    }

    @Test
    fun `bills come out on their due day and spending comes from one account`() {
        val transactions = listOf(
            tx("Monzo", "Rent", -500.0, TransactionCategory.FIXED_COST),
            tx("Monzo", "Netflix", -10.0, TransactionCategory.FIXED_COST)
        )
        val forecast = CashFlowCalculator.forecast(
            period = period, today = today,
            balances = linkedMapOf("Monzo" to 100.0, "Halifax" to 200.0),
            transactions = transactions,
            dueDays = mapOf(CashFlowCalculator.billKey("Monzo", " rent ") to 20),
            spendingAccount = "Monzo",
            expectedSpend = List(11) { 10.0 }
        )
        val monzo = forecast.accounts.first { it.account == "Monzo" }
        assertEquals(11, monzo.days.size)
        assertEquals(590.0, monzo.days[0].balance, 0.001)
        assertEquals(40.0, monzo.days[5].balance, 0.001) // 20 Oct, rent paid
        assertEquals(-10.0, monzo.endBalance, 0.001)
        assertEquals(d("25/10/2026"), monzo.firstBelowZero!!.date)
        assertEquals(listOf("Rent"), monzo.events.map { it.label }) // Netflix went out on payday

        val warning = forecast.warnings.single()
        assertEquals("Monzo", warning.account)
        assertEquals(10.0, warning.moveAmount, 0.001)
        assertEquals("Halifax", warning.fromAccount)
        assertNull(forecast.accounts.first { it.account == "Halifax" }.firstBelowZero)
    }

    @Test
    fun `recurring income lands on its date and the lowest point is found`() {
        val forecast = CashFlowCalculator.forecast(
            period = period, today = today,
            balances = mapOf("Halifax" to 50.0),
            transactions = listOf(
                tx("Halifax", "Phone", -80.0, TransactionCategory.FIXED_COST),
                tx("Halifax", "Lodger", 300.0, TransactionCategory.RECURRING_INCOME, date = "18/10/2026"),
                tx("Halifax", "Old income", 99.0, TransactionCategory.RECURRING_INCOME, date = "01/10/2026")
            ),
            dueDays = mapOf(CashFlowCalculator.billKey("Halifax", "Phone") to 17),
            spendingAccount = null,
            expectedSpend = List(11) { 10.0 }
        )
        val halifax = forecast.accounts.single()
        assertEquals(130.0, halifax.days[0].balance, 0.001)
        assertEquals(d("17/10/2026"), halifax.lowest.date)
        assertEquals(50.0, halifax.lowest.balance, 0.001)
        assertEquals(350.0, halifax.endBalance, 0.001)
        assertTrue(forecast.warnings.isEmpty())
    }

    @Test
    fun `bills not active this month are skipped and a shortfall nobody can cover has no source`() {
        val forecast = CashFlowCalculator.forecast(
            period = period, today = today,
            balances = linkedMapOf("Monzo" to 5.0, "Savings" to 20.0),
            transactions = listOf(
                tx("Monzo", "Car tax", -150.0, TransactionCategory.FIXED_COST, months = listOf(3)),
                tx("Monzo", "Gym", -30.0, TransactionCategory.FIXED_COST)
            ),
            dueDays = mapOf(
                CashFlowCalculator.billKey("Monzo", "Car tax") to 20,
                CashFlowCalculator.billKey("Monzo", "Gym") to 16
            ),
            spendingAccount = "Monzo",
            expectedSpend = List(11) { 5.0 }
        )
        val monzo = forecast.accounts.first()
        assertEquals(listOf("Gym"), monzo.events.map { it.label })
        assertEquals(d("16/10/2026"), monzo.firstBelowZero!!.date)
        val warning = forecast.warnings.single()
        assertEquals(50.0, warning.shortfall, 0.001)
        assertEquals(50.0, warning.moveAmount, 0.001)
        assertNull(warning.fromAccount)
    }

    @Test
    fun `spending account defaults to where most one-off spending happened`() {
        val transactions = listOf(
            tx("Monzo", "Coffee", -3.0, TransactionCategory.ONE_OFF_COST, date = "10/10/2026"),
            tx("Halifax", "Shoes", -60.0, TransactionCategory.ONE_OFF_COST, date = "12/10/2026"),
            tx("Halifax", "Last month", -500.0, TransactionCategory.ONE_OFF_COST, date = "20/09/2026"),
            tx("Monzo", "Lunch", -8.0, TransactionCategory.ONE_OFF_COST, date = "14/10/2026")
        )
        assertEquals("Halifax", CashFlowCalculator.busiestSpendingAccount(transactions, period, listOf("Monzo", "Halifax")))
        assertEquals("Monzo", CashFlowCalculator.busiestSpendingAccount(emptyList(), period, listOf("Monzo", "Halifax")))
    }
}
