package com.budgetr.app.data.local

import com.budgetr.app.data.local.dao.PeriodReviewDao
import com.budgetr.app.data.local.entity.PeriodReviewEntity
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.budgetr.app.data.local.entity.AccountBalanceEntity
import com.budgetr.app.data.local.entity.BalanceRolloverEntity
import com.budgetr.app.data.local.entity.BillDueDayEntity
import com.budgetr.app.data.local.entity.CategoryBudgetEntity
import com.budgetr.app.data.local.entity.CategoryRuleEntity
import com.budgetr.app.data.local.entity.DailyTransferEntity
import com.budgetr.app.data.local.entity.DebtEntity
import com.budgetr.app.data.local.entity.EnvelopeEntity
import com.budgetr.app.data.local.entity.GoalCategoryLinkEntity
import com.budgetr.app.data.local.entity.PendingChangeEntity
import com.budgetr.app.data.local.entity.PeriodSummaryEntity
import com.budgetr.app.data.local.entity.RecurringCostReviewEntity
import com.budgetr.app.data.local.entity.SavingsGoalEntity
import com.budgetr.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        AccountBalanceEntity::class,
        BalanceRolloverEntity::class,
        SavingsGoalEntity::class,
        DebtEntity::class,
        CategoryBudgetEntity::class,
        RecurringCostReviewEntity::class,
        GoalCategoryLinkEntity::class,
        EnvelopeEntity::class,
        PeriodSummaryEntity::class,
        DailyTransferEntity::class,
        PendingChangeEntity::class,
        CategoryRuleEntity::class,
        BillDueDayEntity::class,
        PeriodReviewEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class BudgetrDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountBalanceDao(): AccountBalanceDao
    abstract fun balanceRolloverDao(): BalanceRolloverDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun debtDao(): DebtDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun recurringCostReviewDao(): RecurringCostReviewDao
    abstract fun goalCategoryLinkDao(): GoalCategoryLinkDao
    abstract fun envelopeDao(): EnvelopeDao
    abstract fun periodSummaryDao(): PeriodSummaryDao
    abstract fun dailyTransferDao(): DailyTransferDao
    abstract fun pendingChangeDao(): PendingChangeDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun billDueDayDao(): BillDueDayDao

    abstract fun periodReviewDao(): PeriodReviewDao

    companion object {
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `period_reviews` (`spreadsheetId` TEXT NOT NULL, `periodStart` TEXT NOT NULL, `planJson` TEXT, `snapshotJson` TEXT, `complete` INTEGER NOT NULL, `receiptJson` TEXT, PRIMARY KEY(`spreadsheetId`, `periodStart`))")
            }
        }

        /** Adds the optional spending category to cached transactions. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN tag TEXT")
            }
        }

        /** Adds per-category budgets ("envelopes"). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `envelopes` (`tag` TEXT NOT NULL, `limitAmount` REAL NOT NULL, " +
                        "`rollover` INTEGER NOT NULL, `carriedOver` REAL NOT NULL, `carriedForPeriod` TEXT, PRIMARY KEY(`tag`))"
                )
            }
        }

        /** Adds the local cache of the Sheet's History tab. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `period_summaries` (`periodStart` TEXT NOT NULL, `periodEnd` TEXT NOT NULL, " +
                        "`income` REAL NOT NULL, `fixedCosts` REAL NOT NULL, `oneOffCosts` REAL NOT NULL, " +
                        "`endBalance` REAL NOT NULL, `tagTotals` TEXT NOT NULL, PRIMARY KEY(`periodStart`))"
                )
            }
        }

        /** Adds the queue of changes waiting to reach the Sheet (offline saving). */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_changes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`spreadsheetId` TEXT NOT NULL, `kind` TEXT NOT NULL, `account` TEXT NOT NULL, `txId` TEXT, `rowIndex` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, `info` TEXT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, " +
                        "`activeMonths` TEXT, `tag` TEXT, `failedReason` TEXT, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** Adds bill due days for the cash-flow forecast. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bill_due_days` (`account` TEXT NOT NULL, `infoKey` TEXT NOT NULL, " +
                        "`dueDay` INTEGER NOT NULL, PRIMARY KEY(`account`, `infoKey`))"
                )
            }
        }

        /** Adds category rules ("description contains TESCO → Groceries"). */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `category_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`pattern` TEXT NOT NULL, `tag` TEXT NOT NULL)"
                )
            }
        }

        /** Adds the hidden row ID that keeps edits pointed at the right Sheet row. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN rowId TEXT")
            }
        }

        /** Adds borrow / pay-forward moves for the daily spend target. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_transfers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`periodStart` TEXT NOT NULL, `createdOn` TEXT NOT NULL, `amount` REAL NOT NULL, `targetDay` TEXT)"
                )
            }
        }
    }
}
