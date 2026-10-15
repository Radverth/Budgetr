package com.budgetr.app.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.budgetr.app.data.local.BudgetrDatabase
import com.budgetr.app.data.local.dao.BalanceRolloverDao
import com.budgetr.app.data.local.dao.BillDueDayDao
import com.budgetr.app.data.local.dao.CategoryBudgetDao
import com.budgetr.app.data.local.dao.CategoryRuleDao
import com.budgetr.app.data.local.dao.DailyTransferDao
import com.budgetr.app.data.local.dao.EnvelopeDao
import com.budgetr.app.data.local.dao.GoalCategoryLinkDao
import com.budgetr.app.data.local.dao.RecurringCostReviewDao
import com.budgetr.app.data.local.entity.BalanceRolloverEntity
import com.budgetr.app.data.local.entity.BillDueDayEntity
import com.budgetr.app.data.local.entity.CategoryBudgetEntity
import com.budgetr.app.data.local.entity.CategoryRuleEntity
import com.budgetr.app.data.local.entity.DailyTransferEntity
import com.budgetr.app.data.local.entity.EnvelopeEntity
import com.budgetr.app.data.local.entity.GoalCategoryLinkEntity
import com.budgetr.app.data.local.entity.RecurringCostReviewEntity
import com.budgetr.app.data.repository.DailyTargetRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.PreferencesManager
import com.budgetr.app.util.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class BackupResult { SAVED, UNCHANGED, NOT_OVERWRITTEN, FAILED }
enum class RestoreResult { RESTORED, NOTHING_TO_RESTORE, FAILED }

