package com.budgetr.app.util

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QuickAddPreferencesTest {
    @Test fun `last account survives recreation but does not leak across spreadsheets`() {
        val storage = RuntimeEnvironment.getApplication().getSharedPreferences("quick-add", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        val first = PreferencesManager(storage)
        first.setSpreadsheetId("sheet-a")
        first.setLastAddAccount("Savings")
        val recreated = PreferencesManager(storage)
        assertEquals("Savings", recreated.getLastAddAccount())
        recreated.setSpreadsheetId("sheet-b")
        assertEquals(null, recreated.getLastAddAccount())
        recreated.setSpreadsheetId("sheet-a")
        assertEquals("Savings", recreated.getLastAddAccount())
    }
}
