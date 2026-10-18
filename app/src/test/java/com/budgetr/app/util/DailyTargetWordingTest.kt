package com.budgetr.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyTargetWordingTest {

    @Test
    fun `normal days read as before`() {
        assertEquals("£12.00 left today", DailyTargetWording.headline(12.0, 15.0))
        assertEquals("£4.00 over today", DailyTargetWording.headline(-4.0, 15.0))
        assertEquals("£3.00 spent of £15.00 today", DailyTargetWording.spentToday(3.0, 15.0))
        assertEquals("Tomorrow: £27.00 if you stop now", DailyTargetWording.tomorrow(27.0))
        assertEquals("£3.00 of £15.00", DailyTargetWording.spentOf(3.0, 15.0))
    }

    @Test
    fun `a day that starts behind never shows a negative target`() {
        assertEquals("£16.20 behind today", DailyTargetWording.headline(-16.2, -12.0))
        assertEquals("£4.20 spent today, after starting £12.00 behind", DailyTargetWording.spentToday(4.2, -12.0))
        assertEquals("Started today £12.00 behind from earlier days", DailyTargetWording.spentToday(0.0, -12.0))
        assertEquals("Tomorrow starts £16.20 behind, even if you stop now", DailyTargetWording.tomorrow(-16.2))
        assertEquals("£4.20 · started £12.00 behind", DailyTargetWording.spentOf(4.2, -12.0))
    }

    @Test
    fun `the last day says so`() {
        assertEquals("Last day before payday", DailyTargetWording.tomorrow(null))
    }
}
