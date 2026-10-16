package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BillsCalendarTest {

    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
    private fun d(s: String) = fmt.parse(s)!!
    private fun tx(info: String, amount: Double, category: TransactionCategory, date: String = "26/10/2026", months: List<Int>? = null) =
        Transaction(2, date, info, amount, category, "Monzo", activeMonths = months)

    private val transactions = listOf(
        tx("Rent", -850.0, TransactionCategory.FIXED_COST),
        tx("Council tax", -142.5, TransactionCategory.FIXED_COST),
        tx("Gym", -30.0, TransactionCategory.FIXED_COST),
        tx("Car insurance", -480.0, TransactionCategory.FIXED_COST, months = listOf(3)),
        tx("Salary", 2150.0, TransactionCategory.SALARY),
        tx("Lodger", 300.0, TransactionCategory.RECURRING_INCOME, date = "24/09/2026"),
        tx("Coffee", -3.0, TransactionCategory.ONE_OFF_COST)
    )
    private val dueDays = mapOf(
        CashFlowCalculator.billKey("Monzo", "Council tax") to 20,
        CashFlowCalculator.billKey("Monzo", "Gym") to 31,
        CashFlowCalculator.billKey("Monzo", "Car insurance") to 5
    )

    private fun on(events: List<CalendarEvent>, date: String) = events.filter { it.date == d(date) }.map { it.label }

    @Test
    fun `bills fall on their due day or payday and income on its day`() {
        // Payday 26: Mon 26 Oct 2026
        val october = BillsCalendar.events(2026, Calendar.OCTOBER, transactions, dueDays, 26)
        assertEquals(listOf("Council tax"), on(october, "20/10/2026"))
        assertEquals(listOf("Lodger"), on(october, "24/10/2026"))
        assertEquals(listOf("Rent", "Salary"), on(october, "26/10/2026").sorted())
        assertEquals(listOf("Gym"), on(october, "31/10/2026"))
        assertTrue(october.none { it.label == "Car insurance" || it.label == "Coffee" })
        assertTrue(october.first { it.label == "Rent" }.onPayday)
        // A due day past the end of a short month uses its last day; a yearly bill shows in its month
        assertEquals(listOf("Gym"), on(BillsCalendar.events(2026, Calendar.NOVEMBER, transactions, dueDays, 26), "30/11/2026"))
        assertEquals(listOf("Car insurance"), on(BillsCalendar.events(2027, Calendar.MARCH, transactions, dueDays, 26), "05/03/2027"))
    }

    @Test
    fun `a payday moved back for the weekend shows in the month it lands in`() {
        // Payday 1: Sun 1 Nov 2026 moves to Fri 30 Oct, so October has two paydays and November none
        val pay = listOf(tx("Salary", 2000.0, TransactionCategory.SALARY))
        assertEquals(2, BillsCalendar.events(2026, Calendar.OCTOBER, pay, emptyMap(), 1).size)
        assertTrue(BillsCalendar.events(2026, Calendar.NOVEMBER, pay, emptyMap(), 1).isEmpty())
    }

    @Test
    fun `the grid is whole Monday-first weeks with spending only up to today`() {
        val days = listOf(DaySpendOn(d("14/10/2026"), 12.0, 15.0), DaySpendOn(d("15/10/2026"), 20.0, 15.0), DaySpendOn(d("16/10/2026"), 0.0, 15.0))
        val grid = BillsCalendar.grid(2026, Calendar.OCTOBER, emptyList(), days, today = d("15/10/2026"))
        assertEquals(35, grid.size)
        assertEquals(d("28/09/2026"), grid.first().date) // Thu 1 Oct, so the week starts Mon 28 Sep
        assertFalse(grid.first().inMonth)
        assertEquals(d("01/11/2026"), grid.last().date)
        val byDate = grid.associateBy { it.date }
        assertTrue(byDate.getValue(d("14/10/2026")).spend!!.underTarget)
        assertFalse(byDate.getValue(d("15/10/2026")).spend!!.underTarget)
        assertTrue(byDate.getValue(d("15/10/2026")).isToday)
        assertNull(byDate.getValue(d("16/10/2026")).spend)
    }

    @Test
    fun `only bills with their own due day remind the evening before`() {
        assertEquals(listOf("Council tax"), BillsCalendar.dueTomorrow(d("19/10/2026"), transactions, dueDays, 26).map { it.label })
        // Rent is on payday (no due day), so no reminder
        assertTrue(BillsCalendar.dueTomorrow(d("25/10/2026"), transactions, dueDays, 26).isEmpty())
    }
}
