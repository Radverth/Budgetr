package com.budgetr.app.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.budgetr.app.data.backup.BackupRepository
import com.budgetr.app.data.local.BudgetrDatabase
import com.budgetr.app.data.local.entity.*
import com.budgetr.app.data.model.*
import com.budgetr.app.util.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.lang.reflect.Proxy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReviewAllocationRepositoryTest {
    private lateinit var db: BudgetrDatabase
    private lateinit var prefs: PreferencesManager
    private lateinit var reviews: PeriodReviewRepository
    private lateinit var sheets: SheetsRepository
    private lateinit var backup: BackupRepository
    private lateinit var allocations: ReviewAllocationRepository
    private lateinit var start: String
    private var saved = 10000L
    private var loseResponse = false
    private var goalExists = true
    private var backupJson: String? = null
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        prefs = PreferencesManager(context.getSharedPreferences("allocation-tests", Context.MODE_PRIVATE))
        prefs.clearAll()
        prefs.setSpreadsheetId("sheet")
        prefs.setPayDay(1)
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
        val current = PayPeriodCalculator.current(1)
        val previous = PayPeriodCalculator.current(1, HistoryCalculator.dayBefore(current.start))
        start = fmt.format(previous.start)
        prefs.setLastPayPeriodStart(fmt.format(current.start))
        db = Room.inMemoryDatabaseBuilder(context, BudgetrDatabase::class.java).allowMainThreadQueries().build()
        reviews = PeriodReviewRepository(db, prefs)
        reviews.captureBeforeRollover(previous, emptyList(), mapOf("Bank" to 100.0))
        reviews.completeRollover(start)
        db.balanceRolloverDao().insertOrReplace(BalanceRolloverEntity("Bank", 100.0, fmt.format(current.start)))
        db.accountBalanceDao().insertAll(listOf(AccountBalanceEntity("Bank", 500.0, null, null, null, null)))
        sheets = Proxy.newProxyInstance(SheetsRepository::class.java.classLoader, arrayOf(SheetsRepository::class.java)) { _, method, args ->
            try {
                when (method.name) {
                    "getPendingChanges" -> flowOf(emptyList<PendingChange>())
                    "getAllTransactions" -> flowOf(emptyList<Transaction>())
                    "getSavingsGoals" -> flowOf(if (goalExists) listOf(SavingsGoal("Holiday", 1000.0, saved / 100.0, null, 2)) else emptyList())
                    "refreshSavingsGoals", "refreshAccountBalances", "refreshAllTransactions" -> Unit
                    "readPhoneBackup" -> backupJson
                    "writePhoneBackup" -> { backupJson = args!![0] as String; Unit }
                    "contributeReviewGoal" -> {
                        if (!goalExists) throw IOException("Goal no longer exists")
                        val expected = args!![1] as Long
                        val desired = args[2] as Long
                        if (saved != expected && saved != desired) throw IOException("Goal changed")
                        saved = desired
                        if (loseResponse) { loseResponse = false; throw IOException("Response lost") }
                        Unit
                    }
                    else -> error(method.name)
                }
            } catch (e: IOException) {
                @Suppress("UNCHECKED_CAST")
                (args!!.last() as Continuation<Any?>).resumeWith(Result.failure(e))
                COROUTINE_SUSPENDED
            }
        } as SheetsRepository
        val budgets = BudgetRepositoryImpl(db.categoryBudgetDao(), db.recurringCostReviewDao(), db.goalCategoryLinkDao(), db.envelopeDao(), db.dailyTransferDao())
        val daily = DailyTargetRepository(sheets, budgets, prefs, reviews)
        backup = BackupRepository(sheets, db.categoryBudgetDao(), db.envelopeDao(), db.goalCategoryLinkDao(), db.recurringCostReviewDao(), db.dailyTransferDao(), db.balanceRolloverDao(), db.categoryRuleDao(), db.billDueDayDao(), daily, prefs, context, db)
        allocations = ReviewAllocationRepository(db, reviews, sheets, prefs, backup)
    }
    @After fun close() { db.close() }
    @Test fun `keep next period records choice without adding money`() = runBlocking {
        allocations.allocate(start, ReviewDestination.NextPeriod, 0)
        allocations.allocate(start, ReviewDestination.NextPeriod, 0)
        assertEquals(100.0, db.balanceRolloverDao().getAllSync().single().rolloverAmount, 0.001)
        assertTrue(reviews.receipt(start)!!.complete)
    }
    @Test fun `category allocation adds carry once and caps eligible leftover`() = runBlocking {
        db.envelopeDao().upsert(EnvelopeEntity("Food", 20.0, true, 10.0, prefs.getLastPayPeriodStart()))
        assertEquals(7000L, allocations.available(start))
        allocations.allocate(start, ReviewDestination.Category("Food"), 2500)
        allocations.allocate(start, ReviewDestination.Category("Food"), 2500)
        assertEquals(35.0, db.envelopeDao().getAllSync().single().carriedOver, 0.001)
        assertEquals(0L, allocations.available(start))
    }
    @Test fun `lost goal response locks intent then resumes without a second contribution`() = runBlocking {
        loseResponse = true
        assertTrue(runCatching { allocations.allocate(start, ReviewDestination.Goal("Holiday"), 2500) }.isFailure)
        assertEquals(12500L, saved)
        assertFalse(reviews.receipt(start)!!.complete)
        assertTrue(runCatching { allocations.allocate(start, ReviewDestination.NextPeriod, 0) }.isFailure)
        val restarted = ReviewAllocationRepository(db, PeriodReviewRepository(db, prefs), sheets, prefs, backup)
        restarted.allocate(start, ReviewDestination.Goal("Holiday"), 2500)
        assertEquals(12500L, saved)
        assertEquals(75.0, db.balanceRolloverDao().getAllSync().single().rolloverAmount, 0.001)
        assertTrue(reviews.receipt(start)!!.complete)
    }
    @Test fun `changed goal balance keeps ambiguous intent locked`() = runBlocking {
        loseResponse = true
        runCatching { allocations.allocate(start, ReviewDestination.Goal("Holiday"), 2500) }
        saved = 15000
        assertTrue(runCatching { allocations.allocate(start, ReviewDestination.Goal("Holiday"), 2500) }.isFailure)
        assertEquals(15000L, saved)
        assertFalse(reviews.receipt(start)!!.complete)
    }
    @Test fun `deleted goal unavailable cash and stale period cannot allocate`() = runBlocking {
        goalExists = false
        assertTrue(runCatching { allocations.allocate(start, ReviewDestination.Goal("Holiday"), 2500) }.isFailure)
        assertNull(reviews.receipt(start))
        db.balanceRolloverDao().insertOrReplace(BalanceRolloverEntity("Bank", 1.0, prefs.getLastPayPeriodStart()!!))
        db.envelopeDao().upsert(EnvelopeEntity("Food", 20.0, true, 0.0, null))
        assertTrue(runCatching { allocations.allocate(start, ReviewDestination.Category("Food"), 2500) }.isFailure)
        prefs.setLastPayPeriodStart("01/01/2000")
        assertTrue(runCatching { allocations.allocate(start, ReviewDestination.NextPeriod, 0) }.isFailure)
        assertNull(reviews.receipt(start))
    }
    @Test fun `restoring backed up pending intent reconciles remote outcome once`() = runBlocking {
        loseResponse = true
        runCatching { allocations.allocate(start, ReviewDestination.Goal("Holiday"), 2500) }
        assertNotNull(backupJson)
        assertEquals(com.budgetr.app.data.backup.RestoreResult.RESTORED, backup.restore())
        allocations.allocate(start, ReviewDestination.Goal("Holiday"), 2500)
        assertEquals(12500L, saved)
        assertEquals(75.0, db.balanceRolloverDao().getAllSync().single().rolloverAmount, 0.001)
    }
}
