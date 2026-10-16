package com.budgetr.app.data.repository

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.budgetr.app.ui.screens.transactions.TransactionsViewModel
import com.budgetr.app.data.model.PendingKind
import org.robolectric.Shadows
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.room.Room
import com.budgetr.app.data.api.GoogleDriveApi
import com.budgetr.app.data.api.GoogleSheetsApi
import com.budgetr.app.data.api.ValueRange
import com.budgetr.app.data.local.BudgetrDatabase
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.util.PreferencesManager
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
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
    private lateinit var db: BudgetrDatabase
    private lateinit var repository: SheetsRepositoryImpl
    private lateinit var prefs: PreferencesManager
    private lateinit var workManager: WorkManager
    private var response: (String, Array<out Any?>) -> Any? = { _, _ -> throw IOException("No signal") }
    private val transaction = Transaction(2, "15/10/2026", "Coffee", -3.4,
        TransactionCategory.ONE_OFF_COST, "Current", id = "coffee-id")

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        try {
            workManager = WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(context, Configuration.Builder().build())
            workManager = WorkManager.getInstance(context)
        }
        prefs = PreferencesManager(context.getSharedPreferences("offline-test", Context.MODE_PRIVATE))
        prefs.setSpreadsheetId("original-sheet")
        db = Room.inMemoryDatabaseBuilder(context, BudgetrDatabase::class.java).allowMainThreadQueries().build()
        repository = SheetsRepositoryImpl(offlineApi(GoogleSheetsApi::class.java), offlineApi(GoogleDriveApi::class.java),
            db.transactionDao(), db.accountBalanceDao(), db.balanceRolloverDao(), db.savingsGoalDao(),
            db.debtDao(), db.envelopeDao(), db.periodSummaryDao(), db.pendingChangeDao(), context, prefs, PeriodReviewRepository(db, prefs))
    }

    // Only the network boundary is fake; preferences, queue, repository and Room are real.
    private fun <T> offlineApi(type: Class<T>): T = type.cast(Proxy.newProxyInstance(
        type.classLoader, arrayOf(type)
    ) { _, method, args ->
        try {
            response(method.name, args ?: emptyArray())
        } catch (e: IOException) {
            // Deliver a suspend failure as Retrofit does, without Java Proxy wrapping it.
            @Suppress("UNCHECKED_CAST")
            val continuation = args!!.last() as Continuation<Any?>
            continuation.resumeWith(Result.failure(e))
            COROUTINE_SUSPENDED
        }
    })

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

    @Test fun `lost refresh retains the saved transaction on the phone`() = runBlocking {
        response = { name, args ->
            if (name == "getValues" && args[1].toString().endsWith("!H:H")) {
                ValueRange(values = listOf(listOf("ID"), listOf("coffee-id")))
            } else throw IOException("Connection lost after sending")
        }
        repository.addTransaction(transaction)
        repository.syncPendingChanges()
        assertEquals("Coffee", repository.getAllTransactions().first().single().info)
        assertEquals(1, repository.getPendingChanges().first().size)
    }

    @Test fun `saving schedules durable work before a slow network call finishes`() = runBlocking {
        val releaseNetwork = CountDownLatch(1)
        response = { _, _ ->
            releaseNetwork.await(10, TimeUnit.SECONDS)
            throw IOException("No signal")
        }
        try {
            repository.addTransaction(transaction)
            val jobs = workManager.getWorkInfosForUniqueWork("pending-sheet-sync").get(5, TimeUnit.SECONDS)
            assertTrue("Saving must persist a background job before returning", jobs.isNotEmpty())
        } finally {
            releaseNetwork.countDown()
        }
    }


    @Test fun `editing an offline addition queues an update instead of another add`() = runBlocking {
        val changes = editPendingAddition("Current")
        assertEquals(listOf(PendingKind.ADD, PendingKind.UPDATE), changes.map { it.kind })
        assertEquals("Lunch", repository.getAllTransactions().first().single().info)
        assertEquals("coffee-id", changes.last().transaction.id)
    }

    @Test fun `moving an offline addition removes the original account entry`() = runBlocking {
        val changes = editPendingAddition("Savings")
        assertEquals(listOf(PendingKind.ADD, PendingKind.DELETE, PendingKind.ADD), changes.map { it.kind })
        assertEquals("Savings", repository.getAllTransactions().first().single().account)
        assertNotEquals("coffee-id", changes.last().transaction.id)
    }

    private suspend fun editPendingAddition(account: String): List<com.budgetr.app.data.model.PendingChange> {
        repository.addTransaction(transaction)
        val pending = repository.getAllTransactions().first().single()
        val budgets = BudgetRepositoryImpl(db.categoryBudgetDao(), db.recurringCostReviewDao(),
            db.goalCategoryLinkDao(), db.envelopeDao(), db.dailyTransferDao())
        val vm = TransactionsViewModel(repository, budgets, prefs,
            DailyTargetRepository(repository, budgets, prefs, PeriodReviewRepository(db, prefs)), CategoryRuleRepository(db.categoryRuleDao()), RuntimeEnvironment.getApplication(), SavedStateHandle())
        val store = ViewModelStore()
        store.put("transactions", vm)
        try {
            vm.showEditSheet(pending)
            vm.saveTransaction(pending.copy(info = "Lunch", account = account))
            withTimeout(5000) {
                while (vm.uiState.value.transactionToEdit != null && vm.uiState.value.addSaveCount == 0 && vm.uiState.value.error == null) {
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    delay(10)
                }
            }
            assertNull(vm.uiState.value.error)
            return repository.getPendingChanges().first()
        } finally {
            store.clear()
        }
    }

    @Test fun `edits without a stable ID are rejected before queueing`() = runBlocking {
        try {
            repository.updateTransaction(transaction.copy(id = null))
            fail("An offline edit must not rely on a stale row number")
        } catch (_: IllegalArgumentException) { }
        assertTrue(repository.getPendingChanges().first().isEmpty())
    }
}
