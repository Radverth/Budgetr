package com.budgetr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.budgetr.app.data.local.entity.DebtEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY rowIndex ASC")
    fun getAll(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts ORDER BY rowIndex ASC")
    suspend fun getAllSync(): List<DebtEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(debts: List<DebtEntity>)

    @Query("DELETE FROM debts")
    suspend fun deleteAll()
}
