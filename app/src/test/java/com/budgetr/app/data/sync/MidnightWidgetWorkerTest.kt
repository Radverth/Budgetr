package com.budgetr.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MidnightWidgetWorkerTest {

    private fun at(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 15, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun `late evening waits until just after midnight`() {
        assertEquals((60 + 5) * 60_000L, MidnightWidgetWorker.delayUntilAfterMidnight(at(23, 0)))
    }

    @Test fun `just after midnight books the following night`() {
        assertEquals((24 * 60 - 1) * 60_000L, MidnightWidgetWorker.delayUntilAfterMidnight(at(0, 6)))
    }
}
