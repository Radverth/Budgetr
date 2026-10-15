package com.budgetr.app.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.budgetr.app.data.local.BudgetrDatabase
import com.budgetr.app.data.local.entity.EnvelopeEntity
import com.budgetr.app.data.model.*
import com.budgetr.app.util.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PeriodReviewRepositoryTest {
    private lateinit var db: BudgetrDatabase
    private lateinit var prefs: PreferencesManager
    private lateinit var reviews: PeriodReviewRepository
    private fun date(value: String) = SimpleDateFormat("dd/MM/yyyy", Locale.UK).parse(value)!!
    private val period get() = PayPeriod(date("01/10/2026"), date("01/11/2026"))
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        prefs = PreferencesManager(context.getSharedPreferences("review-tests", Context.MODE_PRIVATE))
        prefs.clearAll()
        prefs.setSpreadsheetId("sheet-a")
        db = Room.inMemoryDatabaseBuilder(context, BudgetrDatabase::class.java).allowMainThreadQueries().build()
        reviews = PeriodReviewRepository(db, prefs)
    }
    @After fun close() { db.close() }
    @Test fun `starting next plan preserves previous plan across repository restart`() = runBlocking {
        reviews.savePlan(ReviewPlan("01/10/2026", 100.0))
        reviews.savePlan(ReviewPlan("01/11/2026", 200.0))
        val restarted = PeriodReviewRepository(db, prefs)
        val saved = restarted.captureBeforeRollover(period, emptyList(), mapOf("Bank" to 50.0))
        assertEquals(100.0, saved.review.planned!!, 0.001)
        assertFalse(saved.complete)
        assertTrue(restarted.observeReviews().first().isEmpty())
    }
    @Test fun `snapshot and rollover amounts survive partial rollover retry`() = runBlocking {
        db.envelopeDao().upsert(EnvelopeEntity("Food", 10.0, true, 5.0, null))
        val first = reviews.captureBeforeRollover(period, emptyList(), mapOf("Bank" to 50.0))
        db.envelopeDao().upsert(EnvelopeEntity("Food", 999.0, true, 999.0, "01/11/2026"))
        val second = reviews.captureBeforeRollover(period, emptyList(), mapOf("Bank" to 999.0))
        assertEquals(first, second)
        assertEquals(1500L, second.review.reservedCarryPence)
        reviews.completeRollover("01/10/2026")
        assertEquals(1, reviews.observeReviews().first().size)
    }
    @Test fun `closed plan cannot be overwritten`() = runBlocking {
        reviews.savePlan(ReviewPlan("01/10/2026", 100.0))
        reviews.captureBeforeRollover(period, emptyList(), mapOf("Bank" to 50.0))
        reviews.savePlan(ReviewPlan("01/10/2026", 999.0))
        assertEquals(100.0, reviews.get("01/10/2026")!!.review.planned!!, 0.001)
    }
    @Test fun `new installation and skipped months have no fabricated comparisons`() = runBlocking {
        assertTrue(reviews.observeReviews().first().isEmpty())
        reviews.savePlan(ReviewPlan("01/10/2026", 100.0))
        val skipped = PayPeriod(date("01/10/2026"), date("01/01/2027"))
        val result = reviews.captureBeforeRollover(skipped, emptyList(), mapOf("Bank" to 50.0))
        assertNull(result.review.planned)
    }
    @Test fun `reviews do not leak between spreadsheets`() = runBlocking {
        reviews.captureBeforeRollover(period, emptyList(), mapOf("Bank" to 50.0))
        reviews.completeRollover("01/10/2026")
        prefs.setSpreadsheetId("sheet-b")
        assertTrue(reviews.observeReviews().first().isEmpty())
        assertNull(reviews.get("01/10/2026"))
        prefs.setSpreadsheetId("sheet-a")
        assertEquals(1, reviews.observeReviews().first().size)
    }
    @Test fun `migration adds reviews without changing existing envelopes or transactions`() = runBlocking {
        db.envelopeDao().upsert(EnvelopeEntity("Food", 25.0, true, 10.0, null))
        val sql = db.openHelper.writableDatabase
        sql.execSQL("DROP TABLE period_reviews")
        sql.execSQL("INSERT INTO transactions (rowIndex, date, info, amount, category, account) VALUES (2, '01/10/2026', 'Cost', -5, 'ONE_OFF_COST', 'Bank')")
        BudgetrDatabase.MIGRATION_14_15.migrate(sql)
        assertEquals(25.0, db.envelopeDao().getAllSync().single().limitAmount, 0.001)
        assertEquals(-5.0, db.transactionDao().getAllSync().single().amount, 0.001)
        assertTrue(db.periodReviewDao().all("sheet-a").isEmpty())
    }
    @Test fun `new period spending is excluded from closing snapshot`() = runBlocking {
        val txs = listOf(Transaction(2, "01/11/2026", "New cost", -25.0, TransactionCategory.ONE_OFF_COST, "Bank"))
        assertEquals(0.0, reviews.captureBeforeRollover(period, txs, mapOf("Bank" to 50.0)).review.spending, 0.001)
    }
}
