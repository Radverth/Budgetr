package com.budgetr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.budgetr.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE account = :account ORDER BY rowIndex DESC")
    fun getTransactionsByAccount(account: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE account = :account ORDER BY rowIndex DESC")
    suspend fun getTransactionsByAccountSync(account: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY rowIndex DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY rowIndex DESC")
    suspend fun getAllSync(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE account = :account")
    suspend fun deleteByAccount(account: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    /**
     * Atomically replaces all cached rows for an account. Because the primary key is an
     * auto-generated id (not rowIndex), a bare deleteByAccount + insertAll pair run from two
     * concurrent refreshes can interleave and leave duplicate rowIndex rows, which then
     * crash the LazyColumn with a duplicate-key exception. Wrapping both in a single DB
     * transaction serialises them and keeps the cache free of duplicates.
     */
    @Transaction
    suspend fun replaceForAccount(account: String, transactions: List<TransactionEntity>) {
        deleteByAccount(account)
        insertAll(transactions)
    }
}
