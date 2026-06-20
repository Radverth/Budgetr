package com.budgetr.app.data.repository

import com.budgetr.app.data.api.AddSheetRequestBody
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
import com.budgetr.app.data.local.dao.TransactionDao
import com.budgetr.app.data.local.entity.AccountBalanceEntity
import com.budgetr.app.data.local.entity.BalanceRolloverEntity
import com.budgetr.app.data.local.entity.TransactionEntity
import com.budgetr.app.data.model.AccountBalance
import com.budgetr.app.data.model.BalanceRollover
import com.budgetr.app.data.model.SheetTab
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.util.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import javax.inject.Inject

class SheetsRepositoryImpl @Inject constructor(
    private val api: GoogleSheetsApi,
    private val driveApi: GoogleDriveApi,
    private val transactionDao: TransactionDao,
    private val accountBalanceDao: AccountBalanceDao,
    private val balanceRolloverDao: BalanceRolloverDao,
    private val prefs: PreferencesManager
) : SheetsRepository {

    // Cache of sheet title -> numeric sheetId (gid) from the Sheets API
    private var sheetIdCache: Map<String, Int> = emptyMap()

    private suspend fun resolveSheetId(sheetTab: SheetTab): Int {
        sheetIdCache[sheetTab.sheetName]?.let { return it }
        val spreadsheetId = prefs.getSpreadsheetId() ?: return 0
        val metadata = api.getSpreadsheet(spreadsheetId)
        sheetIdCache = metadata.sheets
            ?.mapNotNull { it.properties }
            ?.associate { it.title to it.sheetId }
            ?: emptyMap()
        return sheetIdCache[sheetTab.sheetName] ?: 0
    }

    private suspend fun resolveSheetIdByName(name: String): Int? {
        sheetIdCache[name]?.let { return it }
        val spreadsheetId = prefs.getSpreadsheetId() ?: return null
        val metadata = api.getSpreadsheet(spreadsheetId)
        sheetIdCache = metadata.sheets
            ?.mapNotNull { it.properties }
            ?.associate { it.title to it.sheetId }
            ?: emptyMap()
        return sheetIdCache[name]
    }

    override fun getTransactions(sheetTab: SheetTab): Flow<List<Transaction>> =
        transactionDao.getTransactionsByTab(sheetTab.name).map { entities ->
            entities.map { it.toTransaction() }
        }

    override fun getAccountBalances(): Flow<List<AccountBalance>> =
        accountBalanceDao.getAll().map { entities ->
            entities.map { it.toAccountBalance() }
        }

    override fun getBalanceRollovers(): Flow<List<BalanceRollover>> =
        balanceRolloverDao.getAll().map { entities ->
            entities.map { BalanceRollover(it.account, it.rolloverAmount, it.recordedDate) }
        }

    override suspend fun refreshTransactions(sheetTab: SheetTab) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        val range = "${sheetTab.sheetName}!A:F"
        val response = api.getValues(spreadsheetId, range)
        val rows = response.values ?: return

        // First row is header — skip it
        val entities = rows.drop(1).mapIndexedNotNull { index, row ->
            if (row.isEmpty()) return@mapIndexedNotNull null
            TransactionEntity(
                rowIndex = index + 2, // +2: skip header, 1-based
                date = row.getOrElse(0) { "" },
                info = row.getOrElse(1) { "" },
                amount = row.getOrElse(2) { "0" }.replace("[£,]".toRegex(), "").toDoubleOrNull() ?: 0.0,
                category = TransactionCategory.fromString(row.getOrElse(3) { "" }).name,
                sheetTab = sheetTab.name,
                activeMonths = row.getOrElse(5) { "" }.ifBlank { null }
            )
        }

        transactionDao.deleteByTab(sheetTab.name)
        transactionDao.insertAll(entities)
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
    }

    override suspend fun addTransaction(transaction: Transaction) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        val range = "${transaction.sheetTab.sheetName}!A:F"
        val rounded = roundedAmount(transaction.amount, transaction.category)
        val activeMonthsStr = transaction.activeMonths?.joinToString(",") ?: ""
        val row = listOf(listOf(
            transaction.date,
            transaction.info,
            transaction.amount.toString(),
            transaction.category.displayName,
            rounded.toString(),
            activeMonthsStr
        ))
        api.appendValues(spreadsheetId, range, body = ValueRange(values = row))
        refreshTransactions(transaction.sheetTab)
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        val range = "${transaction.sheetTab.sheetName}!A${transaction.rowIndex}:F${transaction.rowIndex}"
        val rounded = roundedAmount(transaction.amount, transaction.category)
        val activeMonthsStr = transaction.activeMonths?.joinToString(",") ?: ""
        val row = listOf(listOf(
            transaction.date,
            transaction.info,
            transaction.amount.toString(),
            transaction.category.displayName,
            rounded.toString(),
            activeMonthsStr
        ))
        api.updateValues(spreadsheetId, range, body = ValueRange(values = row))
        refreshTransactions(transaction.sheetTab)
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        val sheetId = resolveSheetId(transaction.sheetTab)
        val rowIndex = transaction.rowIndex - 1 // 0-based for API
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
        refreshTransactions(transaction.sheetTab)
    }

    override suspend fun deleteOneOffTransactions(sheetTab: SheetTab) {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return
        val sheetId = resolveSheetId(sheetTab)

        // Get ONE_OFF_COST rows sorted descending so high indices delete first (prevents index shifting)
        val oneOffEntities = transactionDao.getTransactionsByTabSync(sheetTab.name)
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
        refreshTransactions(sheetTab)
    }

    override suspend fun listSpreadsheets(): List<DriveFile> =
        driveApi.listFiles().files ?: emptyList()

    override suspend fun createSpreadsheetTemplate(name: String): String {
        val sheets = listOf(
            SheetSpec(NewSheetProperties("Cover Sheet")),
            SheetSpec(NewSheetProperties("Monzo")),
            SheetSpec(NewSheetProperties("Halifax Debit Card")),
            SheetSpec(NewSheetProperties("Halifax Credit Card"))
        )
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
        val accountRows = listOf(
            listOf("Monzo", "0", "0", "0", "0", ""),
            listOf("Halifax Debit Card", "0", "0", "0", "0", ""),
            listOf("Halifax Credit Card", "0", "0", "0", "0", "")
        )
        api.appendValues(spreadsheetId, "Cover Sheet!A:F", body = ValueRange(values = accountRows))

        // Add headers to each transaction sheet
        val txHeaders = listOf(listOf("Date", "Info", "Amount", "Category", "RoundedAmount", "ActiveMonths"))
        listOf("Monzo", "Halifax Debit Card", "Halifax Credit Card").forEach { sheet ->
            api.updateValues(spreadsheetId, "$sheet!A1:F1", body = ValueRange(values = txHeaders))
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
        val headers = listOf(listOf("Date", "Info", "Amount", "Category", "RoundedAmount"))
        api.appendValues(spreadsheetId, "$accountName!A1:E1", body = ValueRange(values = headers))

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

        // Carry over FIXED_COST and TRANSFER (update date to new pay date),
        // and RECURRING_INCOME (update date to the same day-of-month in the current period).
        if (spreadsheetId != null) {
            SheetTab.entries.forEach { tab ->
                try {
                    val tabEntities = transactionDao.getTransactionsByTabSync(tab.name)
                    tabEntities.forEach { entity ->
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
                            val range = "${tab.sheetName}!A${entity.rowIndex}"
                            api.updateValues(
                                spreadsheetId, range,
                                body = ValueRange(values = listOf(listOf(newDate)))
                            )
                        }
                    }
                    // Refresh local cache after date updates
                    refreshTransactions(tab)
                } catch (_: Exception) {}
            }
        }

        // Delete all one-off costs for the new pay period
        SheetTab.entries.forEach { tab ->
            try { deleteOneOffTransactions(tab) } catch (_: Exception) {}
        }
        prefs.setLastPayPeriodStart(currentPeriodStart)
        return true
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

    /** Calculates the effective start date of the current pay period (with weekend → Friday adjustment).
     *
     *  We compare today against the EFFECTIVE (weekend-adjusted) payday for the current month,
     *  not the raw configured day. Without this, a payDay of 26 (Sunday → effective Friday 24)
     *  would not trigger the rollover on Friday 24 because 24 < 26, causing the rollover to fire
     *  two days late on Monday 27 instead. */
    private fun resolveCurrentPayPeriodStart(payDay: Int): String {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
        val cal = Calendar.getInstance()
        val todayDayOfMonth = cal.get(Calendar.DAY_OF_MONTH)

        // Compute the effective (weekend-adjusted) pay day for the current calendar month
        // so the comparison uses the real trigger date, not the raw configured day.
        val thisMonthCal = Calendar.getInstance()
        val maxDayThisMonth = thisMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        thisMonthCal.set(Calendar.DAY_OF_MONTH, minOf(payDay, maxDayThisMonth))
        when (thisMonthCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> thisMonthCal.add(Calendar.DAY_OF_MONTH, -1)
            Calendar.SUNDAY -> thisMonthCal.add(Calendar.DAY_OF_MONTH, -2)
        }
        val effectivePayDayThisMonth = thisMonthCal.get(Calendar.DAY_OF_MONTH)

        if (todayDayOfMonth < effectivePayDayThisMonth) {
            cal.add(Calendar.MONTH, -1)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, minOf(payDay, maxDay))
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> cal.add(Calendar.DAY_OF_MONTH, -1)
            Calendar.SUNDAY -> cal.add(Calendar.DAY_OF_MONTH, -2)
        }

        return fmt.format(cal.time)
    }

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

    private fun TransactionEntity.toTransaction() = Transaction(
        rowIndex = rowIndex,
        date = date,
        info = info,
        amount = amount,
        category = TransactionCategory.fromString(category),
        sheetTab = SheetTab.valueOf(sheetTab),
        activeMonths = activeMonths?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.ifEmpty { null }
    )

    private fun AccountBalanceEntity.toAccountBalance() = AccountBalance(
        account = account,
        remainingBalance = remainingBalance,
        itemCostThisMonth = itemCostThisMonth,
        subscriptionCost = subscriptionCost,
        variance = variance,
        shouldBuySub = shouldBuySub
    )

    private fun String.parseCurrency(): Double =
        replace("[£,\\s]".toRegex(), "").toDoubleOrNull() ?: 0.0

    private fun String.parseCurrencyOrNull(): Double? =
        ifBlank { null }?.replace("[£,\\s]".toRegex(), "")?.toDoubleOrNull()
}
