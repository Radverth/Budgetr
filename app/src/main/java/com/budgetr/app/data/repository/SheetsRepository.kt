package com.budgetr.app.data.repository

import com.budgetr.app.data.api.DriveFile
import com.budgetr.app.data.model.AccountBalance
import com.budgetr.app.data.model.BalanceRollover
import com.budgetr.app.data.model.Debt
import com.budgetr.app.data.model.SavingsGoal
import com.budgetr.app.data.model.Transaction
import kotlinx.coroutines.flow.Flow

interface SheetsRepository {
    fun getTransactions(account: String): Flow<List<Transaction>>
    /** All transactions across every account, for dashboards that need a combined view. */
    fun getAllTransactions(): Flow<List<Transaction>>
    fun getAccountBalances(): Flow<List<AccountBalance>>
    fun getBalanceRollovers(): Flow<List<BalanceRollover>>
    suspend fun refreshTransactions(account: String)
    /** Refreshes transactions for every account currently known locally. */
    suspend fun refreshAllTransactions()
    suspend fun refreshAccountBalances()
    suspend fun addTransaction(transaction: Transaction)
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(transaction: Transaction)
    suspend fun deleteOneOffTransactions(account: String)
    suspend fun listSpreadsheets(): List<DriveFile>
    suspend fun createSpreadsheetTemplate(name: String): String
    suspend fun renameAccount(oldName: String, newName: String)
    suspend fun addAccount(accountName: String)
    suspend fun recordRollover(account: String, amount: Double, date: String)
    suspend fun deleteRollover(account: String)
    suspend fun deleteAccount(accountName: String)
    /** Checks if a new pay period has started and, if so, deletes all one-off cost transactions. Returns true if a reset was performed. */
    suspend fun checkAndProcessNewPayPeriod(): Boolean

    // --- Savings goals ---
    fun getSavingsGoals(): Flow<List<SavingsGoal>>
    suspend fun refreshSavingsGoals()
    suspend fun addSavingsGoal(goal: SavingsGoal)
    suspend fun updateSavingsGoal(goal: SavingsGoal)
    suspend fun deleteSavingsGoal(name: String)

    // --- Debts ---
    fun getDebts(): Flow<List<Debt>>
    suspend fun refreshDebts()
    suspend fun addDebt(debt: Debt)
    suspend fun updateDebt(debt: Debt)
    suspend fun deleteDebt(name: String)
}
