package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class SubscriptionTrackerTest {

    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
    private fun d(s: String) = fmt.parse(s)!!
    private fun bill(info: String, amount: Double, months: List<Int>? = null) =
        Transaction(2, "26/10/2026", info, -amount, TransactionCategory.FIXED_COST, "Monzo", activeMonths = months)

    private val bills = listOf(
        bill("Netflix", 12.99),
        bill("Rent", 850.0),
        bill("Car insurance", 480.0, months = listOf(3)),
        bill("Gas and electric", 120.0, months = listOf(10, 11, 12, 1, 2, 3)),
        Transaction(9, "15/10/2026", "Coffee", -3.0, TransactionCategory.ONE_OFF_COST, "Monzo")
    )
    private val dueDays = mapOf(
        CashFlowCalculator.billKey("Monzo", "Netflix") to 14,
        CashFlowCalculator.billKey("Monzo", "Car insurance") to 5,
        CashFlowCalculator.billKey("Monzo", "Gas and electric") to 1
    )

    @Test
    fun `next charge is the next due day or payday, in an active month`() {
        val list = SubscriptionTracker.list(bills, dueDays, 26, emptyMap(), today = d("15/10/2026"))
        val byName = list.associateBy { it.bill.info }
        assertEquals(4, list.size)
        assertEquals(d("26/10/2026"), byName.getValue("Rent").nextCharge)
        assertTrue(byName.getValue("Rent").onPayday)
        assertEquals(d("14/11/2026"), byName.getValue("Netflix").nextCharge) // the 14th has passed this month
        assertEquals(d("01/11/2026"), byName.getValue("Gas and electric").nextCharge)
        assertEquals(d("05/03/2027"), byName.getValue("Car insurance").nextCharge)
        assertEquals(listOf("Rent", "Gas and electric", "Netflix", "Car insurance"), list.map { it.bill.info })
    }

    @Test
    fun `due today still counts as next`() {
        val netflix = SubscriptionTracker.list(bills, dueDays, 26, emptyMap(), today = d("14/10/2026")).first { it.bill.info == "Netflix" }
        assertEquals(d("14/10/2026"), netflix.nextCharge)
    }

    @Test
    fun `yearly and monthly cost follow the months it's active`() {
        val byName = SubscriptionTracker.list(bills, dueDays, 26, emptyMap(), today = d("15/10/2026")).associateBy { it.bill.info }
        assertEquals(155.88, byName.getValue("Netflix").yearly, 0.001)
        assertEquals(480.0, byName.getValue("Car insurance").yearly, 0.001)
        assertEquals(40.0, byName.getValue("Car insurance").monthly, 0.001)
        assertEquals(720.0, byName.getValue("Gas and electric").yearly, 0.001)
    }

    @Test
    fun `a price change is a difference of a penny or more`() {
        val now = d("15/10/2026")
        assertEquals(PriceChange(10.99, 12.99, now), SubscriptionTracker.detect(-10.99, -12.99, now))
        assertTrue(SubscriptionTracker.detect(-10.99, -12.99, now)!!.wentUp)
        assertNull(SubscriptionTracker.detect(-10.99, -10.99, now))
        assertNull(SubscriptionTracker.detect(null, -10.99, now))
        val change = PriceChange(10.99, 12.99, now)
        val listed = SubscriptionTracker.list(bills, dueDays, 26, mapOf(CashFlowCalculator.billKey("Monzo", "Netflix") to change), now)
        assertEquals(change, listed.first { it.bill.info == "Netflix" }.change)
    }
}
