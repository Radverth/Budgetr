package com.budgetr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.budgetr.app.data.local.entity.DailyTransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTransferDao {
    @Query("SELECT * FROM daily_transfers WHERE periodStart = :periodStart ORDER BY id")
    fun getForPeriod(periodStart: String): Flow<List<DailyTransferEntity>>

    @Query("SELECT * FROM daily_transfers WHERE periodStart = :periodStart ORDER BY id")
    suspend fun getForPeriodSync(periodStart: String): List<DailyTransferEntity>

    @Query("SELECT * FROM daily_transfers ORDER BY id")
    suspend fun getAllSync(): List<DailyTransferEntity>

    @Query("DELETE FROM daily_transfers")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(transfer: DailyTransferEntity)

    @Query("DELETE FROM daily_transfers WHERE id = :id")
    suspend fun delete(id: Long)

    /** Old periods' transfers no longer affect anything. */
    @Query("DELETE FROM daily_transfers WHERE periodStart != :periodStart")
    suspend fun deleteOtherPeriods(periodStart: String)
}
