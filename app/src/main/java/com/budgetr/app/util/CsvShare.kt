package com.budgetr.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.budgetr.app.data.model.Transaction
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes [transactions] to a CSV file and opens Android's share sheet for it. */
object CsvShare {
    fun share(context: Context, transactions: List<Transaction>, label: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // One file at a time is enough; clear out older exports
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
        val file = File(dir, "budgetr-$label-$stamp.csv")
        file.writeText(CsvExport.build(transactions))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Budgetr transactions")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export transactions").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** "all-accounts" or the account name, safe for a file name. */
    fun label(account: String?, isAll: Boolean): String =
        if (isAll || account == null) "all-accounts" else account.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "account" }
}
