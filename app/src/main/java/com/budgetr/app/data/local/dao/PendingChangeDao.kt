package com.budgetr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.budgetr.app.data.local.entity.PendingChangeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingChangeDao {
    @Query("SELECT * FROM pending_changes ORDER BY id")
    fun getAll(): Flow<List<PendingChangeEntity>>

    @Query("SELECT * FROM pending_changes ORDER BY id")
    suspend fun getAllSync(): List<PendingChangeEntity>

    @Insert
    suspend fun insert(change: PendingChangeEntity): Long

    @Query("DELETE FROM pending_changes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_changes SET failedReason = :reason WHERE id = :id")
    suspend fun markFailed(id: Long, reason: String?)
}
