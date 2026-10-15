package com.budgetr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.budgetr.app.data.local.entity.BillDueDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDueDayDao {
    @Query("SELECT * FROM bill_due_days")
    fun getAll(): Flow<List<BillDueDayEntity>>

    @Query("SELECT * FROM bill_due_days")
    suspend fun getAllSync(): List<BillDueDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BillDueDayEntity)

    @Query("DELETE FROM bill_due_days WHERE account = :account AND infoKey = :infoKey")
    suspend fun delete(account: String, infoKey: String)

    @Query("DELETE FROM bill_due_days")
    suspend fun deleteAll()
}
