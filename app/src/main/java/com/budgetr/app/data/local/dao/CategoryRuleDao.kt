package com.budgetr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.budgetr.app.data.local.entity.CategoryRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules ORDER BY pattern COLLATE NOCASE")
    fun getAll(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules ORDER BY pattern COLLATE NOCASE")
    suspend fun getAllSync(): List<CategoryRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryRuleEntity)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM category_rules")
    suspend fun deleteAll()
}
