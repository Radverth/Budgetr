package com.budgetr.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val backup = PhoneBackup(
        savedAt = 1_760_000_000_000,
        categoryBudgets = listOf(CategoryBudgetBackup("ONE_OFF_COST", 400.0)),
        envelopes = listOf(EnvelopeBackup("Groceries", 250.0, rollover = true, carriedOver = 18.5, carriedForPeriod = "25/09/2026")),
        goalLinks = listOf(GoalLinkBackup("Holiday", "ONE_OFF_COST")),
        subscriptionReviews = listOf(ReviewBackup("Monzo", "Netflix", 1_700_000_000_000, -10.99, null)),
        dailyTransfers = listOf(DailyTransferBackup("25/09/2026", "14/10/2026", 10.0, null)),
        rollovers = listOf(RolloverBackup("Monzo", 120.5, "25/09/2026")),
        settings = SettingsBackup(payDay = 26, reminderEnabled = true, reminderHour = 20, dailyPotOverride = 600.0)
    )

    @Test fun `round trips through JSON`() {
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }

    @Test fun `blank, broken and newer backups are ignored`() {
        assertNull(BackupCodec.decode(""))
        assertNull(BackupCodec.decode("{not json"))
        assertNull(BackupCodec.decode(BackupCodec.encode(backup.copy(version = PhoneBackup.CURRENT_VERSION + 1))))
    }

    @Test fun `missing sections read as empty, so older backups still load`() {
        val decoded = BackupCodec.decode("""{"version":1,"savedAt":5}""")!!
        assertTrue(decoded.isEmpty)
        assertEquals(SettingsBackup(), decoded.settings)
    }

    @Test fun `same content ignores when it was saved`() {
        assertTrue(backup.sameContentAs(backup.copy(savedAt = 1)))
        assertFalse(backup.sameContentAs(backup.copy(envelopes = emptyList())))
        assertFalse(backup.sameContentAs(null))
    }
}
