package com.budgetr.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.budgetr.app.data.local.dao.AccountBalanceDao
import com.budgetr.app.data.local.dao.BalanceRolloverDao
import com.budgetr.app.data.local.dao.TransactionDao
import com.budgetr.app.data.local.entity.AccountBalanceEntity
import com.budgetr.app.data.local.entity.BalanceRolloverEntity
import com.budgetr.app.data.local.entity.TransactionEntity

@Database(
    entities = [TransactionEntity::class, AccountBalanceEntity::class, BalanceRolloverEntity::class],
    version = 4,
    exportSchema = false
)
abstract class BudgetrDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountBalanceDao(): AccountBalanceDao
    abstract fun balanceRolloverDao(): BalanceRolloverDao
}
