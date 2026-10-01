package com.budgetr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.budgetr.app.data.local.entity.PeriodSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodSummaryDao {
    @Query("SELECT * FROM period_summaries")
    fun getAll(): Flow<List<PeriodSummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(summaries: List<PeriodSummaryEntity>)

    @Query("DELETE FROM period_summaries")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(summaries: List<PeriodSummaryEntity>) {
        deleteAll()
        insertAll(summaries)
    }
}
