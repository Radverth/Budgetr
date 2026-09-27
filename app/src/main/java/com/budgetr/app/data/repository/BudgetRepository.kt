package com.budgetr.app.data.repository

import com.budgetr.app.data.model.CategoryBudget
import com.budgetr.app.data.model.GoalCategoryLink
import com.budgetr.app.data.model.RecurringCostReview
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import kotlinx.coroutines.flow.Flow

/** Local-only companion to [SheetsRepository] for spending-control features (budget caps,
 *  subscription review, goal-category links) that don't need a place in the shared Sheet. */
interface BudgetRepository {
    fun getCategoryBudgets(): Flow<List<CategoryBudget>>
    suspend fun setCategoryBudget(category: TransactionCategory, limit: Double)
    suspend fun clearCategoryBudget(category: TransactionCategory)

    fun getRecurringCostReviews(): Flow<List<RecurringCostReview>>
    /** Upserts a tracker row per active fixed cost (preserving firstSeenDate/dismissedUntil for
     *  ones already known) and drops rows for fixed costs that are no longer active. */
    suspend fun syncRecurringCostTracking(activeFixedCosts: List<Transaction>)
    suspend fun dismissRecurringCostReview(account: String, info: String, forDays: Int)

    fun getGoalCategoryLinks(): Flow<List<GoalCategoryLink>>
    suspend fun setGoalCategoryLink(goalName: String, category: TransactionCategory)
    suspend fun clearGoalCategoryLink(goalName: String)
}
