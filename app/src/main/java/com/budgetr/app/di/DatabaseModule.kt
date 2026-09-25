package com.budgetr.app.di

import android.content.Context
import androidx.room.Room
import com.budgetr.app.data.local.BudgetrDatabase
import com.budgetr.app.data.local.dao.AccountBalanceDao
import com.budgetr.app.data.local.dao.BalanceRolloverDao
import com.budgetr.app.data.local.dao.DebtDao
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
}
