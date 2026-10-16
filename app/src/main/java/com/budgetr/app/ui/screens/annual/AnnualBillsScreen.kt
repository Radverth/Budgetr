package com.budgetr.app.ui.screens.annual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.util.AnnualBillStatus
import com.budgetr.app.util.toCurrencyString
import java.text.DateFormatSymbols
import java.util.Locale

private fun monthName(month: Int): String = DateFormatSymbols(Locale.UK).months[month - 1]

/** Yearly bills (insurance, MOT) saved up for a little each payday. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnualBillsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnnualBillsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    uiState.confirmStop?.let { status ->
        AlertDialog(
            onDismissRequest = viewModel::dismissStop,
            title = { Text("Stop saving for ${status.bill.info.trim()}?") },
            text = { Text("The ${(status.fund?.saved ?: 0.0).toCurrencyString()} set aside becomes free to spend again.") },
            confirmButton = { Button(onClick = viewModel::confirmStop) { Text("Stop saving") } },
            dismissButton = { TextButton(onClick = viewModel::dismissStop) { Text("Keep saving") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Annual bills") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        AnnualBillsContent(state = uiState, onSetSaving = viewModel::setSaving, modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
internal fun AnnualBillsContent(
    state: AnnualBillsUiState,
    onSetSaving: (AnnualBillStatus, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isLoading) {
        Column(modifier = modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                text = "Spread yearly bills across the year. Each payday sets a share aside, so the month a bill arrives isn't squeezed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        if (state.bills.isEmpty()) {
            item {
                Text(
                    text = "No yearly bills yet. Add a fixed cost and pick only the month it's due, for example Car insurance in March.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@LazyColumn
        }
        if (state.bills.any { it.saving }) {
            item {
                Text(
                    text = "${state.setAsideNow.toCurrencyString()} set aside this payday · ${state.savedTotal.toCurrencyString()} saved so far",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        items(state.bills, key = { "${it.bill.account}:${it.bill.info}" }) { status ->
            AnnualBillCard(status = status, showAccount = state.showAccount, onSetSaving = { onSetSaving(status, it) })
        }
    }
}

@Composable
private fun AnnualBillCard(status: AnnualBillStatus, showAccount: Boolean, onSetSaving: (Boolean) -> Unit) {
    val month = monthName(status.dueMonth)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = status.bill.info.trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull("${status.amount.toCurrencyString()} in $month", status.bill.account.takeIf { showAccount })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = status.saving,
                    onCheckedChange = onSetSaving,
                    modifier = Modifier.semantics { contentDescription = "Save up for ${status.bill.info.trim()}" }
                )
            }
            val fund = status.fund
            if (fund == null) {
                Text(
                    text = "Turn on to set aside about ${status.monthly.toCurrencyString()} each payday.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            LinearProgressIndicator(
                progress = if (status.amount > 0) (fund.saved / status.amount).toFloat().coerceIn(0f, 1f) else 0f,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = IncomeGreen,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            )
            Text(
                text = "${fund.saved.toCurrencyString()} of ${status.amount.toCurrencyString()} saved",
                style = MaterialTheme.typography.bodyMedium
            )
            // An icon and words, so the status never relies on colour alone
            val (good, text) = when {
                status.isDuePeriod -> true to "Paid from the fund this month (${fund.lastReleased.toCurrencyString()}). Saving for next year has started."
                status.onTrack -> true to "On track · ${status.setAsideNow.toCurrencyString()} set aside this payday"
                else -> false to "Catching up · ${status.setAsideNow.toCurrencyString()} this payday to be ready by $month"
            }
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (good) Icons.Default.CheckCircle else Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (good) IncomeGreen else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp).padding(top = 1.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }
    }
}
