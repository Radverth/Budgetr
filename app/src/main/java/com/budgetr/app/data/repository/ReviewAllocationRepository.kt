package com.budgetr.app.data.repository

import androidx.room.withTransaction
import com.budgetr.app.data.backup.BackupRepository
import com.budgetr.app.data.local.BudgetrDatabase
import com.budgetr.app.data.model.Envelope
import com.budgetr.app.data.model.ReviewDestination
import com.budgetr.app.data.model.ReviewReceipt
import com.budgetr.app.util.EnvelopeCalculator
import com.budgetr.app.util.PayPeriodCalculator
import com.budgetr.app.util.PeriodReviewCalculator
import com.budgetr.app.util.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the one leftover decision a finished pay period's review allows. This is budget
 * bookkeeping, not a bank transfer: the leftover is already in the accounts' carry-over, so
 * "keep for next period" changes nothing, a category gets extra carry-over (which its budget
 * then reserves), and a goal contribution raises the goal and takes the same amount out of
 * the carried cash so it isn't spent twice.
 */
@Singleton
class ReviewAllocationRepository @Inject constructor(
    private val db: BudgetrDatabase,
    private val reviews: PeriodReviewRepository,
    private val sheets: SheetsRepository,
    private val prefs: PreferencesManager,
    private val backup: BackupRepository
) {

    /** Pennies that can still be given a job, or 0 once a decision has been made. */
    suspend fun available(start: String): Long = reviews.operationMutex.withLock { availableLocked(start) }

    suspend fun allocate(start: String, destination: ReviewDestination, amountPence: Long) = reviews.operationMutex.withLock {
        val existing = reviews.receipt(start)
        if (existing != null) {
            check(existing.matches(destination, amountPence)) {
                if (existing.complete) "You've already chosen where this leftover goes."
                else "Your earlier choice is still being saved. Try that choice again to finish it."
            }
            // Finish an interrupted goal contribution with its original figures
            if (!existing.complete) finishGoal(start, existing)
            return@withLock
        }

        val snapshot = checkNotNull(reviews.get(start)?.takeIf { it.complete }) { "This review isn't ready yet." }
        check(snapshot.review.nextPeriodStart == prefs.getLastPayPeriodStart()) {
            "Only the most recent pay period's leftover can be moved."
        }
        check(sheets.getPendingChanges().first().isEmpty()) { "Wait until your latest changes reach Google Sheets, then try again." }
        val receipt = ReviewReceipt.of(destination, amountPence, UUID.randomUUID().toString())

        when (destination) {
            ReviewDestination.NextPeriod -> db.withTransaction { reviews.saveReceipt(start, receipt.copy(complete = true)) }
            is ReviewDestination.Category -> {
                checkAmount(start, amountPence)
                db.withTransaction {
                    val envelope = checkNotNull(db.envelopeDao().getAllSync().find { it.tag.equals(destination.tag, ignoreCase = true) }) {
                        "There's no ${destination.tag} budget any more."
                    }
                    db.envelopeDao().upsert(envelope.copy(carriedOver = envelope.carriedOver + amountPence / 100.0))
                    reviews.saveReceipt(start, receipt.copy(complete = true))
                }
            }
            is ReviewDestination.Goal -> {
                checkAmount(start, amountPence)
                sheets.refreshSavingsGoals()
                val goal = checkNotNull(sheets.getSavingsGoals().first().find { it.name == destination.name }) {
                    "The goal \"${destination.name}\" isn't in your Google Sheet any more."
                }
                val expected = PeriodReviewCalculator.pence(goal.savedAmount)
                val intent = receipt.copy(expectedGoalPence = expected, desiredGoalPence = expected + amountPence)
                // Saved (and backed up) before the Sheet write, so a lost response can't lead to a second contribution
                db.withTransaction { reviews.saveReceipt(start, intent) }
                runCatching { backup.backup(force = true) }
                finishGoal(start, intent)
                return@withLock
            }
        }
        runCatching { backup.backup(force = true) }
    }

    private suspend fun checkAmount(start: String, amountPence: Long) {
        require(amountPence > 0) { "Choose an amount above £0." }
        check(amountPence <= availableLocked(start)) { "That's more than the leftover still available." }
    }

    private suspend fun finishGoal(start: String, intent: ReviewReceipt) {
        sheets.contributeReviewGoal(intent.target.orEmpty(), checkNotNull(intent.expectedGoalPence), checkNotNull(intent.desiredGoalPence))
        db.withTransaction {
            takeFromCarry(intent.amountPence)
            reviews.saveReceipt(start, intent.copy(complete = true))
        }
        runCatching { backup.backup(force = true) }
    }

    /** Takes [pence] out of the accounts' carried balances, largest first. */
    private suspend fun takeFromCarry(pence: Long) {
        var left = pence / 100.0
        db.balanceRolloverDao().getAllSync().sortedByDescending { it.rolloverAmount }.forEach { rollover ->
            if (left <= 0.0) return@forEach
            val take = minOf(left, rollover.rolloverAmount.coerceAtLeast(0.0))
            if (take > 0.0) {
                db.balanceRolloverDao().insertOrReplace(rollover.copy(rolloverAmount = rollover.rolloverAmount - take))
                left -= take
            }
        }
    }

    private suspend fun availableLocked(start: String): Long {
        val snapshot = reviews.get(start)?.takeIf { it.complete } ?: return 0
        if (snapshot.review.nextPeriodStart != prefs.getLastPayPeriodStart()) return 0
        if (reviews.receipt(start) != null) return 0
        // Carried cash not already set aside by this period's category budgets
        val period = PayPeriodCalculator.current(prefs.getPayDay())
        val daysLeft = period.daysUntilPayday()
        val transactions = sheets.getAllTransactions().first()
        val reserved = EnvelopeCalculator.reserved(db.envelopeDao().getAllSync().map {
            EnvelopeCalculator.status(Envelope(it.tag, it.limitAmount, it.rollover, it.carriedOver), transactions, period, daysLeft)
        })
        val carried = db.balanceRolloverDao().getAllSync().sumOf { it.rolloverAmount }
        return PeriodReviewCalculator.eligibleLeftover(
            snapshot.review.endBalancePence,
            snapshot.review.reservedCarryPence,
            PeriodReviewCalculator.pence(carried - reserved)
        )
    }
}
