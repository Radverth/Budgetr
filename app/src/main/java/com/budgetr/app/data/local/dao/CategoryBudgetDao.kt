package com.budgetr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.budgetr.app.data.local.entity.CategoryBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {
    @Query("SELECT * FROM category_budgets")
    fun getAll(): Flow<List<CategoryBudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: CategoryBudgetEntity)

    @Query("DELETE FROM category_budgets WHERE category = :category")
    suspend fun delete(category: String)
}
