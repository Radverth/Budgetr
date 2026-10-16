package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Saving up for one yearly bill. [saved] is set aside so far; the rest record what the last
 * processed pay period ([processedPeriod], dd/MM/yyyy) did, so processing it again changes nothing.
 */
data class AnnualFund(
    val account: String,
    val info: String,
    val saved: Double = 0.0,
    val processedPeriod: String? = null,
    val lastSetAside: Double = 0.0,
    val lastReleased: Double = 0.0
)

/** A yearly bill and how its fund is doing this pay period. */
data class AnnualBillStatus(
    val bill: Transaction,
    val amount: Double,
    val dueMonth: Int,
    val fund: AnnualFund?,
    /** Set aside this payday (0 when not saving up). */
    val setAsideNow: Double,
    /** The steady amount: a twelfth of the bill. */
    val monthly: Double,
    val isDuePeriod: Boolean
) {
    val saving: Boolean get() = fund != null
    /** On track when this payday's set-aside is no more than the steady twelfth. */
    val onTrack: Boolean get() = setAsideNow <= monthly + 0.005
}

/**
 * Pure logic behind the annual bills fund. A yearly bill is a fixed cost active in exactly one
 * month. Its due pay period is the one starting in that month, matching how fixed costs are
 * counted at payday. Each payday sets aside what's still needed divided by the paydays left
 * (a twelfth from a standing start); in the due period the fund is released to pay the bill
 * and saving for next year begins.
 */
object AnnualFundCalculator {

    fun isYearly(tx: Transaction): Boolean = tx.category == TransactionCategory.FIXED_COST && tx.activeMonths?.size == 1

    fun yearlyBills(transactions: List<Transaction>): List<Transaction> = transactions.filter(::isYearly)

    private fun startMonth(period: PayPeriod): Int = Calendar.getInstance().apply { time = period.start }.get(Calendar.MONTH) + 1

    fun isDuePeriod(period: PayPeriod, dueMonth: Int): Boolean = startMonth(period) == dueMonth

    /** Paydays left to save before the bill, counting this one: 12 in the due period itself (saving for next year). */
    fun paydaysToSave(period: PayPeriod, dueMonth: Int): Int = ((dueMonth - startMonth(period)) + 12) % 12 + if (isDuePeriod(period, dueMonth)) 12 else 0

    /** Processes [fund] for [period] once: releases it in the due period, then sets aside this payday's share. */
    fun process(fund: AnnualFund, amount: Double, dueMonth: Int, period: PayPeriod): AnnualFund {
        val start = format(period)
        if (fund.processedPeriod == start) return fund
        val released = if (isDuePeriod(period, dueMonth)) fund.saved.coerceAtLeast(0.0) else 0.0
        val kept = fund.saved - released
        val setAside = ((abs(amount) - kept).coerceAtLeast(0.0) / paydaysToSave(period, dueMonth)).roundPennies()
        return fund.copy(saved = kept + setAside, processedPeriod = start, lastSetAside = setAside, lastReleased = released)
    }

    /** What the funds change this period's daily pot by: money released to pay bills, minus money set aside. */
    fun potAdjustment(funds: List<AnnualFund>, period: PayPeriod): Double {
        val start = format(period)
        return funds.filter { it.processedPeriod == start }.sumOf { it.lastReleased - it.lastSetAside }
    }

    /** Money set aside in funds, which isn't free to spend. */
    fun reserved(funds: List<AnnualFund>): Double = funds.sumOf { it.saved.coerceAtLeast(0.0) }

    fun statuses(transactions: List<Transaction>, funds: List<AnnualFund>, period: PayPeriod): List<AnnualBillStatus> =
        yearlyBills(transactions).map { bill ->
            val dueMonth = bill.activeMonths!!.single()
            val amount = abs(bill.amount)
            val fund = funds.firstOrNull { it.account == bill.account && key(it.info) == key(bill.info) }
            val setAside = when {
                fund == null -> 0.0
                fund.processedPeriod == format(period) -> fund.lastSetAside
                else -> process(fund, amount, dueMonth, period).lastSetAside
            }
            AnnualBillStatus(bill, amount, dueMonth, fund, setAside, (amount / 12).roundPennies(), isDuePeriod(period, dueMonth))
        }.sortedWith(compareBy<AnnualBillStatus> { paydaysToSave(period, it.dueMonth) }.thenBy { it.bill.info.lowercase() })

    /** Funds whose yearly bill is still in the Sheet; a deleted bill's fund holds nothing back. */
    fun matching(funds: List<AnnualFund>, transactions: List<Transaction>): List<AnnualFund> {
        val bills = yearlyBills(transactions).map { it.account to key(it.info) }.toSet()
        return funds.filter { (it.account to key(it.info)) in bills }
    }

    fun key(info: String): String = info.trim().lowercase(Locale.ROOT)

    private fun format(period: PayPeriod) = SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(period.start)

    private fun Double.roundPennies(): Double = kotlin.math.round(this * 100) / 100
}
