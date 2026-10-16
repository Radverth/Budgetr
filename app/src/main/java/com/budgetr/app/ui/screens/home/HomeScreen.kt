package com.budgetr.app.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.data.model.AccountBalance
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.FixedCostOrange
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.ui.theme.heroGradient
import com.budgetr.app.ui.screens.daily.DailyTargetCard
import com.budgetr.app.ui.screens.daily.DailyTransferDialog
import com.budgetr.app.ui.screens.daily.TransferKind
import com.budgetr.app.util.SafeToSpend
import com.budgetr.app.util.toCurrencyString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToPaydayPlan: () -> Unit,
    onNavigateToDailyTarget: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToForecast: () -> Unit = {},
    onNavigateToReview: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    var transferKind by remember { mutableStateOf<TransferKind?>(null) }
    val dailyTargetForDialog = uiState.dailyTarget
    if (transferKind != null && dailyTargetForDialog != null) {
        DailyTransferDialog(
            state = dailyTargetForDialog,
            kind = transferKind!!,
            onConfirm = { amount, day ->
                viewModel.addDailyTransfer(amount, day)
                transferKind = null
            },
            onDismiss = { transferKind = null }
        )
    }

    // Home leaves composition while the plan screen is open, so this re-runs on return
    LaunchedEffect(Unit) { viewModel.syncPaydayPlanPending() }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    if (uiState.updateAvailable) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdate,
            title = { Text("Update Available") },
            text = {
                Text("Budgetr v${uiState.updateVersion} is available. Download the latest APK to get bug fixes and new features.")
            },
            confirmButton = {
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uiState.updateUrl)))
                    viewModel.dismissUpdate()
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissUpdate) { Text("Later") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddTransaction) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        val pullRefreshState = rememberPullToRefreshState()
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(true) { viewModel.refresh() }
        }
        LaunchedEffect(uiState.isRefreshing) {
            if (!uiState.isRefreshing) pullRefreshState.endRefresh()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        HomeHeader(
                            userName = uiState.userName,
                            totalAvailable = uiState.totalAvailable,
                            topPadding = paddingValues.calculateTopPadding(),
                            onOpenSettings = onNavigateToSettings
                        )
                    }

                    val dailyTarget = uiState.dailyTarget
                    val safeToSpend = uiState.safeToSpend
                    if (uiState.showDailyTarget && dailyTarget != null) {
                        item {
                            DailyTargetCard(
                                state = dailyTarget,
                                onOpen = onNavigateToDailyTarget,
                                onTransfer = { transferKind = it },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            )
                        }
                    } else if (safeToSpend != null && uiState.accountBalances.isNotEmpty()) {
                        item {
                            SafeToSpendCard(
                                safeToSpend = safeToSpend,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            )
                        }
                    }

                    val attention = attentionItems(
                        paydayPlanPending = uiState.paydayPlanPending,
                        alerts = uiState.budgetAlerts,
                        resetDays = uiState.budgetResetDays,
                        suggestions = uiState.goalSuggestions,
                        reviews = uiState.recurringReviews,
                        dismissed = uiState.dismissedAttention,
                        cashFlowWarnings = uiState.cashFlowWarnings,
                        periodReview = uiState.periodReview
                    )
                    if (attention.isNotEmpty()) {
                        item {
                            NeedsAttentionCard(
                                items = attention,
                                onPlanPayday = onNavigateToPaydayPlan,
                                onViewBudgets = onNavigateToBudgets,
                                onAddToGoal = viewModel::addUnderspendToGoal,
                                onRemindLater = viewModel::dismissRecurringReview,
                                onDismiss = viewModel::dismissAttention,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                onOpenForecast = onNavigateToForecast,
                                onOpenReview = onNavigateToReview
                            )
                        }
                    }

                    if (uiState.showPeriodSection) {
                        item {
                            ThisPeriodCard(
                                income = uiState.totalIncome,
                                fixedCosts = uiState.totalFixedCosts,
                                oneOffCosts = uiState.totalOneOffCosts,
                                byTag = uiState.oneOffByTag,
                                streakDays = uiState.noSpendStreakDays,
                                trend = uiState.spendingTrend,
                                onSeeHistory = onNavigateToInsights,
                                onBudgets = onNavigateToBudgets,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            )
                        }
                    }

                    if (uiState.showGoalsSection && (uiState.goalsCount > 0 || uiState.debtCount > 0)) {
                        item {
                            GoalsGlanceCard(
                                goalsCount = uiState.goalsCount,
                                avgGoalProgress = uiState.avgGoalProgress,
                                debtCount = uiState.debtCount,
                                totalDebt = uiState.totalDebt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }

                    if (uiState.showAccountsSection && uiState.accountBalances.isNotEmpty()) {
                        item {
                            AccountBalancesChartCard(
                                balances = uiState.accountBalances,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        alpha = if (pullRefreshState.isRefreshing || pullRefreshState.progress > 0f) 1f else 0f
                    }
            )
        }
    }
}

