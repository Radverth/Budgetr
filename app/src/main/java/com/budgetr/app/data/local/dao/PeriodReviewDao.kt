package com.budgetr.app.data.local.dao

import androidx.room.*
import com.budgetr.app.data.local.entity.PeriodReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodReviewDao {
    @Query("SELECT * FROM period_reviews WHERE spreadsheetId = :sheet")
    fun observe(sheet: String): Flow<List<PeriodReviewEntity>>
    @Query("SELECT * FROM period_reviews WHERE spreadsheetId = :sheet")
    suspend fun all(sheet: String): List<PeriodReviewEntity>
    @Query("SELECT * FROM period_reviews WHERE spreadsheetId = :sheet AND periodStart = :start")
    suspend fun get(sheet: String, start: String): PeriodReviewEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: PeriodReviewEntity)
}
