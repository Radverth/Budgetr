package com.budgetr.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class SetupCalculatorTest {

    private val today = SimpleDateFormat("dd/MM/yyyy", Locale.UK).parse("01/10/2026")!!

    @Test
    fun `account names that Sheets would reject are caught`() {
        assertNotNull(SetupCalculator.accountNameProblem("  ", emptyList()))
        assertNotNull(SetupCalculator.accountNameProblem("Bills/Joint", emptyList()))
        assertNotNull(SetupCalculator.accountNameProblem("cover sheet", emptyList()))
        assertNotNull(SetupCalculator.accountNameProblem("monzo", listOf("Monzo")))
        assertNull(SetupCalculator.accountNameProblem("Joint account", listOf("Monzo")))
    }

    @Test
    fun `summary spreads what's left after fixed costs over the pay period`() {
        // Payday 26: 1 Oct 2026 sits in the period 25 Sep (Fri, payday moved back) to 26 Oct = 31 days
        val summary = SetupCalculator.summary(
            incomeInput = "£2,000",
            fixedCosts = listOf(SetupFixedCost("Rent", "900", "Current"), SetupFixedCost("Phone", "15.50", "Current")),
            payDay = 26,
            today = today
        )
        assertEquals(2000.0, summary.income, 0.001)
        assertEquals(915.5, summary.fixedCosts, 0.001)
        assertEquals(1084.5, summary.pot, 0.001)
        assertEquals(31, summary.days)
        assertEquals(34.98, summary.perDay, 0.01)
    }

    @Test
    fun `fixed costs above income leave nothing rather than a negative pot`() {
        val summary = SetupCalculator.summary("500", listOf(SetupFixedCost("Rent", "900", "Current")), 26, today)
        assertEquals(0.0, summary.pot, 0.001)
        assertEquals(0.0, summary.perDay, 0.001)
    }

    @Test
    fun `only named fixed costs with an amount are saved`() {
        val valid = SetupCalculator.validFixedCosts(
            listOf(
                SetupFixedCost("Rent", "900", "Current"),
                SetupFixedCost("", "20", "Current"),
                SetupFixedCost("Gym", "", "Current"),
                SetupFixedCost("Free trial", "0", "Current")
            )
        )
        assertEquals(listOf("Rent"), valid.map { it.first.name })
        assertEquals(900.0, valid.single().second, 0.001)
    }
}
