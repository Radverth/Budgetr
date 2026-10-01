package com.budgetr.app.util

import com.budgetr.app.data.model.PeriodSummary
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

private fun historyDateFormat() = SimpleDateFormat("dd/MM/yyyy", Locale.UK)

/** Reads and writes rows of the Sheet's "History" tab. */
object PeriodSummaryRow {
    val HEADER = listOf("Period start", "Period end", "Income", "Fixed costs", "One-off costs", "End balance", "One-offs by category")

    private const val UNTAGGED = "Untagged"

    fun encode(summary: PeriodSummary): List<String> = listOf(
        summary.periodStart,
        summary.periodEnd,
        money(summary.income),
        money(summary.fixedCosts),
        money(summary.oneOffCosts),
        money(summary.endBalance),
        encodeTags(summary.byTag)
    )

    /** Null for blank or unreadable rows. Amounts may come back formatted, e.g. "£1,200.00". */
    fun decode(row: List<String>): PeriodSummary? {
        val start = row.getOrNull(0)?.trim().orEmpty()
        if (start.isEmpty()) return null
        return PeriodSummary(
            periodStart = start,
            periodEnd = row.getOrNull(1)?.trim().orEmpty(),
            income = parseMoney(row.getOrNull(2)),
            fixedCosts = parseMoney(row.getOrNull(3)),
            oneOffCosts = parseMoney(row.getOrNull(4)),
            endBalance = parseMoney(row.getOrNull(5)),
            byTag = decodeTags(row.getOrNull(6).orEmpty())
        )
    }

    /** "Groceries=120.50; Fun=30.00; Untagged=10.00" — readable in the Sheet and easy to parse back. */
    fun encodeTags(byTag: List<TagSpend>): String =
        byTag.joinToString("; ") { "${cleanTag(it.tag ?: UNTAGGED)}=${money(it.amount)}" }

    fun decodeTags(text: String): List<TagSpend> = text.split(";").mapNotNull { part ->
        val split = part.lastIndexOf('=')
        if (split <= 0) return@mapNotNull null
        val tag = part.substring(0, split).trim().ifEmpty { return@mapNotNull null }
        val amount = part.substring(split + 1).trim().toDoubleOrNull() ?: return@mapNotNull null
        TagSpend(if (tag == UNTAGGED) null else tag, amount)
    }

    /** Keeps the separators out of tag names, and stops a leading +, - or = being read as a formula. */
    private fun cleanTag(tag: String) = tag.replace(Regex("[=;]"), " ").trim().trimStart('+', '-', '@').trim()

    private fun money(value: Double) = String.format(Locale.ROOT, "%.2f", value)

    private fun parseMoney(text: String?): Double =
        text?.replace(Regex("[£,\\s]"), "")?.toDoubleOrNull() ?: 0.0
}

/** Pure calculations behind the History tab and the Insights screen. */
object HistoryCalculator {

    /** Summarises the period [start]..[end] (inclusive) from the transactions still in the
     *  Sheet at payday. Fixed costs use the active-months rule for the month the period ends
     *  in, since most of a late-month pay period falls in that month. */
    fun summarise(transactions: List<Transaction>, start: Date, end: Date, endBalance: Double): PeriodSummary {
        val endMonth = Calendar.getInstance().apply { time = end }.get(Calendar.MONTH) + 1
        val income = transactions
            .filter {
                it.category == TransactionCategory.INCOME ||
                    it.category == TransactionCategory.SALARY ||
                    it.category == TransactionCategory.RECURRING_INCOME
            }
            .sumOf { it.amount }
        val fmt = historyDateFormat()
        return PeriodSummary(
            periodStart = fmt.format(start),
            periodEnd = fmt.format(end),
            income = income,
            fixedCosts = spendForCategory(transactions, TransactionCategory.FIXED_COST, endMonth),
            oneOffCosts = spendForCategory(transactions, TransactionCategory.ONE_OFF_COST, endMonth),
            endBalance = endBalance,
            byTag = SpendTags.oneOffSpendByTag(transactions)
        )
    }

    /** The day before [nextStart], i.e. the last day of the period that ends there. */
    fun dayBefore(nextStart: Date): Date = Calendar.getInstance().apply {
        time = nextStart
        add(Calendar.DAY_OF_MONTH, -1)
    }.time

    /** Oldest first, unreadable dates dropped, at most the latest [limit] periods. */
    fun latest(summaries: List<PeriodSummary>, limit: Int = 6): List<PeriodSummary> {
        val fmt = historyDateFormat()
        return summaries
            .mapNotNull { s -> runCatching { fmt.parse(s.periodStart) }.getOrNull()?.let { it to s } }
            .sortedBy { it.first }
            .map { it.second }
            .distinctBy { it.periodStart }
            .takeLast(limit)
    }

    /** Per-category comparison of [history] (oldest first) with the current period so far.
     *  Average is over every period in [history], counting periods with no spend as zero. */
    fun tagInsights(history: List<PeriodSummary>, thisPeriod: List<TagSpend>): List<TagInsight> {
        val tags = (history.flatMap { it.byTag } + thisPeriod).mapNotNull { it.tag }
            .distinctBy { it.lowercase() }
        return tags.map { tag ->
            fun amountIn(list: List<TagSpend>) = list.filter { it.tag.equals(tag, ignoreCase = true) }.sumOf { it.amount }
            val average = if (history.isEmpty()) 0.0 else history.sumOf { amountIn(it.byTag) } / history.size
            TagInsight(
                tag = tag,
                average = average,
                lastPeriod = history.lastOrNull()?.let { amountIn(it.byTag) },
                thisPeriod = amountIn(thisPeriod),
                suggestedLimit = suggestedLimit(average)
            )
        }.sortedWith(compareByDescending<TagInsight> { it.average }.thenByDescending { it.thisPeriod })
    }

    /** The average rounded up to the next £5, as a starting point for a category budget. */
    fun suggestedLimit(average: Double): Double? =
        if (average <= 0) null else ceil(average / 5.0) * 5.0
}

data class TagInsight(
    val tag: String,
    val average: Double,
    /** Null when there's no history yet. */
    val lastPeriod: Double?,
    val thisPeriod: Double,
    val suggestedLimit: Double?
)
