package com.budgetr.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.budgetr.app.data.local.dao.AccountBalanceDao
import com.budgetr.app.data.local.dao.BalanceRolloverDao
import com.budgetr.app.data.local.dao.CategoryBudgetDao
import com.budgetr.app.data.local.dao.DebtDao
import com.budgetr.app.data.local.dao.GoalCategoryLinkDao
import com.budgetr.app.data.local.dao.RecurringCostReviewDao
import com.budgetr.app.data.local.dao.SavingsGoalDao
import com.budgetr.app.data.local.dao.TransactionDao
import com.budgetr.app.data.local.entity.AccountBalanceEntity
import com.budgetr.app.data.local.entity.BalanceRolloverEntity
import com.budgetr.app.data.local.entity.CategoryBudgetEntity
import com.budgetr.app.data.local.entity.DebtEntity
import com.budgetr.app.data.local.entity.GoalCategoryLinkEntity
import com.budgetr.app.data.local.entity.RecurringCostReviewEntity
import com.budgetr.app.data.local.entity.SavingsGoalEntity
import com.budgetr.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        AccountBalanceEntity::class,
        BalanceRolloverEntity::class,
        SavingsGoalEntity::class,
        DebtEntity::class,
        CategoryBudgetEntity::class,
        RecurringCostReviewEntity::class,
        GoalCategoryLinkEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class BudgetrDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountBalanceDao(): AccountBalanceDao
    abstract fun balanceRolloverDao(): BalanceRolloverDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun debtDao(): DebtDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun recurringCostReviewDao(): RecurringCostReviewDao
    abstract fun goalCategoryLinkDao(): GoalCategoryLinkDao

    companion object {
        /** Adds the optional spending category to cached transactions. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN tag TEXT")
            }
        }
    }
}
