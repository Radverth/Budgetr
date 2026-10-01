package com.budgetr.app.data.repository

import com.budgetr.app.data.local.dao.CategoryBudgetDao
import com.budgetr.app.data.local.dao.EnvelopeDao
import com.budgetr.app.data.local.dao.GoalCategoryLinkDao
import com.budgetr.app.data.local.dao.RecurringCostReviewDao
import com.budgetr.app.data.local.entity.CategoryBudgetEntity
import com.budgetr.app.data.local.entity.EnvelopeEntity
import com.budgetr.app.data.local.entity.GoalCategoryLinkEntity
import com.budgetr.app.data.local.entity.RecurringCostReviewEntity
import com.budgetr.app.data.model.CategoryBudget
import com.budgetr.app.data.model.Envelope
import com.budgetr.app.data.model.GoalCategoryLink
import com.budgetr.app.data.model.RecurringCostReview
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val categoryBudgetDao: CategoryBudgetDao,
    private val recurringCostReviewDao: RecurringCostReviewDao,
    private val goalCategoryLinkDao: GoalCategoryLinkDao,
    private val envelopeDao: EnvelopeDao
) : BudgetRepository {

    override fun getCategoryBudgets(): Flow<List<CategoryBudget>> =
        categoryBudgetDao.getAll().map { entities ->
            entities.map { CategoryBudget(TransactionCategory.fromString(it.category), it.limit) }
        }

    override suspend fun setCategoryBudget(category: TransactionCategory, limit: Double) {
        categoryBudgetDao.upsert(CategoryBudgetEntity(category = category.name, limit = limit))
    }

    override suspend fun clearCategoryBudget(category: TransactionCategory) {
        categoryBudgetDao.delete(category.name)
    }

    override fun getRecurringCostReviews(): Flow<List<RecurringCostReview>> =
        recurringCostReviewDao.getAll().map { entities -> entities.map { it.toModel() } }

    override suspend fun syncRecurringCostTracking(activeFixedCosts: List<Transaction>) {
        val existing = recurringCostReviewDao.getAllSync().associateBy { it.account to it.info }
        val activeKeys = activeFixedCosts.map { it.account to it.info }.toSet()

        activeFixedCosts.forEach { tx ->
            val current = existing[tx.account to tx.info]
            recurringCostReviewDao.upsert(
                RecurringCostReviewEntity(
                    account = tx.account,
                    info = tx.info,
                    firstSeenDate = current?.firstSeenDate ?: System.currentTimeMillis(),
                    lastAmount = tx.amount,
                    dismissedUntil = current?.dismissedUntil
                )
            )
        }

        existing.keys.filter { it !in activeKeys }.forEach { (account, info) ->
            recurringCostReviewDao.delete(account, info)
        }
    }

    override suspend fun dismissRecurringCostReview(account: String, info: String, forDays: Int) {
        val existing = recurringCostReviewDao.getAllSync().find { it.account == account && it.info == info } ?: return
        val dismissedUntil = System.currentTimeMillis() + forDays * 24L * 60 * 60 * 1000
        recurringCostReviewDao.upsert(existing.copy(dismissedUntil = dismissedUntil))
    }

    override fun getGoalCategoryLinks(): Flow<List<GoalCategoryLink>> =
        goalCategoryLinkDao.getAll().map { entities ->
            entities.map { GoalCategoryLink(it.goalName, TransactionCategory.fromString(it.category)) }
        }

    override suspend fun setGoalCategoryLink(goalName: String, category: TransactionCategory) {
        goalCategoryLinkDao.upsert(GoalCategoryLinkEntity(goalName = goalName, category = category.name))
    }

    override suspend fun clearGoalCategoryLink(goalName: String) {
        goalCategoryLinkDao.delete(goalName)
    }

    override fun getEnvelopes(): Flow<List<Envelope>> =
        envelopeDao.getAll().map { entities -> entities.map { it.toModel() } }

    override suspend fun setEnvelope(tag: String, limit: Double, rollover: Boolean, renameFrom: String?) {
        val source = renameFrom ?: tag
        val existing = envelopeDao.getAllSync().find { it.tag.equals(source, ignoreCase = true) }
        if (existing != null && existing.tag != tag) envelopeDao.delete(existing.tag)
        envelopeDao.upsert(
            EnvelopeEntity(
                tag = tag,
                limitAmount = limit,
                rollover = rollover,
                // Turning rollover off drops what was carried, so the envelope shows just its limit
                carriedOver = if (rollover) existing?.carriedOver ?: 0.0 else 0.0,
                carriedForPeriod = existing?.carriedForPeriod
            )
        )
    }

    override suspend fun deleteEnvelope(tag: String) {
        envelopeDao.delete(tag)
    }
}

private fun EnvelopeEntity.toModel() = Envelope(
    tag = tag,
    limit = limitAmount,
    rollover = rollover,
    carriedOver = carriedOver
)

private fun RecurringCostReviewEntity.toModel() = RecurringCostReview(
    account = account,
    info = info,
    firstSeenDate = firstSeenDate,
    lastAmount = lastAmount,
    dismissedUntil = dismissedUntil
)
