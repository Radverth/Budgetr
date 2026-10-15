package com.budgetr.app.di

import android.content.Context
import androidx.room.Room
import com.budgetr.app.data.local.BudgetrDatabase
import com.budgetr.app.data.local.dao.AccountBalanceDao
import com.budgetr.app.data.local.dao.BalanceRolloverDao
import com.budgetr.app.data.local.dao.BillDueDayDao
import com.budgetr.app.data.local.dao.CategoryBudgetDao
import com.budgetr.app.data.local.dao.CategoryRuleDao
import com.budgetr.app.data.local.dao.DailyTransferDao
import com.budgetr.app.data.local.dao.DebtDao
import com.budgetr.app.data.local.dao.EnvelopeDao
import com.budgetr.app.data.local.dao.GoalCategoryLinkDao
import com.budgetr.app.data.local.dao.PendingChangeDao
import com.budgetr.app.data.local.dao.PeriodSummaryDao
import com.budgetr.app.data.local.dao.RecurringCostReviewDao
import com.budgetr.app.data.local.dao.SavingsGoalDao
import com.budgetr.app.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BudgetrDatabase =
        Room.databaseBuilder(context, BudgetrDatabase::class.java, "budgetr.db")
            // Real migrations keep device-only data (budget caps, reviews) across updates;
            // the destructive fallback only covers versions older than these.
            .addMigrations(BudgetrDatabase.MIGRATION_6_7, BudgetrDatabase.MIGRATION_7_8, BudgetrDatabase.MIGRATION_8_9, BudgetrDatabase.MIGRATION_9_10, BudgetrDatabase.MIGRATION_10_11, BudgetrDatabase.MIGRATION_11_12, BudgetrDatabase.MIGRATION_12_13, BudgetrDatabase.MIGRATION_13_14)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTransactionDao(db: BudgetrDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideAccountBalanceDao(db: BudgetrDatabase): AccountBalanceDao = db.accountBalanceDao()

    @Provides
    fun provideBalanceRolloverDao(db: BudgetrDatabase): BalanceRolloverDao = db.balanceRolloverDao()

    @Provides
    fun provideSavingsGoalDao(db: BudgetrDatabase): SavingsGoalDao = db.savingsGoalDao()

    @Provides
    fun provideDebtDao(db: BudgetrDatabase): DebtDao = db.debtDao()

    @Provides
    fun provideCategoryBudgetDao(db: BudgetrDatabase): CategoryBudgetDao = db.categoryBudgetDao()

    @Provides
    fun provideRecurringCostReviewDao(db: BudgetrDatabase): RecurringCostReviewDao = db.recurringCostReviewDao()

    @Provides
    fun provideGoalCategoryLinkDao(db: BudgetrDatabase): GoalCategoryLinkDao = db.goalCategoryLinkDao()

    @Provides
    fun provideEnvelopeDao(db: BudgetrDatabase): EnvelopeDao = db.envelopeDao()

    @Provides
    fun providePeriodSummaryDao(db: BudgetrDatabase): PeriodSummaryDao = db.periodSummaryDao()

    @Provides
    fun provideDailyTransferDao(db: BudgetrDatabase): DailyTransferDao = db.dailyTransferDao()

    @Provides
    fun providePendingChangeDao(db: BudgetrDatabase): PendingChangeDao = db.pendingChangeDao()

    @Provides
    fun provideCategoryRuleDao(db: BudgetrDatabase): CategoryRuleDao = db.categoryRuleDao()

    @Provides
    fun provideBillDueDayDao(db: BudgetrDatabase): BillDueDayDao = db.billDueDayDao()
}
