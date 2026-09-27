package com.budgetr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.budgetr.app.data.local.entity.GoalCategoryLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalCategoryLinkDao {
    @Query("SELECT * FROM goal_category_links")
    fun getAll(): Flow<List<GoalCategoryLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: GoalCategoryLinkEntity)

    @Query("DELETE FROM goal_category_links WHERE goalName = :goalName")
    suspend fun delete(goalName: String)
}
