package com.budgetr.app.widget

import com.budgetr.app.data.local.dao.AccountBalanceDao
import com.budgetr.app.data.local.dao.BalanceRolloverDao
import com.budgetr.app.data.local.dao.TransactionDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun accountBalanceDao(): AccountBalanceDao
    fun transactionDao(): TransactionDao
    fun balanceRolloverDao(): BalanceRolloverDao
}
