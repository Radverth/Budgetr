package com.budgetr.app.util

import com.budgetr.app.data.model.Envelope
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class EnvelopeCalculatorTest {

    private fun day(s: String) = SimpleDateFormat("dd/MM/yyyy", Locale.UK).parse(s)!!

    private fun tx(date: String, amount: Double, tag: String?, category: TransactionCategory = TransactionCategory.ONE_OFF_COST) =
        Transaction(rowIndex = 0, date = date, info = "", amount = amount, category = category, account = "A", tag = tag)

    private val period = PayPeriod(start = day("25/09/2026"), nextPayday = day("26/10/2026"))

    @Test
    fun `spent counts matching tag in the period only`() {
        val spent = EnvelopeCalculator.spent(
            listOf(
                tx("01/10/2026", -30.0, "Groceries"),
                tx("02/10/2026", -20.0, "groceries"),
                tx("03/10/2026", -15.0, "Fun"),
                tx("27/10/2026", -40.0, "Groceries"),
                tx("01/10/2026", -99.0, "Groceries", TransactionCategory.FIXED_COST)
            ),
            "Groceries",
            period
        )
        assertEquals(50.0, spent, 0.001)
    }

    @Test
    fun `status includes carried money and a daily allowance`() {
        val status = EnvelopeStatus(Envelope("Fun", limit = 100.0, rollover = true, carriedOver = 20.0), spent = 60.0, daysLeft = 10)
        assertEquals(120.0, status.available, 0.001)
        assertEquals(60.0, status.left, 0.001)
        assertEquals(6.0, status.perDayLeft, 0.001)
        assertFalse(status.isOver)
    }

    @Test
    fun `over budget has no daily allowance`() {
        val status = EnvelopeStatus(Envelope("Fun", limit = 50.0), spent = 70.0, daysLeft = 5)
        assertTrue(status.isOver)
        assertEquals(0.0, status.perDayLeft, 0.001)
    }

    @Test
    fun `reserved sums what's left and ignores overspent budgets`() {
        val statuses = listOf(
            EnvelopeStatus(Envelope("Groceries", 200.0), spent = 50.0, daysLeft = 10),
            EnvelopeStatus(Envelope("Fun", 40.0), spent = 60.0, daysLeft = 10)
        )
        assertEquals(150.0, EnvelopeCalculator.reserved(statuses), 0.001)
    }

    @Test
    fun `carry is unspent money for rollover envelopes`() {
        assertEquals(30.0, EnvelopeCalculator.nextCarry(Envelope("Fun", 100.0, rollover = true, carriedOver = 10.0), spent = 80.0), 0.001)
    }

    @Test
    fun `carry is zero when overspent or rollover is off`() {
        assertEquals(0.0, EnvelopeCalculator.nextCarry(Envelope("Fun", 100.0, rollover = true), spent = 150.0), 0.001)
        assertEquals(0.0, EnvelopeCalculator.nextCarry(Envelope("Fun", 100.0, rollover = false), spent = 10.0), 0.001)
    }
}