/**
 * Saves the data Budgetr keeps only on the phone (budgets, carry-over, borrow / pay forward,
 * subscription reviews, category rules, bill due days, settings) to a hidden tab in the Google Sheet, and restores it.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val sheetsRepository: SheetsRepository,
    private val categoryBudgetDao: CategoryBudgetDao,
    private val envelopeDao: EnvelopeDao,
    private val goalCategoryLinkDao: GoalCategoryLinkDao,
    private val recurringCostReviewDao: RecurringCostReviewDao,
    private val dailyTransferDao: DailyTransferDao,
    private val balanceRolloverDao: BalanceRolloverDao,
    private val categoryRuleDao: CategoryRuleDao,
    private val billDueDayDao: BillDueDayDao,
    private val dailyTargetRepository: DailyTargetRepository,
    private val prefs: PreferencesManager,
    @ApplicationContext private val context: Context,
    private val db: BudgetrDatabase
) {
    private val mutex = Mutex()

    /** Writes a backup if anything changed since the last one. [force] writes regardless. */
    suspend fun backup(force: Boolean = false): BackupResult = mutex.withLock {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return@withLock BackupResult.FAILED
        try {
            val snapshot = snapshot()
            val fingerprint = "$spreadsheetId:${BackupCodec.encode(snapshot.copy(savedAt = 0)).hashCode()}"
            if (!force && fingerprint == prefs.getLastBackupFingerprint()) return@withLock BackupResult.UNCHANGED
            // A phone with nothing set up mustn't wipe a backup made on another phone
            if (snapshot.isEmpty && BackupCodec.decode(sheetsRepository.readPhoneBackup())?.isEmpty == false) {
                return@withLock BackupResult.NOT_OVERWRITTEN
            }
            val savedAtText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.UK).format(Date(snapshot.savedAt))
            sheetsRepository.writePhoneBackup(BackupCodec.encode(snapshot), savedAtText)
            prefs.setLastBackup(snapshot.savedAt, fingerprint)
            BackupResult.SAVED
        } catch (e: Exception) {
            BackupResult.FAILED
        }
    }

    /** Replaces the phone's budgets and settings with the Sheet's backup. */
    suspend fun restore(): RestoreResult = mutex.withLock {
        val backup = try {
            BackupCodec.decode(sheetsRepository.readPhoneBackup())
        } catch (e: Exception) {
            return@withLock RestoreResult.FAILED
        } ?: return@withLock RestoreResult.NOTHING_TO_RESTORE
        try { apply(backup) } catch (_: Exception) { return@withLock RestoreResult.FAILED }
        prefs.getSpreadsheetId()?.let { id ->
            prefs.setLastBackup(backup.savedAt, "$id:${BackupCodec.encode(backup.copy(savedAt = 0)).hashCode()}")
        }
        RestoreResult.RESTORED
    }

    /**
     * Once per spreadsheet per phone: if this phone has no budget data yet but the Sheet has a
     * backup (a new phone, or a reinstall), restore it. A phone that already has data is left
     * alone; it can still restore by hand from Settings.
     */
    suspend fun restoreIfNewPhone(): RestoreResult {
        val spreadsheetId = prefs.getSpreadsheetId() ?: return RestoreResult.NOTHING_TO_RESTORE
        if (prefs.isRestoreChecked(spreadsheetId)) return RestoreResult.NOTHING_TO_RESTORE
        if (!snapshot().isEmpty) {
            prefs.setRestoreChecked(spreadsheetId)
            return RestoreResult.NOTHING_TO_RESTORE
        }
        val result = restore()
        // Try again next launch if Google couldn't be reached
        if (result != RestoreResult.FAILED) prefs.setRestoreChecked(spreadsheetId)
        return result
    }

    private suspend fun snapshot(): PhoneBackup = db.withTransaction { PhoneBackup(
        savedAt = System.currentTimeMillis(),
        categoryBudgets = categoryBudgetDao.getAllSync().map { CategoryBudgetBackup(it.category, it.limit) },
        envelopes = envelopeDao.getAllSync().map { EnvelopeBackup(it.tag, it.limitAmount, it.rollover, it.carriedOver, it.carriedForPeriod) },
        goalLinks = goalCategoryLinkDao.getAllSync().map { GoalLinkBackup(it.goalName, it.category) },
        subscriptionReviews = recurringCostReviewDao.getAllSync().map {
            ReviewBackup(it.account, it.info, it.firstSeenDate, it.lastAmount, it.dismissedUntil)
        },
        dailyTransfers = dailyTransferDao.getAllSync().map { DailyTransferBackup(it.periodStart, it.createdOn, it.amount, it.targetDay) },
        rollovers = balanceRolloverDao.getAllSync().map { RolloverBackup(it.account, it.rolloverAmount, it.recordedDate) },
        categoryRules = categoryRuleDao.getAllSync().map { CategoryRuleBackup(it.pattern, it.tag) },
        billDueDays = billDueDayDao.getAllSync().map { BillDueDayBackup(it.account, it.infoKey, it.dueDay) },
        periodReviews = db.periodReviewDao().all(prefs.getSpreadsheetId().orEmpty()),
        settings = SettingsBackup(
            payDay = prefs.getPayDay(),
            lastPayPeriodStart = prefs.getLastPayPeriodStart(),
            reminderEnabled = prefs.isReminderEnabled(),
            reminderHour = prefs.getReminderHour(),
            reminderMinute = prefs.getReminderMinute(),
            spendingPromptEnabled = prefs.isSpendingPromptEnabled(),
            spendingPromptThreshold = prefs.getSpendingPromptThreshold(),
            planIncludeGoals = prefs.isPlanIncludeGoals(),
            planIncludeDebts = prefs.isPlanIncludeDebts(),
            dailyPotOverride = prefs.getDailyPotOverride(),
            dailyPotPeriod = prefs.getDailyPotPeriod(),
            dailyPotAmount = prefs.getDailyPotAmount(),
            dailyCountBudgeted = prefs.isDailyTargetCountingBudgeted(),
            dailyOnHome = prefs.isDailyTargetOnHome(),
            homeShowPeriod = prefs.isHomeShowingPeriod(),
            homeShowGoals = prefs.isHomeShowingGoals(),
            homeShowAccounts = prefs.isHomeShowingAccounts(),
            forecastSpendingAccount = prefs.getForecastSpendingAccount()
        )
    ) }

    private suspend fun apply(backup: PhoneBackup) {
        db.withTransaction {
        val sheet = requireNotNull(prefs.getSpreadsheetId())
        val restored = backup.periodReviews.map { it.copy(spreadsheetId = sheet) }
        // Never restore older money decisions over newer receipts on this phone.
        db.periodReviewDao().all(sheet).filter { it.receiptJson != null }.forEach { local ->
            check(restored.any { it.periodStart == local.periodStart && it.receiptJson == local.receiptJson }) {
                "This backup predates a saved leftover choice"
            }
        }
        restored.forEach { db.periodReviewDao().put(it) }
        categoryBudgetDao.deleteAll()
        backup.categoryBudgets.forEach { categoryBudgetDao.upsert(CategoryBudgetEntity(it.category, it.limit)) }
        envelopeDao.deleteAll()
        backup.envelopes.forEach { envelopeDao.upsert(EnvelopeEntity(it.tag, it.limit, it.rollover, it.carriedOver, it.carriedForPeriod)) }
        goalCategoryLinkDao.deleteAll()
        backup.goalLinks.forEach { goalCategoryLinkDao.upsert(GoalCategoryLinkEntity(it.goalName, it.category)) }
        recurringCostReviewDao.deleteAll()
        backup.subscriptionReviews.forEach {
            recurringCostReviewDao.upsert(RecurringCostReviewEntity(it.account, it.info, it.firstSeenDate, it.lastAmount, it.dismissedUntil))
        }
        dailyTransferDao.deleteAll()
        backup.dailyTransfers.forEach { dailyTransferDao.insert(DailyTransferEntity(0, it.periodStart, it.createdOn, it.amount, it.targetDay)) }
        balanceRolloverDao.deleteAll()
        balanceRolloverDao.insertAll(backup.rollovers.map { BalanceRolloverEntity(it.account, it.amount, it.recordedDate) })
        categoryRuleDao.deleteAll()
        backup.categoryRules.forEach { categoryRuleDao.upsert(CategoryRuleEntity(pattern = it.pattern, tag = it.tag)) }
        billDueDayDao.deleteAll()
        backup.billDueDays.forEach { billDueDayDao.upsert(BillDueDayEntity(it.account, it.infoKey, it.dueDay)) }

        }

        val s = backup.settings
        s.payDay?.let { prefs.setPayDay(it) }
        // lastPayPeriodStart is kept in the backup for reference but never restored: an older
        // value could make this phone re-run a payday another phone already handled, which
        // would clear the new period's one-off costs.
        s.spendingPromptEnabled?.let { prefs.setSpendingPromptEnabled(it) }
        s.spendingPromptThreshold?.let { prefs.setSpendingPromptThreshold(it) }
        s.planIncludeGoals?.let { prefs.setPlanIncludeGoals(it) }
        s.planIncludeDebts?.let { prefs.setPlanIncludeDebts(it) }
        prefs.setDailyPotOverride(s.dailyPotOverride)
        if (s.dailyPotPeriod != null && s.dailyPotAmount != null) prefs.setDailyPot(s.dailyPotPeriod, s.dailyPotAmount)
        s.dailyCountBudgeted?.let { prefs.setDailyTargetCountingBudgeted(it) }
        s.dailyOnHome?.let { prefs.setDailyTargetOnHome(it) }
        s.homeShowPeriod?.let { prefs.setHomeShowingPeriod(it) }
        s.homeShowGoals?.let { prefs.setHomeShowingGoals(it) }
        s.homeShowAccounts?.let { prefs.setHomeShowingAccounts(it) }
        s.forecastSpendingAccount?.let { prefs.setForecastSpendingAccount(it) }
        if (s.reminderHour != null && s.reminderMinute != null) prefs.setReminderTime(s.reminderHour, s.reminderMinute)
        s.reminderEnabled?.let { enabled ->
            prefs.setReminderEnabled(enabled)
            if (enabled) ReminderScheduler.schedule(context, prefs.getReminderHour(), prefs.getReminderMinute())
            else ReminderScheduler.cancel(context)
        }
        dailyTargetRepository.notifySettingsChanged()
    }
}
