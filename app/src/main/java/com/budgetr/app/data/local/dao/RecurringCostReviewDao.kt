package com.budgetr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.budgetr.app.data.local.entity.RecurringCostReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringCostReviewDao {
    @Query("SELECT * FROM recurring_cost_reviews")
    fun getAll(): Flow<List<RecurringCostReviewEntity>>

    @Query("SELECT * FROM recurring_cost_reviews")
    suspend fun getAllSync(): List<RecurringCostReviewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecurringCostReviewEntity)

    @Query("DELETE FROM recurring_cost_reviews WHERE account = :account AND info = :info")
    suspend fun delete(account: String, info: String)
}
