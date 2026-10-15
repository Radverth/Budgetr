package com.budgetr.app.data.repository

import android.content.Context
import java.io.IOException
import com.budgetr.app.data.local.dao.PendingChangeDao
import com.budgetr.app.data.local.entity.PendingChangeEntity
import com.budgetr.app.data.model.PendingChange
import com.budgetr.app.data.model.PendingKind
import com.budgetr.app.data.sync.PendingSyncWorker
import com.budgetr.app.widget.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import com.budgetr.app.data.api.AddSheetRequestBody
import com.budgetr.app.data.api.BatchUpdateValuesRequest
import com.budgetr.app.data.api.BatchUpdateRequest
import com.budgetr.app.data.api.CreateSpreadsheetRequest
import com.budgetr.app.data.api.DeleteDimensionRequest
import com.budgetr.app.data.api.DeleteSheetRequest
import com.budgetr.app.data.api.DimensionRange
import com.budgetr.app.data.api.DriveFile
import com.budgetr.app.data.api.GoogleDriveApi
import com.budgetr.app.data.api.GoogleSheetsApi
import com.budgetr.app.data.api.NewSheetProperties
import com.budgetr.app.data.api.Request
import com.budgetr.app.data.api.SheetSpec
import com.budgetr.app.data.api.SpreadsheetTitleProperties
import com.budgetr.app.data.api.UpdateSheetPropertiesRequest
import com.budgetr.app.data.api.UpdateSheetProps
import com.budgetr.app.data.api.ValueRange
import com.budgetr.app.data.local.dao.AccountBalanceDao
import com.budgetr.app.data.local.dao.BalanceRolloverDao
import com.budgetr.app.data.local.dao.DebtDao
import com.budgetr.app.data.local.dao.EnvelopeDao
import com.budgetr.app.data.local.dao.PeriodSummaryDao
import com.budgetr.app.data.local.dao.SavingsGoalDao
import com.budgetr.app.data.local.dao.TransactionDao
import com.budgetr.app.data.local.entity.AccountBalanceEntity
import com.budgetr.app.data.local.entity.BalanceRolloverEntity
import com.budgetr.app.data.local.entity.DebtEntity
import com.budgetr.app.data.local.entity.PeriodSummaryEntity
import com.budgetr.app.data.local.entity.SavingsGoalEntity
import com.budgetr.app.data.local.entity.TransactionEntity
import com.budgetr.app.data.model.AccountBalance
import com.budgetr.app.data.model.BalanceRollover
import com.budgetr.app.data.model.Debt
import com.budgetr.app.data.model.Envelope
import com.budgetr.app.data.model.PeriodSummary
import com.budgetr.app.data.model.SavingsGoal
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.util.EnvelopeCalculator
import com.budgetr.app.util.HistoryCalculator
import com.budgetr.app.util.PeriodSummaryRow
import com.budgetr.app.util.PayPeriod
import com.budgetr.app.util.PayPeriodCalculator
import com.budgetr.app.util.PreferencesManager
import com.budgetr.app.util.SpendTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import javax.inject.Inject

private const val SAVINGS_GOALS_SHEET = "Savings Goals"
private const val DEBTS_SHEET = "Debts"
private const val HISTORY_SHEET = "History"
private const val BACKUP_SHEET = "Budgetr backup"

