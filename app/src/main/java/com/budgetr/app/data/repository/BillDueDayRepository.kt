package com.budgetr.app.data.repository

import com.budgetr.app.data.local.dao.BillDueDayDao
import com.budgetr.app.data.local.entity.BillDueDayEntity
import com.budgetr.app.util.CashFlowCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** When each bill actually leaves the bank (day of the month), kept on the phone. */
@Singleton
class BillDueDayRepository @Inject constructor(private val dao: BillDueDayDao) {

    /** Due days by [CashFlowCalculator.billKey]. */
    fun getDueDays(): Flow<Map<String, Int>> = dao.getAll().map { rows ->
        rows.associate { CashFlowCalculator.billKey(it.account, it.infoKey) to it.dueDay }
    }

    /** Sets the due day for a bill, or clears it (back to payday) when [dueDay] is null. */
    suspend fun setDueDay(account: String, info: String, dueDay: Int?) {
        val key = info.trim().lowercase(Locale.ROOT)
        if (dueDay == null) dao.delete(account, key) else dao.upsert(BillDueDayEntity(account, key, dueDay.coerceIn(1, 31)))
    }
}
