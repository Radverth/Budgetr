package com.budgetr.app.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetr.app.data.model.PendingChange
import com.budgetr.app.data.model.PendingKind
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.util.toCurrencyString

/** Shown above the list while changes saved on the phone haven't reached the Sheet. */
@Composable
internal fun PendingSyncBanner(
    changes: List<PendingChange>,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val failed = changes.count { it.failedReason != null }
    val waiting = changes.size - failed
    if (failed == 0 && waiting == 0) return
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (failed > 0) ExpenseRed.copy(alpha = 0.10f) else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            if (waiting > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (waiting == 1) "1 change waiting to sync" else "$waiting changes waiting to sync",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(18.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = onSyncNow) { Text("Sync now") }
                    }
                }
            }
            if (failed > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (failed == 1) "1 change couldn't be saved" else "$failed changes couldn't be saved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onReview) { Text("Review") }
                }
            }
        }
    }
}

/** Refused changes, each with Google's reason and Retry / Discard. */
@Composable
internal fun FailedChangesDialog(
    changes: List<PendingChange>,
    onRetry: (Long) -> Unit,
    onDiscard: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val failed = changes.filter { it.failedReason != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Changes that couldn't be saved") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Retry sends it again. Discard drops it, and your Google Sheet keeps what it has.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                failed.forEachIndexed { index, change ->
                    if (index > 0) HorizontalDivider()
                    val tx = change.transaction
                    val action = when (change.kind) {
                        PendingKind.ADD -> "Add"
                        PendingKind.UPDATE -> "Edit"
                        PendingKind.DELETE -> "Delete"
                    }
                    Text(
                        text = "$action · ${tx.info} · ${tx.amount.toCurrencyString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tx.account} · ${tx.date}\n${change.failedReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onDiscard(change.id) }) { Text("Discard", color = ExpenseRed) }
                        TextButton(onClick = { onRetry(change.id) }) { Text("Retry") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
