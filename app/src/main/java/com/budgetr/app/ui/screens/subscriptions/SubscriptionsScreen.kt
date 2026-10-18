package com.budgetr.app.ui.screens.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.util.Subscription
import com.budgetr.app.util.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private fun nextText(date: Date): String {
    val today = Calendar.getInstance()
    val day = Calendar.getInstance().apply { time = date }
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        same(day, today) -> "Today"
        same(day, tomorrow) -> "Tomorrow"
        else -> SimpleDateFormat("EEE d MMM", Locale.UK).format(date)
    }
}

/** Every bill and subscription, its next charge and what it costs over a year. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscriptions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        SubscriptionsContent(state = uiState, modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
internal fun SubscriptionsContent(state: SubscriptionsUiState, modifier: Modifier = Modifier) {
    if (state.isLoading) {
        Column(modifier = modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.subscriptions.isEmpty()) {
            item {
                Text(
                    "No bills or subscriptions yet. Add them as Fixed costs and they'll show here with their next charge.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@LazyColumn
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${state.monthlyTotal.toCurrencyString()} a month",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "${state.yearlyTotal.toCurrencyString()} a year across ${state.subscriptions.size} bills and subscriptions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        val changed = state.subscriptions.filter { it.change != null }
        if (changed.isNotEmpty()) {
            item {
                Text("Price changes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            items(changed, key = { "changed-${it.bill.account}-${it.bill.info}" }) { SubscriptionRow(it, state.showAccount, card = true) }
            item { Text("All, by next charge", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    state.subscriptions.forEachIndexed { index, subscription ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SubscriptionRow(subscription, state.showAccount, card = false)
                    }
                }
            }
        }
        item {
            Text(
                "Bills without a due day show on payday. Set the real day on the cash-flow forecast. A price change is spotted when a fixed cost's amount changes in your Sheet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubscriptionRow(subscription: Subscription, showAccount: Boolean, card: Boolean) {
    val content = @Composable {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    subscription.bill.info.trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(subscription.amount.toCurrencyString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            val next = subscription.nextCharge?.let { nextText(it) + if (subscription.onPayday) " (payday)" else "" } ?: "Not active"
            val often = if (subscription.timesAYear == 12) "monthly" else if (subscription.timesAYear == 1) "yearly" else "${subscription.timesAYear}× a year"
            Text(
                listOfNotNull("Next: $next", often, subscription.bill.account.takeIf { showAccount }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            subscription.change?.let { change ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (change.wentUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = if (change.wentUp) ExpenseRed else IncomeGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${if (change.wentUp) "Up" else "Down"} ${abs(change.difference).toCurrencyString()} from ${change.from.toCurrencyString()} on " +
                            SimpleDateFormat("d MMM", Locale.UK).format(change.on),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    if (card) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) { content() }
    } else {
        content()
    }
}
