package com.budgetr.app.util

import com.budgetr.app.data.model.*
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToLong

object PeriodReviewCalculator {
    fun pence(amount: Double): Long = (amount * 100).roundToLong()

    fun calculate(period: PayPeriod, transactions: List<Transaction>, plan: ReviewPlan?, transfers: List<DailyTransfer>, endBalance: Double): PeriodReview {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
        val start = fmt.format(period.start)
        val closingPlan = plan?.takeIf { it.periodStart == start }
        val summary = HistoryCalculator.summarise(transactions, period, endBalance)
        val envelopes = closingPlan?.envelopes.orEmpty().map { it.envelope() }
        val plannedTags = envelopes.map { it.tag.lowercase() }.toSet()
        val categories = envelopes.map { envelope ->
            ReviewCategory(envelope.tag, EnvelopeCalculator.spent(transactions, envelope.tag, period), envelope.limit + envelope.carriedOver)
        }.sortedByDescending { it.variance } + summary.byTag.filter { it.tag?.lowercase() !in plannedTags }
            .map { ReviewCategory(it.tag ?: "Untagged", it.amount, null) }
        val daily = closingPlan?.pot?.let { pot ->
            DailyTargetCalculator.calculate(period, pot, transactions, transfers.filter { it.periodStart == start }, HistoryCalculator.dayBefore(period.nextPayday)) {
                closingPlan.countBudgeted || it.tag?.lowercase() !in plannedTags
            }
        }
        return PeriodReview(
            start, summary.periodEnd, fmt.format(period.nextPayday), summary.oneOffCosts, summary.fixedCosts,
            closingPlan?.pot, daily?.days?.count { it.left >= -0.005 }, daily?.days?.size,
            categories, pence(endBalance), pence(envelopes.sumOf { EnvelopeCalculator.nextCarry(it, EnvelopeCalculator.spent(transactions, it.tag, period)) })
        )
    }

    fun eligibleLeftover(savedBalance: Long, reservedCarry: Long, availableNow: Long): Long =
        minOf((savedBalance.coerceAtLeast(0) - reservedCarry.coerceAtLeast(0)).coerceAtLeast(0), availableNow.coerceAtLeast(0))
}
