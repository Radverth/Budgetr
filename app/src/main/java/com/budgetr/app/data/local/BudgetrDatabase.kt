package com.budgetr.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.budgetr.app.data.local.dao.AccountBalanceDao
import com.budgetr.app.data.local.dao.BalanceRolloverDao
import com.budgetr.app.data.local.dao.DebtDao
import com.budgetr.app.data.local.dao.SavingsGoalDao
import com.budgetr.app.data.local.dao.TransactionDao
import com.budgetr.app.data.local.entity.AccountBalanceEntity
import com.budgetr.app.data.local.entity.BalanceRolloverEntity
import com.budgetr.app.data.local.entity.DebtEntity
import com.budgetr.app.data.local.entity.SavingsGoalEntity
import com.budgetr.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        AccountBalanceEntity::class,
        BalanceRolloverEntity::class,
        SavingsGoalEntity::class,
        DebtEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class BudgetrDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountBalanceDao(): AccountBalanceDao
    abstract fun balanceRolloverDao(): BalanceRolloverDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun debtDao(): DebtDao
}
