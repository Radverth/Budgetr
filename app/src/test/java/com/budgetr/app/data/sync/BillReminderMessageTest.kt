package com.budgetr.app.data.sync

import com.budgetr.app.util.CalendarEvent
import com.budgetr.app.util.CalendarEventKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class BillReminderMessageTest {

    private fun bill(label: String, amount: Double) = CalendarEvent(Date(), label, -amount, CalendarEventKind.BILL, "Monzo")

    @Test
    fun `one bill names it and the account`() {
        assertEquals("Council tax goes out tomorrow" to "£142.50 from Monzo", BillReminderWorker.message(listOf(bill("Council tax", 142.5))))
    }

    @Test
    fun `several bills are listed with a total`() {
        val (title, text) = BillReminderWorker.message(listOf(bill("Council tax", 142.5), bill("Gym", 30.0), bill("Phone", 15.0)))
        assertEquals("3 bills go out tomorrow", title)
        assertEquals("Council tax £142.50, Gym £30.00 and Phone £15.00 (£187.50 in all)", text)
    }
}
