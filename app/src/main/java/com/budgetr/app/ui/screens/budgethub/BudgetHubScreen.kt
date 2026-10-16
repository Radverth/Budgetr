package com.budgetr.app.ui.screens.budgethub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.ui.screens.daily.DailyTargetCard
import com.budgetr.app.ui.screens.daily.DailyTransferDialog
import com.budgetr.app.ui.screens.daily.TransferKind
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.util.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Locale

/** The Budget tab: every planning tool in one place, each with a live summary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetHubScreen(
    onOpenBudgets: () -> Unit,
    onOpenDailyTarget: () -> Unit,
    onOpenPaydayPlan: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenForecast: () -> Unit,
    onOpenReview: () -> Unit,
    viewModel: BudgetHubViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var transferKind by remember { mutableStateOf<TransferKind?>(null) }

    // Leaves composition while a sub-screen is open, so this re-runs on return
    LaunchedEffect(Unit) { viewModel.refreshPaydayFlag() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val daily = uiState.daily
    if (transferKind != null && daily != null) {
        DailyTransferDialog(
            state = daily,
            kind = transferKind!!,
            onConfirm = { amount, day ->
                viewModel.addTransfer(amount, day)
                transferKind = null
            },
            onDismiss = { transferKind = null }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Budget", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        BudgetHubContent(
            state = uiState,
            onOpenBudgets = onOpenBudgets,
            onOpenDailyTarget = onOpenDailyTarget,
            onOpenPaydayPlan = onOpenPaydayPlan,
            onOpenHistory = onOpenHistory,
            onOpenRules = onOpenRules,
            onOpenForecast = onOpenForecast,
            onOpenReview = onOpenReview,
            onTransfer = { transferKind = it },
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

@Composable
internal fun BudgetHubContent(
    state: BudgetHubUiState,
    onOpenBudgets: () -> Unit,
    onOpenDailyTarget: () -> Unit,
    onOpenPaydayPlan: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenForecast: () -> Unit,
    onOpenReview: () -> Unit = {},
    onTransfer: (TransferKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val paydayText = state.nextPayday?.let { SimpleDateFormat("EEE d MMM", Locale.UK).format(it) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.daily?.let { daily ->
            item {
                DailyTargetCard(state = daily, onOpen = onOpenDailyTarget, onTransfer = onTransfer, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    HubRow(
                        icon = Icons.Default.Category,
                        title = "Category budgets",
                        subtitle = when {
                            state.budgetCount == 0 -> "Give categories like Groceries their own limit"
                            state.budgetsOver > 0 -> "${state.budgetCount} budgets · ${state.budgetsOver} over"
                            else -> "${state.budgetCount} budgets · all on track"
                        },
                        subtitleColor = if (state.budgetsOver > 0) ExpenseRed else null,
                        onClick = onOpenBudgets
                    )
                    HubDivider()
                    HubRow(
                        icon = Icons.Default.Today,
                        title = "Daily spend target",
                        subtitle = state.daily?.let { "${it.target.base.toCurrencyString()} a day · borrow or pay forward" }
                            ?: "Borrow or pay forward between days",
                        onClick = onOpenDailyTarget
                    )
                    HubDivider()
                    HubRow(
                        icon = Icons.Default.ShowChart,
                        title = "Cash-flow forecast",
                        subtitle = state.forecastWarning?.let { "${it.account} dips below £0 on ${SimpleDateFormat("EEE d MMM", Locale.UK).format(it.date)}" }
                            ?: if (state.forecastReady) "Every account stays above £0 until payday" else "Your balances day by day until payday",
                        subtitleColor = if (state.forecastWarning != null) ExpenseRed else null,
                        onClick = onOpenForecast
                    )
                    HubDivider()
                    HubRow(
                        icon = Icons.Default.CalendarMonth,
                        title = "Payday plan",
                        subtitle = when {
                            state.paydayPlanPending -> "Ready to plan this payday"
                            paydayText != null -> "Next payday $paydayText"
                            else -> "Give every pound a job"
                        },
                        subtitleColor = if (state.paydayPlanPending) MaterialTheme.colorScheme.primary else null,
                        onClick = onOpenPaydayPlan
                    )
                    HubDivider()
                    HubRow(
                        icon = Icons.Default.History,
                        title = "Spending history",
                        subtitle = when (state.historyPeriods) {
                            0 -> "Starts at your next payday"
                            1 -> "1 pay period saved"
                            else -> "${state.historyPeriods} pay periods saved"
                        },
                        onClick = onOpenHistory
                    )
                    HubDivider()
                    HubRow(
                        icon = Icons.Default.FactCheck,
                        title = "Pay period review",
                        subtitle = when {
                            state.reviewReady -> "Your last pay period is ready to review"
                            state.reviewCount > 0 -> "How your last pay period went"
                            else -> "Your first review appears after payday"
                        },
                        subtitleColor = if (state.reviewReady) MaterialTheme.colorScheme.primary else null,
                        onClick = onOpenReview
                    )
                    HubDivider()
                    HubRow(
                        icon = Icons.Default.Rule,
                        title = "Category rules",
                        subtitle = when (state.ruleCount) {
                            0 -> "Fill in categories automatically, e.g. TESCO → Groceries"
                            1 -> "1 rule fills in categories for you"
                            else -> "${state.ruleCount} rules fill in categories for you"
                        },
                        onClick = onOpenRules
                    )
                }
            }
        }
    }
}

@Composable
private fun HubDivider() = HorizontalDivider(
    modifier = Modifier.padding(start = 56.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
)

@Composable
private fun HubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    subtitleColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}
