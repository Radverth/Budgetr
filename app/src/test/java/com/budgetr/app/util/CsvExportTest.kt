package com.budgetr.app.util

import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExportTest {

    @Test fun `header and rows with amounts to two places`() {
        val csv = CsvExport.build(
            listOf(Transaction(2, "14/10/2026", "Tesco", -4.2, TransactionCategory.ONE_OFF_COST, "Monzo", tag = "Groceries"))
        )
        assertEquals(
            "Date,Description,Amount,Type,Spending category,Account\r\n" +
                "14/10/2026,Tesco,-4.20,One Off Cost,Groceries,Monzo\r\n",
            csv
        )
    }

    @Test fun `commas quotes and line breaks are quoted`() {
        assertEquals("\"Fish, chips\"", CsvExport.escape("Fish, chips"))
        assertEquals("\"The \"\"good\"\" pub\"", CsvExport.escape("The \"good\" pub"))
        assertEquals("\"two\nlines\"", CsvExport.escape("two\nlines"))
    }

    @Test fun `formula-looking text is made safe but negative numbers are not touched`() {
        assertEquals("'=SUM(A1)", CsvExport.escape("=SUM(A1)"))
        assertEquals("'@cmd", CsvExport.escape("@cmd"))
        assertEquals("-4.20", CsvExport.escape("-4.20"))
    }
}
