package com.budgetr.app.util

import com.budgetr.app.data.model.PeriodSummary
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class PeriodSummaryRowTest {

    private val summary = PeriodSummary(
        periodStart = "25/09/2026",
        periodEnd = "25/10/2026",
        income = 2500.0,
        fixedCosts = 1200.5,
        oneOffCosts = 160.0,
        endBalance = 340.25,
        byTag = listOf(TagSpend("Groceries", 120.5), TagSpend(null, 39.5))
    )

    @Test
    fun `round trips through a sheet row`() {
        val row = PeriodSummaryRow.encode(summary)
        assertEquals("Groceries=120.50; Untagged=39.50", row[6])
        assertEquals(summary, PeriodSummaryRow.decode(row))
    }

    @Test
    fun `reads formatted amounts`() {
        val row = listOf("25/09/2026", "25/10/2026", "£2,500.00", "£1,200.50", "160", "-£12.00", "")
        val decoded = PeriodSummaryRow.decode(row)!!
        assertEquals(2500.0, decoded.income, 0.001)
        assertEquals(-12.0, decoded.endBalance, 0.001)
        assertEquals(emptyList<TagSpend>(), decoded.byTag)
    }

    @Test
    fun `blank rows are skipped`() {
        assertNull(PeriodSummaryRow.decode(listOf("", "x")))
    }

    @Test
    fun `separators and formula characters are kept out of tags`() {
        assertEquals("Food  drink=5.00", PeriodSummaryRow.encodeTags(listOf(TagSpend("=Food;=drink", 5.0))))
    }
}

class HistoryCalculatorTest {

    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)

    private fun tx(
        amount: Double,
        category: TransactionCategory,
        tag: String? = null,
        activeMonths: List<Int>? = null,
        date: String = "01/10/2026"
    ) = Transaction(rowIndex = 0, date = date, info = "", amount = amount, category = category, account = "A", activeMonths = activeMonths, tag = tag)

    private fun summary(start: String, vararg byTag: TagSpend) =
        PeriodSummary(start, start, 0.0, 0.0, byTag.sumOf { it.amount }, 0.0, byTag.toList())

    @Test
    fun `summarise totals each kind of transaction`() {
        val result = HistoryCalculator.summarise(
            listOf(
                tx(2000.0, TransactionCategory.SALARY),
                tx(50.0, TransactionCategory.RECURRING_INCOME),
                tx(-800.0, TransactionCategory.FIXED_COST),
                tx(-60.0, TransactionCategory.FIXED_COST, activeMonths = listOf(3)),
                tx(-30.0, TransactionCategory.ONE_OFF_COST, tag = "Fun"),
                tx(-100.0, TransactionCategory.TRANSFER),
                // Already in the next period: left out
                tx(-45.0, TransactionCategory.ONE_OFF_COST, tag = "Fun", date = "26/10/2026"),
                tx(75.0, TransactionCategory.RECURRING_INCOME, date = "09/11/2026")
            ),
            period = PayPeriod(fmt.parse("25/09/2026")!!, fmt.parse("26/10/2026")!!),
            endBalance = 120.0
        )
        assertEquals(2050.0, result.income, 0.001)
        assertEquals(800.0, result.fixedCosts, 0.001)
        assertEquals(30.0, result.oneOffCosts, 0.001)
        assertEquals(listOf(TagSpend("Fun", 30.0)), result.byTag)
        assertEquals("25/10/2026", result.periodEnd)
    }

    @Test
    fun `day before handles month ends`() {
        assertEquals("30/09/2026", fmt.format(HistoryCalculator.dayBefore(fmt.parse("01/10/2026")!!)))
    }

    @Test
    fun `latest sorts by date and keeps the newest`() {
        val list = listOf(summary("26/12/2025"), summary("24/10/2025"), summary("26/11/2025"), summary("bad"))
        assertEquals(listOf("26/11/2025", "26/12/2025"), HistoryCalculator.latest(list, 2).map { it.periodStart })
    }

    @Test
    fun `tag insights average over all periods and suggest a rounded limit`() {
        val history = listOf(
            summary("26/08/2026", TagSpend("Groceries", 100.0)),
            summary("25/09/2026", TagSpend("Groceries", 142.0), TagSpend("Fun", 40.0))
        )
        val insights = HistoryCalculator.tagInsights(history, listOf(TagSpend("groceries", 30.0), TagSpend("Pets", 12.0)))

        assertEquals(listOf("Groceries", "Fun", "Pets"), insights.map { it.tag })
        val groceries = insights[0]
        assertEquals(121.0, groceries.average, 0.001)
        assertEquals(142.0, groceries.lastPeriod!!, 0.001)
        assertEquals(30.0, groceries.thisPeriod, 0.001)
        assertEquals(125.0, groceries.suggestedLimit!!, 0.001)
        assertEquals(20.0, insights[1].average, 0.001)
        assertNull(insights[2].suggestedLimit)
    }
}
