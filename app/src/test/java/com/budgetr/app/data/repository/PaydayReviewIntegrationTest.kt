package com.budgetr.app.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.budgetr.app.data.api.*
import com.budgetr.app.data.local.BudgetrDatabase
import com.budgetr.app.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PaydayReviewIntegrationTest {
    @Test fun `payday preserves already entered new period costs and snapshots only old spending`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefs = PreferencesManager(context.getSharedPreferences("payday-review-test", Context.MODE_PRIVATE))
        prefs.clearAll()
        prefs.setSpreadsheetId("sheet")
        prefs.setPayDay(1)
        val current = PayPeriodCalculator.current(1)
        val previous = PayPeriodCalculator.current(1, HistoryCalculator.dayBefore(current.start))
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
        val oldDate = fmt.format(previous.start)
        val newDate = fmt.format(current.start)
        prefs.setLastPayPeriodStart(oldDate)
        val rows = mutableListOf(
            listOf("Date", "Info", "Amount", "Category", "Account", "Months", "Tag", "ID"),
            listOf(oldDate, "Old", "-10", "One Off Cost", "Bank", "", "", "old"),
            listOf(newDate, "New", "-20", "One Off Cost", "Bank", "", "", "new")
        )
        val api = Proxy.newProxyInstance(GoogleSheetsApi::class.java.classLoader, arrayOf(GoogleSheetsApi::class.java)) { _, method, args ->
            when (method.name) {
                "getSpreadsheet" -> SpreadsheetMetadata(listOf(SheetMetadata(SheetProperties(1, "Bank")), SheetMetadata(SheetProperties(2, "History"))))
                "getValues" -> when (args!![1]) {
                    "Cover Sheet!A:F" -> ValueRange(values = listOf(listOf("Account", "Balance"), listOf("Bank", "70")))
                    "Bank!A:H" -> ValueRange(values = rows.toList())
                    else -> ValueRange(values = emptyList())
                }
                "batchUpdate" -> {
                    (args!![1] as BatchUpdateRequest).requests.mapNotNull { it.deleteDimension }.forEach { rows.removeAt(it.range.startIndex) }
                    BatchUpdateResponse()
                }
                "batchUpdateValues" -> Unit
                "updateValues" -> UpdateResponse()
                "appendValues" -> AppendResponse()
                else -> error(method.name)
            }
        } as GoogleSheetsApi
        val drive = Proxy.newProxyInstance(GoogleDriveApi::class.java.classLoader, arrayOf(GoogleDriveApi::class.java)) { _, method, _ -> error(method.name) } as GoogleDriveApi
        val db = Room.inMemoryDatabaseBuilder(context, BudgetrDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = SheetsRepositoryImpl(api, drive, db.transactionDao(), db.accountBalanceDao(), db.balanceRolloverDao(),
                db.savingsGoalDao(), db.debtDao(), db.envelopeDao(), db.periodSummaryDao(), db.pendingChangeDao(), context, prefs)
            repo.refreshAccountBalances()
            repo.refreshAllTransactions()
            assertTrue(repo.checkAndProcessNewPayPeriod())
            assertEquals(listOf("ID", "new"), rows.map { it[7] })
            assertEquals(10.0, db.periodSummaryDao().getAll().first().single().oneOffCosts, 0.001)
            assertFalse(repo.checkAndProcessNewPayPeriod())
        } finally { db.close() }
    }
}
