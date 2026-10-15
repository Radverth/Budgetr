package com.budgetr.app.data.repository

import com.budgetr.app.data.api.DriveFile
import com.budgetr.app.data.model.AccountBalance
import com.budgetr.app.data.model.BalanceRollover
import com.budgetr.app.data.model.Debt
import com.budgetr.app.data.model.PendingChange
import com.budgetr.app.data.model.PeriodSummary
import com.budgetr.app.data.model.SavingsGoal
import com.budgetr.app.data.model.Transaction
import kotlinx.coroutines.flow.Flow

/** What a sync run achieved. */
enum class SyncOutcome { NOTHING_TO_SEND, SYNCED, OFFLINE, SOME_FAILED }

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
    /** Assigns or clears spending tags in a single Sheets request, then refreshes the cache. */
    suspend fun assignSpendingCategory(transactions: List<Transaction>, tag: String?)
    suspend fun deleteTransaction(transaction: Transaction)

    // --- Phone backup (hidden tab) ---
    /** The backup JSON saved in the Sheet, or null if there's none yet. */
    suspend fun readPhoneBackup(): String?
    /** Saves the backup JSON to the hidden tab, creating it the first time. */
    suspend fun writePhoneBackup(json: String, savedAtText: String)

    // --- Offline saving: adds, edits and deletes are queued on the phone first ---
    /** Queued changes, including ones Google refused (they have a failedReason). */
    fun getPendingChanges(): Flow<List<PendingChange>>
    /** Sends the queue to the Sheet in order. Safe to call any time. */
    suspend fun syncPendingChanges(): SyncOutcome
    /** Tries a refused change again. */
    suspend fun retryPendingChange(id: Long)
    /** Drops a refused change; the Sheet keeps whatever it has. */
    suspend fun discardPendingChange(id: Long)
    suspend fun deleteOneOffTransactions(account: String)
    suspend fun listSpreadsheets(): List<DriveFile>
    /** Creates a new spreadsheet with a tab for each of [accounts]. */
    suspend fun createSpreadsheetTemplate(
        name: String,
        accounts: List<String> = listOf("Monzo", "Halifax Debit Card", "Halifax Credit Card")
    ): String
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

    // --- History (one summary row per finished pay period) ---
    fun getPeriodSummaries(): Flow<List<PeriodSummary>>
    suspend fun refreshPeriodSummaries()
}
