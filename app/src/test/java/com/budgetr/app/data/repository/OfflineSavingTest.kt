package com.budgetr.app.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.budgetr.app.data.api.GoogleDriveApi
import com.budgetr.app.data.api.GoogleSheetsApi
import com.budgetr.app.data.local.BudgetrDatabase
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.util.PreferencesManager
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OfflineSavingTest {
    private lateinit var repository: SheetsRepositoryImpl
    private lateinit var prefs: PreferencesManager
    private val transaction = Transaction(2, "15/10/2026", "Coffee", -3.4,
        TransactionCategory.ONE_OFF_COST, "Current", id = "coffee-id")

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        prefs = PreferencesManager(context.getSharedPreferences("offline-test", Context.MODE_PRIVATE))
        prefs.setSpreadsheetId("original-sheet")
        val db = Room.inMemoryDatabaseBuilder(context, BudgetrDatabase::class.java).allowMainThreadQueries().build()
        repository = SheetsRepositoryImpl(offlineApi(GoogleSheetsApi::class.java), offlineApi(GoogleDriveApi::class.java),
            db.transactionDao(), db.accountBalanceDao(), db.balanceRolloverDao(), db.savingsGoalDao(),
            db.debtDao(), db.envelopeDao(), db.periodSummaryDao(), db.pendingChangeDao(), context, prefs)
    }

    // Only the network boundary is fake; preferences, queue, repository and Room are real.
    private fun <T> offlineApi(type: Class<T>): T = type.cast(Proxy.newProxyInstance(
        type.classLoader, arrayOf(type)
    ) { _, _, _ -> throw IOException("No signal") })

    @Test fun `changing sheets hides old queued changes and switching back restores them`() = runBlocking {
        repository.addTransaction(transaction)
        assertEquals(1, repository.getPendingChanges().first().size)
        prefs.setSpreadsheetId("other-sheet")
        assertTrue(repository.getPendingChanges().first().isEmpty())
        assertTrue(repository.getAllTransactions().first().isEmpty())
        assertEquals(SyncOutcome.NOTHING_TO_SEND, repository.syncPendingChanges())
        prefs.setSpreadsheetId("original-sheet")
        assertEquals("Coffee", repository.getPendingChanges().first().single().transaction.info)
    }

    @Test fun `edits without a stable ID are rejected before queueing`() = runBlocking {
        try {
            repository.updateTransaction(transaction.copy(id = null))
            fail("An offline edit must not rely on a stale row number")
        } catch (_: IllegalArgumentException) { }
        assertTrue(repository.getPendingChanges().first().isEmpty())
    }
}
