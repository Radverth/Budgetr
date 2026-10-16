package com.budgetr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.budgetr.app.data.local.entity.AnnualFundEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnualFundDao {
    @Query("SELECT * FROM annual_funds")
    fun getAll(): Flow<List<AnnualFundEntity>>

    @Query("SELECT * FROM annual_funds")
    suspend fun getAllSync(): List<AnnualFundEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AnnualFundEntity)

    @Query("DELETE FROM annual_funds WHERE account = :account AND infoKey = :infoKey")
    suspend fun delete(account: String, infoKey: String)

    @Query("DELETE FROM annual_funds")
    suspend fun deleteAll()
}
