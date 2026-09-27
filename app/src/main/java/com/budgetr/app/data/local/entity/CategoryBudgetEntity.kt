package com.budgetr.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A spending cap for a category, applied to the current pay period. Device-local only —
 *  not synced to the Google Sheet, so caps can be set without changing the shared template. */
@Entity(tableName = "category_budgets")
data class CategoryBudgetEntity(
    @PrimaryKey val category: String,
    val limit: Double
)
