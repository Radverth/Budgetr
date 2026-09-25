package com.budgetr.app.util

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSchedulerTest {

    private fun calendarAt(hour: Int, minute: Int, second: Int = 0): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun `schedules later today when the target time hasn't passed yet`() {
        val now = calendarAt(9, 0)
        val expected = calendarAt(20, 0)

        val result = ReminderScheduler.nextTriggerTimeMillis(hour = 20, minute = 0, now = now.timeInMillis)

        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `schedules tomorrow when the target time already passed today`() {
        val now = calendarAt(21, 0)
        val expected = calendarAt(20, 0).apply { add(Calendar.DAY_OF_MONTH, 1) }

        val result = ReminderScheduler.nextTriggerTimeMillis(hour = 20, minute = 0, now = now.timeInMillis)

        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `schedules tomorrow when now is exactly the target time`() {
        val now = calendarAt(20, 0)
        val expected = calendarAt(20, 0).apply { add(Calendar.DAY_OF_MONTH, 1) }

        val result = ReminderScheduler.nextTriggerTimeMillis(hour = 20, minute = 0, now = now.timeInMillis)

        assertEquals(expected.timeInMillis, result)
    }
}
