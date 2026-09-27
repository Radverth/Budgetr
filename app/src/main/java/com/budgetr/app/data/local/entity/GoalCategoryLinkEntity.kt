package com.budgetr.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Links a savings goal to a spend category, so underspending against that category's budget
 *  cap can be suggested as a contribution to the goal. Device-local only — the savings goal
 *  itself still lives in the Sheet; this is just a local pairing. */
@Entity(tableName = "goal_category_links")
data class GoalCategoryLinkEntity(
    @PrimaryKey val goalName: String,
    val category: String
)
