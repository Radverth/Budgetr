package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class AnnualFundCalculatorTest {

    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
    private fun period(start: String, next: String) = PayPeriod(fmt.parse(start)!!, fmt.parse(next)!!)
    private val october = period("26/10/2026", "26/11/2026")
    private val march = period("26/03/2027", "26/04/2027")

    private fun bill(months: List<Int>?, amount: Double = -480.0, category: TransactionCategory = TransactionCategory.FIXED_COST) =
        Transaction(2, "26/10/2026", "Car insurance", amount, category, "Monzo", activeMonths = months)

    @Test
    fun `only fixed costs in exactly one month are yearly`() {
        assertTrue(AnnualFundCalculator.isYearly(bill(listOf(3))))
        assertFalse(AnnualFundCalculator.isYearly(bill(null)))
        assertFalse(AnnualFundCalculator.isYearly(bill(listOf(3, 9))))
        assertFalse(AnnualFundCalculator.isYearly(bill(listOf(3), category = TransactionCategory.ONE_OFF_COST)))
    }

    @Test
    fun `paydays to save count down to the bill's month and restart at twelve`() {
        assertEquals(5, AnnualFundCalculator.paydaysToSave(october, 3)) // Oct, Nov, Dec, Jan, Feb
        assertEquals(12, AnnualFundCalculator.paydaysToSave(march, 3))
        assertEquals(1, AnnualFundCalculator.paydaysToSave(october, 11))
    }

    @Test
    fun `a standing start sets aside a twelfth and processing twice changes nothing`() {
        val fund = AnnualFundCalculator.process(AnnualFund("Monzo", "Car insurance"), 480.0, 3, march)
        assertEquals(0.0, fund.lastReleased, 0.001)
        assertEquals(40.0, fund.lastSetAside, 0.001)
        assertEquals(40.0, fund.saved, 0.001)
        assertEquals(fund, AnnualFundCalculator.process(fund, 480.0, 3, march))
        assertEquals(-40.0, AnnualFundCalculator.potAdjustment(listOf(fund), march), 0.001)
    }

    @Test
    fun `starting late catches up by the bill's month`() {
        val fund = AnnualFundCalculator.process(AnnualFund("Monzo", "Car insurance", saved = 80.0, processedPeriod = "26/09/2026"), 480.0, 3, october)
        assertEquals(80.0, fund.lastSetAside, 0.001) // (480 - 80) / 5
        assertEquals(160.0, fund.saved, 0.001)
        val status = AnnualFundCalculator.statuses(listOf(bill(listOf(3))), listOf(fund), october).single()
        assertEquals(40.0, status.monthly, 0.001)
        assertFalse(status.onTrack)
    }

    @Test
    fun `the due month releases the fund to pay the bill and starts again`() {
        val saved = AnnualFund("Monzo", "Car insurance", saved = 480.0, processedPeriod = "26/02/2027")
        val fund = AnnualFundCalculator.process(saved, 480.0, 3, march)
        assertEquals(480.0, fund.lastReleased, 0.001)
        assertEquals(40.0, fund.lastSetAside, 0.001)
        assertEquals(40.0, fund.saved, 0.001)
        assertEquals(440.0, AnnualFundCalculator.potAdjustment(listOf(fund), march), 0.001)
        assertEquals(40.0, AnnualFundCalculator.reserved(listOf(fund)), 0.001)
    }

    @Test
    fun `bills without a fund show but set nothing aside`() {
        val status = AnnualFundCalculator.statuses(listOf(bill(listOf(3)), bill(null)), emptyList(), october).single()
        assertFalse(status.saving)
        assertEquals(0.0, status.setAsideNow, 0.001)
        assertEquals(3, status.dueMonth)
    }
}
