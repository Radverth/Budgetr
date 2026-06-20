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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.data.model.AccountBalance
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.FixedCostOrange
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.util.toCurrencyString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddTransaction: () -> Unit,
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
                            topPadding = paddingValues.calculateTopPadding()
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SummaryStatCard(
                                label = "Income",
                                amount = uiState.totalIncome,
                                color = IncomeGreen,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryStatCard(
                                label = "Outgoings",
                                amount = uiState.totalOutgoings,
                                color = ExpenseRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (uiState.totalIncome > 0 || uiState.totalOutgoings > 0) {
                        item {
                            SpendingBreakdownCard(
                                totalIncome = uiState.totalIncome,
                                totalFixedCosts = uiState.totalFixedCosts,
                                totalOneOffCosts = uiState.totalOneOffCosts,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }

                    if (uiState.accountBalances.isNotEmpty()) {
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
private fun HomeHeader(userName: String?, totalAvailable: Double, topPadding: Dp) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 24.dp, bottomStart = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = topPadding + 16.dp, bottom = 28.dp)
        ) {
            Text(
                text = "Budgetr",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
            if (userName != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Hi, $userName",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Total Available",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = totalAvailable.toCurrencyString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (totalAvailable >= 0) IncomeGreen else ExpenseRed
            )
        }
    }
}

@Composable
private fun SummaryStatCard(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = amount.toCurrencyString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun SpendingBreakdownCard(
    totalIncome: Double,
    totalFixedCosts: Double,
    totalOneOffCosts: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Spending Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpendingDonutChart(
                    totalIncome = totalIncome,
                    totalFixedCosts = totalFixedCosts,
                    totalOneOffCosts = totalOneOffCosts,
                    modifier = Modifier.size(140.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChartLegendItem(
                        label = "Fixed Costs",
                        amount = totalFixedCosts,
                        color = FixedCostOrange
                    )
                    ChartLegendItem(
                        label = "One-off Costs",
                        amount = totalOneOffCosts,
                        color = ExpenseRed
                    )
                    val remaining = totalIncome - totalFixedCosts - totalOneOffCosts
                    ChartLegendItem(
                        label = "Remaining",
                        amount = remaining,
                        color = IncomeGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun SpendingDonutChart(
    totalIncome: Double,
    totalFixedCosts: Double,
    totalOneOffCosts: Double,
    modifier: Modifier = Modifier
) {
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(totalIncome, totalFixedCosts, totalOneOffCosts) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(1000))
    }
    val progress = animatedProgress.value

    val total = maxOf(totalIncome, totalFixedCosts + totalOneOffCosts, 0.01)
    val fixedSweep = (totalFixedCosts / total * 360f * progress).toFloat()
    val oneOffSweep = (totalOneOffCosts / total * 360f * progress).toFloat()
    val remainingSweep = ((total - totalFixedCosts - totalOneOffCosts).coerceAtLeast(0.0) / total * 360f * progress).toFloat()

    val trackColor = Color.Gray.copy(alpha = 0.15f)

    Canvas(modifier = modifier) {
        val strokeWidth = 28.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        val style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth),
            topLeft = topLeft,
            size = arcSize
        )

        var currentAngle = -90f

        if (fixedSweep > 0f) {
            drawArc(
                color = FixedCostOrange,
                startAngle = currentAngle,
                sweepAngle = fixedSweep,
                useCenter = false,
                style = style,
                topLeft = topLeft,
                size = arcSize
            )
            currentAngle += fixedSweep
        }
        if (oneOffSweep > 0f) {
            drawArc(
                color = ExpenseRed,
                startAngle = currentAngle,
                sweepAngle = oneOffSweep,
                useCenter = false,
                style = style,
                topLeft = topLeft,
                size = arcSize
            )
            currentAngle += oneOffSweep
        }
        if (remainingSweep > 0.1f) {
            drawArc(
                color = IncomeGreen,
                startAngle = currentAngle,
                sweepAngle = remainingSweep,
                useCenter = false,
                style = style,
                topLeft = topLeft,
                size = arcSize
            )
        }
    }
}

@Composable
private fun ChartLegendItem(label: String, amount: Double, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = CircleShape)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = amount.toCurrencyString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
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
                fontWeight = FontWeight.Medium
            )
            Text(
                text = balance.remainingBalance.toCurrencyString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = barColor
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