@Composable
internal fun HomeHeader(userName: String?, totalAvailable: Double, topPadding: Dp, onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 28.dp, bottomStart = 28.dp))
            .background(heroGradient())
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = topPadding + 16.dp, bottom = 28.dp)
        ) {
            // Settings lives here now that the bottom bar is for everyday tabs
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Budgetr",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
            if (userName != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Hi, $userName",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Total Available",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Text(
                text = totalAvailable.toCurrencyString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                // Lighter tints than the standard semantic colors — they're calibrated for
                // light surfaces and would look muddy against this dark gradient.
                color = if (totalAvailable >= 0) Color(0xFF7EE8B0) else Color(0xFFFFA8A0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SafeToSpendCard(safeToSpend: SafeToSpend, modifier: Modifier = Modifier) {
    val days = safeToSpend.daysLeft
    val daysText = if (days == 1) "Payday tomorrow" else "$days days to payday"
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (safeToSpend.isOverspent) "Overspent this pay period" else "Safe to spend today",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = if (safeToSpend.isOverspent) {
                    "${(-safeToSpend.available).toCurrencyString()} over"
                } else {
                    safeToSpend.perDay.toCurrencyString()
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    safeToSpend.isOverspent -> ExpenseRed
                    safeToSpend.isOverBudgeted -> MaterialTheme.colorScheme.tertiary
                    else -> IncomeGreen
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (safeToSpend.isOverspent) {
                    "$daysText · try to hold off on one-off costs"
                } else {
                    "${safeToSpend.thisWeek.toCurrencyString()} this week · $daysText"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (!safeToSpend.isOverspent && safeToSpend.reserved > 0) {
                Text(
                    text = if (safeToSpend.isOverBudgeted) {
                        "Your category budgets have ${safeToSpend.reserved.toCurrencyString()} left, but only " +
                            "${safeToSpend.available.toCurrencyString()} is left overall. Lower a budget to free some up."
                    } else {
                        "${safeToSpend.reserved.toCurrencyString()} kept aside for your category budgets"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (safeToSpend.isOverBudgeted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun AccountBalancesChartCard(
    balances: List<AccountBalance>,
    modifier: Modifier = Modifier
) {
    val maxBalance = balances.maxOfOrNull { it.remainingBalance }?.coerceAtLeast(1.0) ?: 1.0

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Account Balances",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            balances.forEachIndexed { index, balance ->
                AccountBalanceBarItem(balance = balance, maxBalance = maxBalance)
                if (index < balances.size - 1) {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AccountBalanceBarItem(balance: AccountBalance, maxBalance: Double) {
    val targetProgress = (balance.remainingBalance / maxBalance).coerceIn(0.0, 1.0).toFloat()
    val animatedProgress = remember(balance.account) { Animatable(0f) }
    LaunchedEffect(balance.remainingBalance) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(targetProgress, animationSpec = tween(800))
    }

    val barColor = if (balance.remainingBalance >= 0) IncomeGreen else ExpenseRed
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = balance.account,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = balance.remainingBalance.toCurrencyString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = barColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        balance.itemCostThisMonth?.let { cost ->
            Text(
                text = "This month: ${cost.toCurrencyString()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Spacer(Modifier.height(6.dp))
        val currentTrackColor = trackColor
        val currentBarColor = barColor
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val radius = 4.dp.toPx()
            drawRoundRect(
                color = currentTrackColor,
                size = size,
                cornerRadius = CornerRadius(radius)
            )
            if (animatedProgress.value > 0f) {
                drawRoundRect(
                    color = currentBarColor,
                    size = Size(size.width * animatedProgress.value, size.height),
                    cornerRadius = CornerRadius(radius)
                )
            }
        }
    }
}

@Composable
private fun GoalsGlanceCard(
    goalsCount: Int,
    avgGoalProgress: Float,
    debtCount: Int,
    totalDebt: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (goalsCount > 0) {
                GlanceRow(
                    emoji = "🎯",
                    title = if (goalsCount == 1) "1 goal" else "$goalsCount goals",
                    detail = "${(avgGoalProgress * 100).toInt()}% avg. progress",
                    titleColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            if (debtCount > 0) {
                GlanceRow(
                    emoji = "💳",
                    title = totalDebt.toCurrencyString() + " owed",
                    detail = if (debtCount == 1) "1 debt" else "$debtCount debts",
                    titleColor = ExpenseRed
                )
            }
        }
    }
}

@Composable
private fun GlanceRow(
    emoji: String,
    title: String,
    detail: String,
    titleColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = emoji, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

