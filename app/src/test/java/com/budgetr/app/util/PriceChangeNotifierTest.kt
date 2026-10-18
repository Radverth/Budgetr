package com.budgetr.app.util

import com.budgetr.app.data.model.RecurringCostReview
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceChangeNotifierTest {

    private fun review(info: String, from: Double, to: Double) =
        RecurringCostReview("Monzo", info, 0L, -to, previousAmount = -from, changedAt = 1L)

    @Test
    fun `one change says which way and by how much`() {
        assertEquals(
            "Netflix went up from £10.99 to £12.99" to "Check it's right on the Subscriptions screen.",
            PriceChangeNotifier.message(listOf(review("Netflix", 10.99, 12.99)))
        )
    }

    @Test
    fun `several changes are listed`() {
        val (title, text) = PriceChangeNotifier.message(listOf(review("Netflix", 10.99, 12.99), review("Phone", 20.0, 15.0)))
        assertEquals("2 bills changed price", title)
        assertEquals("Netflix went up from £10.99 to £12.99. Phone went down from £20.00 to £15.00.", text)
    }
}