class SheetsRepositoryImpl @Inject constructor(
    private val api: GoogleSheetsApi,
    private val driveApi: GoogleDriveApi,
    private val transactionDao: TransactionDao,
    private val accountBalanceDao: AccountBalanceDao,
    private val balanceRolloverDao: BalanceRolloverDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val debtDao: DebtDao,
    private val envelopeDao: EnvelopeDao,
    private val periodSummaryDao: PeriodSummaryDao,
    private val pendingChangeDao: PendingChangeDao,
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager
) : SheetsRepository {

    // Cache of sheet title -> numeric sheetId (gid) from the Sheets API
    private var sheetIdCache: Map<String, Int> = emptyMap()
    private val sheetIdMutex = Mutex()

    private val backfillMutex = Mutex()

    // One sync run at a time, so queued changes are never sent twice or out of order
    private val syncMutex = Mutex()

    // Outlives any one screen (the repository is a singleton), so a send finishes after the form closes
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Accounts whose column G header has been written this session (see ensureTagHeader)
    private val tagHeaderWritten = mutableSetOf<String>()

    private suspend fun resolveSheetIdByName(name: String): Int? = sheetIdMutex.withLock {
        sheetIdCache[name]?.let { return@withLock it }
        val spreadsheetId = prefs.getSpreadsheetId() ?: return@withLock null
        val metadata = api.getSpreadsheet(spreadsheetId)
        sheetIdCache = metadata.sheets
            ?.mapNotNull { it.properties }
            ?.associate { it.title to it.sheetId }
            ?: emptyMap()
        sheetIdCache[name]
    }

    // The cache mirrors the Sheet; queued changes are laid on top so they show straight away.
    override fun getTransactions(account: String): Flow<List<Transaction>> =
        combine(transactionDao.getTransactionsByAccount(account), pendingChangeDao.getAll()) { entities, pending ->
            // Guard against any duplicate rowIndex rows left in the cache by an older build:
            // distinct keeps the UI's per-row keys unique so the LazyColumn can't crash.
            val sheet = entities.map { it.toTransaction() }.distinctBy { it.rowIndex }
            PendingOverlay.apply(sheet, pending.filter { it.spreadsheetId == prefs.getSpreadsheetId() }.map { it.toModel() }.filter { it.transaction.account == account })
        }

    override fun getAllTransactions(): Flow<List<Transaction>> =
        combine(transactionDao.getAll(), pendingChangeDao.getAll()) { entities, pending ->
            val sheet = entities.map { it.toTransaction() }.distinctBy { it.account to it.rowIndex }
            PendingOverlay.apply(sheet, pending.filter { it.spreadsheetId == prefs.getSpreadsheetId() }.map { it.toModel() })
        }

    override fun getAccountBalances(): Flow<List<AccountBalance>> =
        accountBalanceDao.getAll().map { entities ->
            entities.map { it.toAccountBalance() }
        }

    override fun getBalanceRollovers(): Flow<List<BalanceRollover>> =
        balanceRolloverDao.getAll().map { entities ->
            entities.map { BalanceRollover(it.account, it.rolloverAmount, it.recordedDate) }
        }

    override suspend fun refreshTransactions(account: String) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        refreshTransactions(account, spreadsheetId)
    }

    private suspend fun refreshTransactions(account: String, spreadsheetId: String) {
        val range = "$account!A:H"
        val response = api.getValues(spreadsheetId, range)
        val rows = backfillRowIds(spreadsheetId, account, response.values.orEmpty())

        // First row is header — skip it
        val entities = rows.drop(1).mapIndexedNotNull { index, row ->
            if (row.isEmpty()) return@mapIndexedNotNull null
            TransactionEntity(
                rowIndex = index + 2, // +2: skip header, 1-based
                date = row.getOrElse(0) { "" },
                info = row.getOrElse(1) { "" },
                amount = row.getOrElse(2) { "0" }.replace("[£,]".toRegex(), "").toDoubleOrNull() ?: 0.0,
                category = TransactionCategory.fromString(row.getOrElse(3) { "" }).name,
                account = account,
                activeMonths = row.getOrElse(5) { "" }.ifBlank { null },
                tag = SpendTags.normalise(row.getOrElse(6) { "" }),
                rowId = row.getOrElse(RowIds.COLUMN_INDEX) { "" }.trim().ifBlank { null }
            )
        }

        // Atomic replace so concurrent refreshes of the same account can't interleave into
        // duplicate rowIndex rows (which crash the transactions list with a duplicate key).
        if (prefs.getSpreadsheetId() != spreadsheetId) throw IOException("The selected spreadsheet changed during sync")
        transactionDao.replaceForAccount(account, entities)
    }

    /**
     * Gives every row without an ID one, in a single write, and labels column H. Returns [rows]
     * with the new IDs filled in. If the write fails (e.g. offline) the rows are returned as they
     * were and get IDs on a later refresh; until then edits fall back to row numbers.
     */
    private suspend fun backfillRowIds(spreadsheetId: String, account: String, firstRead: List<List<String>>): List<List<String>> {
        if (!needsBackfill(firstRead)) return firstRead
        // Two refreshes of one account can run at once (Home and Transactions on launch). Re-read
        // inside the lock so only one of them writes IDs and both cache the same ones.
        return backfillMutex.withLock {
            val rows = api.getValues(spreadsheetId, "$account!A:H").values ?: firstRead
            if (!needsBackfill(rows)) return@withLock rows
            writeRowIds(spreadsheetId, account, rows)
        }
    }

    private fun needsBackfill(rows: List<List<String>>): Boolean =
        RowIds.rowsMissingIds(rows).isNotEmpty() ||
            (rows.isNotEmpty() && rows.first().getOrElse(RowIds.COLUMN_INDEX) { "" } != RowIds.HEADER)

    private suspend fun writeRowIds(spreadsheetId: String, account: String, rows: List<List<String>>): List<List<String>> {
        val missing = RowIds.rowsMissingIds(rows)
        val needsHeader = rows.isNotEmpty() && rows.first().getOrElse(RowIds.COLUMN_INDEX) { "" } != RowIds.HEADER
        val newIds = missing.associateWith { RowIds.newId() }
        val sheet = "'${account.replace("'", "''")}'"
        val data = buildList {
            if (needsHeader) add(ValueRange(range = "$sheet!H1", values = listOf(listOf(RowIds.HEADER))))
            newIds.forEach { (row, id) -> add(ValueRange(range = "$sheet!H$row", values = listOf(listOf(id)))) }
        }
        return try {
            api.batchUpdateValues(spreadsheetId, BatchUpdateValuesRequest(data))
            rows.mapIndexed { index, row ->
                val id = newIds[index + 1] ?: return@mapIndexed row
                // Pad short rows out to column H so the ID lands in the right place
                List(maxOf(row.size, RowIds.COLUMN_INDEX + 1)) { col ->
                    if (col == RowIds.COLUMN_INDEX) id else row.getOrElse(col) { "" }
                }
            }
        } catch (e: Exception) {
            rows
        }
    }

    /** Column H of [account], top to bottom, for finding rows by ID. */
    private suspend fun readIdColumn(spreadsheetId: String, account: String): List<String> =
        api.getValues(spreadsheetId, "$account!H:H").values?.map { it.firstOrNull().orEmpty() }.orEmpty()

    /** The Sheet row that currently holds [transaction], or [RowMovedException] if it's gone. */
    private suspend fun currentRow(spreadsheetId: String, transaction: Transaction): Int {
        if (transaction.id.isNullOrBlank()) return transaction.rowIndex
        return RowIds.resolve(readIdColumn(spreadsheetId, transaction.account), transaction.id, transaction.rowIndex)
            ?: throw RowMovedException()
    }

    override suspend fun refreshAllTransactions() {
        accountBalanceDao.getAllSync().forEach { refreshTransactions(it.account) }
    }

    override suspend fun refreshAccountBalances() {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        val range = "Cover Sheet!A:F"
        val response = api.getValues(spreadsheetId, range)
        val rows = response.values ?: return

        val entities = rows.drop(1).mapNotNull { row ->
            if (row.isEmpty() || row.getOrElse(0) { "" }.isBlank()) return@mapNotNull null
            AccountBalanceEntity(
                account = row.getOrElse(0) { "" },
                remainingBalance = row.getOrElse(1) { "0" }.parseCurrency(),
                itemCostThisMonth = row.getOrElse(2) { "" }.parseCurrencyOrNull(),
                subscriptionCost = row.getOrElse(3) { "" }.parseCurrencyOrNull(),
                variance = row.getOrElse(4) { "" }.parseCurrencyOrNull(),
                shouldBuySub = row.getOrElse(5) { "" }.ifBlank { null }
            )
        }

        accountBalanceDao.deleteAll()
        accountBalanceDao.insertAll(entities)
        // The widget shows these balances, so redraw it whenever they change
        WidgetRefresher.refresh(context)
    }

    override suspend fun addTransaction(transaction: Transaction) {
        enqueue(PendingKind.ADD, transaction.copy(id = transaction.id ?: RowIds.newId()))
    }

    override suspend fun updateTransaction(transaction: Transaction) = enqueue(PendingKind.UPDATE, transaction)

    override suspend fun deleteTransaction(transaction: Transaction) = enqueue(PendingKind.DELETE, transaction)

    /** Saves the change on the phone and returns straight away, so saving never waits on a
     *  slow connection. Sending happens in the background; offline, the background job takes
     *  over once there's a connection. */
    private suspend fun enqueue(kind: PendingKind, transaction: Transaction) {
        val spreadsheetId = checkNotNull(prefs.getSpreadsheetId()) { "Connect a spreadsheet before saving changes." }
        require(kind == PendingKind.ADD || !transaction.id.isNullOrBlank()) {
            "Pull down to refresh this transaction before editing or deleting it."
        }
        pendingChangeDao.insert(transaction.toPendingEntity(kind, spreadsheetId))
        PendingSyncWorker.schedule(context)
        syncScope.launch { runCatching { syncPendingChanges() } }
    }

    override fun getPendingChanges(): Flow<List<PendingChange>> =
        pendingChangeDao.getAll().map { list -> list.filter { it.spreadsheetId == prefs.getSpreadsheetId() }.map { it.toModel() } }

    override suspend fun retryPendingChange(id: Long) {
        pendingChangeDao.markFailed(id, null)
        PendingSyncWorker.schedule(context)
        syncScope.launch { runCatching { syncPendingChanges() } }
    }

    override suspend fun discardPendingChange(id: Long) {
        pendingChangeDao.delete(id)
        PendingSyncWorker.schedule(context)
        syncScope.launch { runCatching { syncPendingChanges() } }
    }

    /**
     * Sends queued changes in the order they were made. A dropped connection or a server hiccup
     * stops the run and leaves the rest queued for later. A change Google refuses outright is
     * marked failed (with a reason); later changes wait for Retry or Discard to preserve order.
     * Each save has already booked a durable background job to retry when connected.
     */
    override suspend fun syncPendingChanges(): SyncOutcome = syncMutex.withLock {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return@withLock SyncOutcome.NOTHING_TO_SEND
        val queue = pendingChangeDao.getAllSync().filter { it.spreadsheetId == spreadsheetId }
        if (queue.isEmpty()) return@withLock SyncOutcome.NOTHING_TO_SEND
        var anyFailed = false
        var outcome: SyncOutcome? = null
        for (entity in queue) {
            if (entity.failedReason != null) {
                anyFailed = true
                break
            }
            val change = entity.toModel()
            try {
                when (change.kind) {
                    PendingKind.ADD -> remoteAdd(spreadsheetId, change.transaction)
                    PendingKind.UPDATE -> remoteUpdate(spreadsheetId, change.transaction)
                    PendingKind.DELETE -> remoteDelete(spreadsheetId, change.transaction)
                }
                // Keep the overlay until the confirmed Sheet state is safely cached.
                refreshTransactions(change.transaction.account, spreadsheetId)
                pendingChangeDao.delete(entity.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (SyncErrors.isRetryable(e)) {
                    outcome = SyncOutcome.OFFLINE
                    break
                }
                pendingChangeDao.markFailed(entity.id, SyncErrors.reason(e))
                anyFailed = true
                break
            }
        }
        // Something may have reached the Sheet, so bring the widget up to date
        WidgetRefresher.refresh(context)
        outcome ?: if (anyFailed) SyncOutcome.SOME_FAILED else SyncOutcome.SYNCED
    }

    /** Appends [transaction], unless its ID is already in the Sheet (an earlier attempt got
     *  through but the reply was lost), so a retry never adds it twice. */
    private suspend fun remoteAdd(spreadsheetId: String, transaction: Transaction) {
        if (transaction.id != null && transaction.id in readIdColumn(spreadsheetId, transaction.account).map { it.trim() }) return
        val range = "${transaction.account}!A:H"
        val rounded = roundedAmount(transaction.amount, transaction.category)
        val activeMonthsStr = transaction.activeMonths?.joinToString(",") ?: ""
        val row = listOf(listOf(
            transaction.date,
            transaction.info,
            transaction.amount.toString(),
            transaction.category.displayName,
            rounded.toString(),
            activeMonthsStr,
            SpendTags.normalise(transaction.tag) ?: "",
            transaction.id ?: RowIds.newId()
        ))
        api.appendValues(spreadsheetId, range, body = ValueRange(values = row))
        ensureTagHeader(transaction, spreadsheetId)
    }

    private suspend fun remoteUpdate(spreadsheetId: String, transaction: Transaction) {
        val sheetRow = currentRow(spreadsheetId, transaction)
        val range = "${transaction.account}!A$sheetRow:H$sheetRow"
        val rounded = roundedAmount(transaction.amount, transaction.category)
        val activeMonthsStr = transaction.activeMonths?.joinToString(",") ?: ""
        val row = listOf(listOf(
            transaction.date,
            transaction.info,
            transaction.amount.toString(),
            transaction.category.displayName,
            rounded.toString(),
            activeMonthsStr,
            SpendTags.normalise(transaction.tag) ?: "",
            transaction.id ?: RowIds.newId()
        ))
        api.updateValues(spreadsheetId, range, body = ValueRange(values = row))
        ensureTagHeader(transaction, spreadsheetId)
    }

    override suspend fun assignSpendingCategory(transactions: List<Transaction>, tag: String?) {
        if (transactions.isEmpty()) return
        val spreadsheetId = checkNotNull(prefs.getSpreadsheetId()) { "Connect a spreadsheet before assigning categories." }
        // Point each transaction at the row that holds it now, one ID-column read per account
        val located = transactions.groupBy { it.account }.flatMap { (account, rows) ->
            val ids = readIdColumn(spreadsheetId, account)
            rows.map { tx -> tx.copy(rowIndex = RowIds.resolve(ids, tx.id, tx.rowIndex) ?: throw RowMovedException()) }
        }
        val data = bulkSpendingCategoryValues(located, tag)
        if (data.isEmpty()) return
        api.batchUpdateValues(spreadsheetId, BatchUpdateValuesRequest(data))
        transactions.map { it.account }.distinct().forEach { account ->
            tagHeaderWritten += account
            refreshTransactions(account)
        }
    }

    /** Sheets made before tags existed have no header over column G. Label it the first time
     *  a tagged transaction is written to that account this session. */
    private suspend fun ensureTagHeader(transaction: Transaction, spreadsheetId: String) {
        if (SpendTags.normalise(transaction.tag) == null || transaction.account in tagHeaderWritten) return
        api.updateValues(spreadsheetId, "${transaction.account}!G1", body = ValueRange(values = listOf(listOf("Tag"))))
        tagHeaderWritten += transaction.account
    }

    /** Deleting a row that's already gone counts as done. */
    private suspend fun remoteDelete(spreadsheetId: String, transaction: Transaction) {
        val sheetId = api.getSpreadsheet(spreadsheetId).sheets
            ?.mapNotNull { it.properties }?.firstOrNull { it.title == transaction.account }?.sheetId
            ?: throw RowMovedException()
        val rowIndex = try {
            currentRow(spreadsheetId, transaction) - 1 // 0-based for API
        } catch (e: RowMovedException) {
            return
        }
        api.batchUpdate(
            spreadsheetId,
            BatchUpdateRequest(
                requests = listOf(
                    Request(
                        deleteDimension = DeleteDimensionRequest(
                            range = DimensionRange(
                                sheetId = sheetId,
                                startIndex = rowIndex,
                                endIndex = rowIndex + 1
                            )
                        )
                    )
                )
            )
        )
    }

    override suspend fun deleteOneOffTransactions(account: String) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        val sheetId = resolveSheetIdByName(account) ?: 0

        // Read the Sheet afresh so row numbers match it now, even if it was edited by hand.
        // Then delete ONE_OFF_COST rows highest first, so earlier deletes don't shift later ones.
        refreshTransactions(account)
        val oneOffEntities = transactionDao.getTransactionsByAccountSync(account)
            .filter { it.category == TransactionCategory.ONE_OFF_COST.name }
            .sortedByDescending { it.rowIndex }

        if (oneOffEntities.isEmpty()) return

        val requests = oneOffEntities.map { entity ->
            val rowIdx = entity.rowIndex - 1 // 0-based
            Request(
                deleteDimension = DeleteDimensionRequest(
                    range = DimensionRange(sheetId = sheetId, startIndex = rowIdx, endIndex = rowIdx + 1)
                )
            )
        }
        api.batchUpdate(spreadsheetId, BatchUpdateRequest(requests = requests))
        refreshTransactions(account)
    }

    override suspend fun listSpreadsheets(): List<DriveFile> =
        driveApi.listFiles().files ?: emptyList()

    override suspend fun createSpreadsheetTemplate(name: String, accounts: List<String>): String {
        val sheets = listOf(SheetSpec(NewSheetProperties("Cover Sheet"))) + accounts.map { SheetSpec(NewSheetProperties(it)) }
        val response = api.createSpreadsheet(
            CreateSpreadsheetRequest(
                properties = SpreadsheetTitleProperties(title = name),
                sheets = sheets
            )
        )
        val spreadsheetId = response.spreadsheetId

        // Add headers to Cover Sheet
        val coverHeaders = listOf(listOf("Account", "Balance", "Item Cost This Month", "Subscription Cost", "Variance", "Should Buy Sub"))
        api.updateValues(spreadsheetId, "Cover Sheet!A1:F1", body = ValueRange(values = coverHeaders))

        // Add account rows to Cover Sheet
        val accountRows = accounts.map { listOf(it, "0", "0", "0", "0", "") }
        api.appendValues(spreadsheetId, "Cover Sheet!A:F", body = ValueRange(values = accountRows))

        // Add headers to each transaction sheet
        val txHeaders = listOf(listOf("Date", "Info", "Amount", "Category", "RoundedAmount", "ActiveMonths", "Tag"))
        accounts.forEach { sheet ->
            api.updateValues(spreadsheetId, "$sheet!A1:G1", body = ValueRange(values = txHeaders))
        }

        return spreadsheetId
    }

    override suspend fun renameAccount(oldName: String, newName: String) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return

        // Find the row in Cover Sheet
        val response = api.getValues(spreadsheetId, "Cover Sheet!A:A")
        val rows = response.values ?: return

        val rowIndex = rows.indexOfFirst { it.firstOrNull() == oldName }
        if (rowIndex == -1) return
        val rowNum = rowIndex + 1 // 1-based

        // Update the name cell in Cover Sheet
        api.updateValues(
            spreadsheetId,
            "Cover Sheet!A$rowNum",
            body = ValueRange(values = listOf(listOf(newName)))
        )

        // Rename the corresponding worksheet tab if it exists
        val sheetId = resolveSheetIdByName(oldName)
        if (sheetId != null) {
            api.batchUpdate(
                spreadsheetId,
                BatchUpdateRequest(
                    requests = listOf(
                        Request(
                            updateSheetProperties = UpdateSheetPropertiesRequest(
                                properties = UpdateSheetProps(sheetId = sheetId, title = newName),
                                fields = "title"
                            )
                        )
                    )
                )
            )
            sheetIdCache = emptyMap()
        }

        // The local cache still has rows keyed by the old account name — drop them and
        // re-fetch under the new name so the combined transaction feed doesn't show a
        // ghost account until the next full refresh.
        transactionDao.deleteByAccount(oldName)
        refreshTransactions(newName)
        refreshAccountBalances()
    }

    override suspend fun addAccount(accountName: String) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return

        // Create new worksheet tab
        api.batchUpdate(
            spreadsheetId,
            BatchUpdateRequest(
                requests = listOf(
                    Request(
                        addSheet = AddSheetRequestBody(
                            properties = NewSheetProperties(title = accountName)
                        )
                    )
                )
            )
        )

        // Add header row to the new sheet
        val headers = listOf(listOf("Date", "Info", "Amount", "Category", "RoundedAmount", "ActiveMonths", "Tag"))
        api.appendValues(spreadsheetId, "$accountName!A1:G1", body = ValueRange(values = headers))

        // Add account to Cover Sheet
        val coverRow = listOf(listOf(accountName, "0", "0", "0", "0", ""))
        api.appendValues(spreadsheetId, "Cover Sheet!A:F", body = ValueRange(values = coverRow))

        sheetIdCache = emptyMap()
        refreshAccountBalances()
    }

    override suspend fun recordRollover(account: String, amount: Double, date: String) {
        balanceRolloverDao.insertOrReplace(
            BalanceRolloverEntity(account = account, rolloverAmount = amount, recordedDate = date)
        )
    }

    override suspend fun deleteRollover(account: String) {
        balanceRolloverDao.delete(account)
    }

    override suspend fun deleteAccount(accountName: String) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return

        // Delete the row in Cover Sheet
        val response = api.getValues(spreadsheetId, "Cover Sheet!A:A")
        val rows = response.values ?: return
        val rowIndex = rows.indexOfFirst { it.firstOrNull() == accountName }
        if (rowIndex != -1) {
            val coverSheetId = resolveSheetIdByName("Cover Sheet") ?: 0
            api.batchUpdate(
                spreadsheetId,
                BatchUpdateRequest(
                    requests = listOf(
                        Request(
                            deleteDimension = DeleteDimensionRequest(
                                range = DimensionRange(
                                    sheetId = coverSheetId,
                                    startIndex = rowIndex,
                                    endIndex = rowIndex + 1
                                )
                            )
                        )
                    )
                )
            )
        }

        // Delete the worksheet tab if it exists
        val sheetId = resolveSheetIdByName(accountName)
        if (sheetId != null) {
            api.batchUpdate(
                spreadsheetId,
                BatchUpdateRequest(
                    requests = listOf(Request(deleteSheet = DeleteSheetRequest(sheetId = sheetId)))
                )
            )
            sheetIdCache = emptyMap()
        }

        // Remove from local cache
        accountBalanceDao.deleteAll()
        balanceRolloverDao.delete(accountName)
        transactionDao.deleteByAccount(accountName)
        refreshAccountBalances()
    }

    override suspend fun checkAndProcessNewPayPeriod(): Boolean {
        val payDay = prefs.getPayDay()
        val currentPeriodStart = resolveCurrentPayPeriodStart(payDay)
        val lastProcessed = prefs.getLastPayPeriodStart()

        if (lastProcessed == currentPeriodStart) return false

        // On first install there is no recorded pay period. Just record the current one
        // without deleting any data — the user hasn't completed a full pay cycle yet.
        if (lastProcessed == null) {
            prefs.setLastPayPeriodStart(currentPeriodStart)
            return false
        }

        // Snapshot current balances as rollover BEFORE clearing one-off costs,
        // so the carried-over amount reflects the true end-of-period balance.
        val balances = accountBalanceDao.getAllSync()
        // Summarise the ending period now, before carryover re-dates recurring income into
        // the new one; it's written to the History tab further down.
        val endingPayPeriod = endingPeriod(lastProcessed, currentPeriodStart)
        val endingSummary = endingPayPeriod?.let { period ->
            HistoryCalculator.summarise(
                transactions = transactionDao.getAllSync().map { it.toTransaction() },
                period = period,
                endBalance = balances.sumOf { it.remainingBalance }
            )
        }
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(java.util.Date())
        balances.forEach { entity ->
            balanceRolloverDao.insertOrReplace(
                BalanceRolloverEntity(
                    account = entity.account,
                    rolloverAmount = entity.remainingBalance,
                    recordedDate = today
                )
            )
        }

        val spreadsheetId = prefs.getSpreadsheetId()
        val accounts = balances.map { it.account }

        // Carry over FIXED_COST and TRANSFER (update date to new pay date),
        // and RECURRING_INCOME (update date to the same day-of-month in the current period).
        //
        // Deliberately not swallowed per-account: if any account's update fails (e.g. a
        // network blip), we abort before deleting one-off costs or marking the period as
        // processed below. lastPayPeriodStart stays unset, so the whole operation retries
        // from scratch next launch instead of silently losing carryover data while still
        // wiping that period's one-off costs.
        if (spreadsheetId != null) {
            accounts.forEach { account ->
                // Read the Sheet afresh so the row numbers below match it now
                refreshTransactions(account)
                val entities = transactionDao.getTransactionsByAccountSync(account)
                entities.forEach { entity ->
                    val newDate = when (entity.category) {
                        TransactionCategory.FIXED_COST.name,
                        TransactionCategory.TRANSFER.name -> currentPeriodStart
                        TransactionCategory.RECURRING_INCOME.name -> {
                            val day = entity.date.split("/").firstOrNull()?.toIntOrNull() ?: 1
                            resolveRecurringDate(day, payDay)
                        }
                        else -> null
                    }
                    if (newDate != null && newDate != entity.date) {
                        val range = "$account!A${entity.rowIndex}"
                        api.updateValues(
                            spreadsheetId, range,
                            body = ValueRange(values = listOf(listOf(newDate)))
                        )
                    }
                }
                // Refresh local cache after date updates
                refreshTransactions(account)
            }
        }

        // Carry unspent envelope money forward while the ending period's one-offs still exist.
        // carriedForPeriod makes this safe to repeat if the deletion below fails and retries.
        // Only spending dated in the ending period counts; a cost already added for the new
        // period (e.g. from the widget before the app was opened) mustn't eat into the carry.
        val endingTransactions = transactionDao.getAllSync().map { it.toTransaction() }
        envelopeDao.getAllSync().forEach { entity ->
            if (entity.carriedForPeriod == currentPeriodStart) return@forEach
            val envelope = Envelope(entity.tag, entity.limitAmount, entity.rollover, entity.carriedOver)
            val spent = EnvelopeCalculator.spent(endingTransactions, entity.tag, endingPayPeriod)
            envelopeDao.upsert(
                entity.copy(carriedOver = EnvelopeCalculator.nextCarry(envelope, spent), carriedForPeriod = currentPeriodStart)
            )
        }

        // Save a summary of the ending period to the History tab, since its one-off costs are
        // about to go. A failure here aborts like the carryover above, so nothing is lost.
        if (spreadsheetId != null) {
            endingSummary?.let { recordPeriodSummary(it) }
        }

        // Delete all one-off costs for the new pay period. Only reached if every account's
        // carryover above succeeded (or there was no spreadsheet to carry over).
        accounts.forEach { account -> deleteOneOffTransactions(account) }
        prefs.setLastPayPeriodStart(currentPeriodStart)
        return true
    }

    // --- History ---

    override fun getPeriodSummaries(): Flow<List<PeriodSummary>> =
        periodSummaryDao.getAll().map { entities -> entities.map { it.toPeriodSummary() } }

    override suspend fun refreshPeriodSummaries() {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        if (resolveSheetIdByName(HISTORY_SHEET) == null) {
            periodSummaryDao.deleteAll()
            return
        }
        val rows = api.getValues(spreadsheetId, "$HISTORY_SHEET!A:G").values ?: emptyList()
        periodSummaryDao.replaceAll(rows.drop(1).mapNotNull { PeriodSummaryRow.decode(it)?.toEntity() })
    }

    /** Appends [summary] to the History tab. Skipped if its period already has a row, so a
     *  retried rollover doesn't add it twice. */
    private suspend fun recordPeriodSummary(summary: PeriodSummary) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        ensureSheetExists(HISTORY_SHEET, PeriodSummaryRow.HEADER)
        val existingStarts = api.getValues(spreadsheetId, "$HISTORY_SHEET!A:A").values
            ?.mapNotNull { it.firstOrNull()?.trim() }
            .orEmpty()
        if (summary.periodStart in existingStarts) return

        api.appendValues(spreadsheetId, "$HISTORY_SHEET!A:G", body = ValueRange(values = listOf(PeriodSummaryRow.encode(summary))))
        periodSummaryDao.insertAll(listOf(summary.toEntity()))
    }

    private fun PeriodSummary.toEntity() = PeriodSummaryEntity(
        periodStart = periodStart,
        periodEnd = periodEnd,
        income = income,
        fixedCosts = fixedCosts,
        oneOffCosts = oneOffCosts,
        endBalance = endBalance,
        tagTotals = PeriodSummaryRow.encodeTags(byTag)
    )

    private fun PeriodSummaryEntity.toPeriodSummary() = PeriodSummary(
        periodStart = periodStart,
        periodEnd = periodEnd,
        income = income,
        fixedCosts = fixedCosts,
        oneOffCosts = oneOffCosts,
        endBalance = endBalance,
        byTag = PeriodSummaryRow.decodeTags(tagTotals)
    )

    /** The pay period that ran from [start] up to [nextStart] (both dd/MM/yyyy), or null if
     *  either can't be read, in which case callers count every row. */
    private fun endingPeriod(start: String, nextStart: String): PayPeriod? {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
        val from = runCatching { fmt.parse(start) }.getOrNull() ?: return null
        val to = runCatching { fmt.parse(nextStart) }.getOrNull() ?: return null
        return PayPeriod(from, to)
    }

    // --- Savings goals ---

    override fun getSavingsGoals(): Flow<List<SavingsGoal>> =
        savingsGoalDao.getAll().map { entities -> entities.map { it.toSavingsGoal() } }

    override suspend fun refreshSavingsGoals() {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        if (resolveSheetIdByName(SAVINGS_GOALS_SHEET) == null) return
        val response = api.getValues(spreadsheetId, "$SAVINGS_GOALS_SHEET!A:D")
        val rows = response.values ?: emptyList()
        val entities = rows.drop(1).mapIndexedNotNull { index, row ->
            val name = row.getOrElse(0) { "" }
            if (name.isBlank()) return@mapIndexedNotNull null
            SavingsGoalEntity(
                name = name,
                targetAmount = row.getOrElse(1) { "0" }.parseCurrency(),
                savedAmount = row.getOrElse(2) { "0" }.parseCurrency(),
                targetDate = row.getOrElse(3) { "" }.ifBlank { null },
                rowIndex = index + 2
            )
        }
        savingsGoalDao.deleteAll()
        savingsGoalDao.insertAll(entities)
    }

    override suspend fun addSavingsGoal(goal: SavingsGoal) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        ensureSheetExists(SAVINGS_GOALS_SHEET, listOf("Name", "Target Amount", "Saved Amount", "Target Date"))
        val row = listOf(listOf(goal.name, goal.targetAmount.toString(), goal.savedAmount.toString(), goal.targetDate ?: ""))
        api.appendValues(spreadsheetId, "$SAVINGS_GOALS_SHEET!A:D", body = ValueRange(values = row))
        refreshSavingsGoals()
    }

    override suspend fun updateSavingsGoal(goal: SavingsGoal) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        val row = listOf(listOf(goal.name, goal.targetAmount.toString(), goal.savedAmount.toString(), goal.targetDate ?: ""))
        api.updateValues(
            spreadsheetId,
            "$SAVINGS_GOALS_SHEET!A${goal.rowIndex}:D${goal.rowIndex}",
            body = ValueRange(values = row)
        )
        refreshSavingsGoals()
    }

    override suspend fun deleteSavingsGoal(name: String) {
        deleteNamedRow(SAVINGS_GOALS_SHEET, name)
        refreshSavingsGoals()
    }

    // --- Debts ---

    override fun getDebts(): Flow<List<Debt>> =
        debtDao.getAll().map { entities -> entities.map { it.toDebt() } }

    override suspend fun refreshDebts() {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        if (resolveSheetIdByName(DEBTS_SHEET) == null) return
        val response = api.getValues(spreadsheetId, "$DEBTS_SHEET!A:D")
        val rows = response.values ?: emptyList()
        val entities = rows.drop(1).mapIndexedNotNull { index, row ->
            val name = row.getOrElse(0) { "" }
            if (name.isBlank()) return@mapIndexedNotNull null
            DebtEntity(
                name = name,
                balance = row.getOrElse(1) { "0" }.parseCurrency(),
                aprPercent = row.getOrElse(2) { "0" }.toDoubleOrNull() ?: 0.0,
                minPayment = row.getOrElse(3) { "0" }.parseCurrency(),
                rowIndex = index + 2
            )
        }
        debtDao.deleteAll()
        debtDao.insertAll(entities)
    }

    override suspend fun addDebt(debt: Debt) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        ensureSheetExists(DEBTS_SHEET, listOf("Name", "Balance", "APR %", "Min Payment"))
        val row = listOf(listOf(debt.name, debt.balance.toString(), debt.aprPercent.toString(), debt.minPayment.toString()))
        api.appendValues(spreadsheetId, "$DEBTS_SHEET!A:D", body = ValueRange(values = row))
        refreshDebts()
    }

    override suspend fun updateDebt(debt: Debt) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        val row = listOf(listOf(debt.name, debt.balance.toString(), debt.aprPercent.toString(), debt.minPayment.toString()))
        api.updateValues(
            spreadsheetId,
            "$DEBTS_SHEET!A${debt.rowIndex}:D${debt.rowIndex}",
            body = ValueRange(values = row)
        )
        refreshDebts()
    }

    override suspend fun deleteDebt(name: String) {
        deleteNamedRow(DEBTS_SHEET, name)
        refreshDebts()
    }

    /** Creates [sheetTitle] with a header row if it doesn't already exist in the spreadsheet. */
    override suspend fun readPhoneBackup(): String? {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return null
        if (resolveSheetIdByName(BACKUP_SHEET) == null) return null
        return api.getValues(spreadsheetId, "'$BACKUP_SHEET'!B2").values?.firstOrNull()?.firstOrNull()
    }

    override suspend fun writePhoneBackup(json: String, savedAtText: String) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        if (resolveSheetIdByName(BACKUP_SHEET) == null) {
            api.batchUpdate(
                spreadsheetId,
                BatchUpdateRequest(
                    requests = listOf(Request(addSheet = AddSheetRequestBody(properties = NewSheetProperties(title = BACKUP_SHEET, hidden = true))))
                )
            )
            sheetIdCache = emptyMap()
        }
        api.updateValues(
            spreadsheetId,
            "'$BACKUP_SHEET'!A1:B2",
            valueInputOption = "RAW",
            body = ValueRange(
                values = listOf(
                    listOf("Saved", "Budgetr phone backup (budgets and settings). Please don't edit."),
                    listOf(savedAtText, json)
                )
            )
        )
    }

    private suspend fun ensureSheetExists(sheetTitle: String, header: List<String>) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        if (resolveSheetIdByName(sheetTitle) != null) return
        api.batchUpdate(
            spreadsheetId,
            BatchUpdateRequest(requests = listOf(Request(addSheet = AddSheetRequestBody(properties = NewSheetProperties(title = sheetTitle)))))
        )
        val lastColumn = 'A' + (header.size - 1)
        api.updateValues(spreadsheetId, "$sheetTitle!A1:${lastColumn}1", body = ValueRange(values = listOf(header)))
        sheetIdCache = emptyMap()
    }

    /** Finds the row in [sheetTitle] whose first column equals [name] and deletes it. No-op if not found. */
    private suspend fun deleteNamedRow(sheetTitle: String, name: String) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        val sheetId = resolveSheetIdByName(sheetTitle) ?: return
        val response = api.getValues(spreadsheetId, "$sheetTitle!A:A")
        val rows = response.values ?: return
        val rowIndex = rows.indexOfFirst { it.firstOrNull() == name }
        if (rowIndex == -1) return
        api.batchUpdate(
            spreadsheetId,
            BatchUpdateRequest(
                requests = listOf(
                    Request(
                        deleteDimension = DeleteDimensionRequest(
                            range = DimensionRange(sheetId = sheetId, startIndex = rowIndex, endIndex = rowIndex + 1)
                        )
                    )
                )
            )
        )
    }

    /** Calculates the date for a recurring item that falls on [dayOfMonth] within the current
     *  pay period (which starts on [payDay]). If [dayOfMonth] is before [payDay], the income
     *  falls in the next calendar month (e.g. pay day=26, recurring=9 → next month's 9th).
     *  Applies Saturday → Friday and Sunday → Friday weekend adjustment. */
    private fun resolveRecurringDate(dayOfMonth: Int, payDay: Int): String {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
        val cal = Calendar.getInstance()
        // A recurring day before the pay day falls in the following calendar month
        // within this pay period (e.g. pay day 26 Apr, day 9 → 9 May)
        if (dayOfMonth < payDay) {
            cal.add(Calendar.MONTH, 1)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, minOf(dayOfMonth, maxDay))
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> cal.add(Calendar.DAY_OF_MONTH, -1)
            Calendar.SUNDAY -> cal.add(Calendar.DAY_OF_MONTH, -2)
        }
        return fmt.format(cal.time)
    }

    /** Effective start date of the current pay period (with weekend → Friday adjustment).
     *  Shared with Home's "safe to spend" so the rollover and the days-to-payday count agree. */
    private fun resolveCurrentPayPeriodStart(payDay: Int): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(PayPeriodCalculator.current(payDay).start)

    // Replicates: =IF(OR(EQ(D,"Income"),EQ(D,"Transfer")), C, IF(C<0, ROUNDUP(C,0), C))
    // ROUNDUP rounds away from zero, so -2.99 → -3, 2.99 → 3
    private fun roundedAmount(amount: Double, category: TransactionCategory): Double {
        if (category == TransactionCategory.INCOME ||
            category == TransactionCategory.SALARY ||
            category == TransactionCategory.RECURRING_INCOME ||
            category == TransactionCategory.TRANSFER) {
            return amount
        }
        return if (amount < 0) floor(amount) else ceil(amount)
    }

    private fun Transaction.toPendingEntity(kind: PendingKind, spreadsheetId: String) = PendingChangeEntity(
        spreadsheetId = spreadsheetId,
        kind = kind.name,
        account = account,
        txId = id,
        rowIndex = rowIndex,
        date = date,
        info = info,
        amount = amount,
        category = category.name,
        activeMonths = activeMonths?.joinToString(","),
        tag = tag
    )

    private fun PendingChangeEntity.toModel() = PendingChange(
        id = id,
        kind = runCatching { PendingKind.valueOf(kind) }.getOrDefault(PendingKind.ADD),
        transaction = Transaction(
            rowIndex = rowIndex,
            date = date,
            info = info,
            amount = amount,
            category = TransactionCategory.fromString(category),
            account = account,
            activeMonths = activeMonths?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.ifEmpty { null },
            tag = tag,
            id = txId
        ),
        failedReason = failedReason
    )

    private fun TransactionEntity.toTransaction() = Transaction(
        rowIndex = rowIndex,
        date = date,
        info = info,
        amount = amount,
        category = TransactionCategory.fromString(category),
        account = account,
        activeMonths = activeMonths?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.ifEmpty { null },
        tag = tag,
        id = rowId
    )

    private fun AccountBalanceEntity.toAccountBalance() = AccountBalance(
        account = account,
        remainingBalance = remainingBalance,
        itemCostThisMonth = itemCostThisMonth,
        subscriptionCost = subscriptionCost,
        variance = variance,
        shouldBuySub = shouldBuySub
    )

    private fun SavingsGoalEntity.toSavingsGoal() = SavingsGoal(
        name = name,
        targetAmount = targetAmount,
        savedAmount = savedAmount,
        targetDate = targetDate,
        rowIndex = rowIndex
    )

    private fun DebtEntity.toDebt() = Debt(
        name = name,
        balance = balance,
        aprPercent = aprPercent,
        minPayment = minPayment,
        rowIndex = rowIndex
    )

    private fun String.parseCurrency(): Double =
        replace("[£,\\s]".toRegex(), "").toDoubleOrNull() ?: 0.0

    private fun String.parseCurrencyOrNull(): Double? =
        ifBlank { null }?.replace("[£,\\s]".toRegex(), "")?.toDoubleOrNull()
}
