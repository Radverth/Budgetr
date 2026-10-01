package com.budgetr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.budgetr.app.data.local.entity.EnvelopeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EnvelopeDao {
    @Query("SELECT * FROM envelopes ORDER BY tag COLLATE NOCASE")
    fun getAll(): Flow<List<EnvelopeEntity>>

    @Query("SELECT * FROM envelopes")
    suspend fun getAllSync(): List<EnvelopeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(envelope: EnvelopeEntity)

    @Query("DELETE FROM envelopes WHERE tag = :tag")
    suspend fun delete(tag: String)
}
