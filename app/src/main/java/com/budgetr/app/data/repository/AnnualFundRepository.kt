package com.budgetr.app.data.repository

import com.budgetr.app.data.local.dao.AnnualFundDao
import com.budgetr.app.data.local.entity.AnnualFundEntity
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.util.AnnualFund
import com.budgetr.app.util.AnnualFundCalculator
import com.budgetr.app.util.PayPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Funds saving up for yearly bills, kept on the phone; see [AnnualFundCalculator]. */
@Singleton
class AnnualFundRepository @Inject constructor(private val dao: AnnualFundDao) {

    private val mutex = Mutex()

    fun getFunds(): Flow<List<AnnualFund>> = dao.getAll().map { rows -> rows.map { it.toFund() } }

    /** Starts saving up for [bill] from this payday, or stops (its saved money becomes free to spend again). */
    suspend fun setSaving(bill: Transaction, saving: Boolean, period: PayPeriod) = mutex.withLock {
        val key = AnnualFundCalculator.key(bill.info)
        if (!saving) {
            dao.delete(bill.account, key)
            return@withLock
        }
        if (dao.getAllSync().any { it.account == bill.account && it.infoKey == key }) return@withLock
        val dueMonth = bill.activeMonths?.singleOrNull() ?: return@withLock
        val fund = AnnualFundCalculator.process(AnnualFund(bill.account, key), abs(bill.amount), dueMonth, period)
        dao.upsert(fund.toEntity())
    }

    /** Sets aside (and releases) each fund's share for [period], once. Funds whose bill is gone are left alone. */
    suspend fun processPeriod(transactions: List<Transaction>, period: PayPeriod) = mutex.withLock {
        val bills = AnnualFundCalculator.yearlyBills(transactions)
        dao.getAllSync().forEach { row ->
            val bill = bills.firstOrNull { it.account == row.account && AnnualFundCalculator.key(it.info) == row.infoKey } ?: return@forEach
            val fund = row.toFund()
            val next = AnnualFundCalculator.process(fund, abs(bill.amount), bill.activeMonths!!.single(), period)
            if (next != fund) dao.upsert(next.toEntity())
        }
    }

    private fun AnnualFundEntity.toFund() = AnnualFund(account, infoKey, saved, processedPeriod, lastSetAside, lastReleased)

    private fun AnnualFund.toEntity() =
        AnnualFundEntity(account, AnnualFundCalculator.key(info), saved, processedPeriod, lastSetAside, lastReleased)
}
