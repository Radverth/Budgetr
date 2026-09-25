package com.budgetr.app.ui.screens.balances

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.data.model.AccountBalance
import com.budgetr.app.data.model.BalanceRollover
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.FixedCostOrange
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.util.toCurrencyString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountBalancesScreen(
    onNavigateToTransactions: ((String) -> Unit)? = null,
    viewModel: AccountBalancesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    // Rename dialog
    uiState.renameAccount?.let { account ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRenameDialog,
            title = { Text("Rename Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Renaming will also update the Google Sheet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    OutlinedTextField(
                        value = uiState.renameText,
                        onValueChange = viewModel::setRenameText,
                        label = { Text("Account name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmRename,
                    enabled = uiState.renameText.isNotBlank() && !uiState.isRenaming
                ) {
                    if (uiState.isRenaming) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRenameDialog) { Text("Cancel") }
            }
        )
    }

    // Add account dialog
    if (uiState.showAddAccount) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAddAccountDialog,
            title = { Text("New Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This creates a new worksheet in your Google Sheet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    OutlinedTextField(
                        value = uiState.newAccountName,
                        onValueChange = viewModel::setNewAccountName,
                        label = { Text("Account name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmAddAccount,
                    enabled = uiState.newAccountName.isNotBlank() && !uiState.isAddingAccount
                ) {
                    if (uiState.isAddingAccount) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAddAccountDialog) { Text("Cancel") }
            }
        )
    }

    // Delete account confirmation dialog
    uiState.deleteAccount?.let { account ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteAccountDialog,
            title = { Text("Delete Account") },
            text = {
                Text(
                    "Delete \"${account.account}\"? This will permanently remove the account and all its transactions from your Google Sheet. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDeleteAccount,
                    enabled = !uiState.isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    if (uiState.isDeletingAccount) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteAccountDialog) { Text("Cancel") }
            }
        )
    }

    // Rollover edit dialog
    uiState.rolloverEditAccount?.let { accountName ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRolloverEditDialog,
            title = { Text("Edit Rollover for $accountName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the balance carried over from the previous pay period.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    OutlinedTextField(
                        value = uiState.rolloverEditText,
                        onValueChange = viewModel::setRolloverEditText,
                        label = { Text("Rollover amount (£)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmRolloverEdit,
                    enabled = !uiState.isRecordingRollover
                ) {
                    if (uiState.isRecordingRollover) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRolloverEditDialog) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = viewModel::recordAllRollovers,
                        enabled = uiState.balances.isNotEmpty() && !uiState.isRecordingRollover
                    ) {
                        Icon(Icons.Default.Loop, contentDescription = "Record pay period rollover", tint = IncomeGreen)
                    }
                    IconButton(onClick = viewModel::showAddAccountDialog) {
                        Icon(Icons.Default.Add, contentDescription = "Add account", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
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
                .padding(paddingValues)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            if (uiState.balances.isEmpty() && !uiState.isRefreshing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No balance data found.\nPull down to refresh.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary header card
                    if (uiState.totalAvailable != 0.0 || uiState.totalIncome != 0.0) {
                        item {
                            SummaryHeaderCard(
                                totalAvailable = uiState.totalAvailable,
                                totalIncome = uiState.totalIncome,
                                totalOutgoings = uiState.totalOutgoings,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)
                            )
                        }
                    }

                    if (uiState.goalsCount > 0 || uiState.debtCount > 0) {
                        item {
                            GoalsGlanceCard(
                                goalsCount = uiState.goalsCount,
                                avgGoalProgress = uiState.avgGoalProgress,
                                debtCount = uiState.debtCount,
                                totalDebt = uiState.totalDebt,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Your Accounts",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    items(uiState.balances) { balance ->
                        val rollover = uiState.rollovers.find { it.account == balance.account }
                        AccountBalanceDetailCard(
                            balance = balance,
                            rollover = rollover,
                            isClickable = onNavigateToTransactions != null,
                            onClick = { onNavigateToTransactions?.invoke(balance.account) },
                            onRename = { viewModel.showRenameDialog(balance) },
                            onDelete = { viewModel.showDeleteAccountDialog(balance) },
                            onEditRollover = { viewModel.showRolloverEditDialog(balance.account) },
                            onDeleteRollover = { rollover?.let { viewModel.deleteRollover(balance.account) } }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
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
private fun SummaryHeaderCard(
    totalAvailable: Double,
    totalIncome: Double,
    totalOutgoings: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "Total Available",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = totalAvailable.toCurrencyString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (totalAvailable >= 0) IncomeGreen else ExpenseRed
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                    Text(
                        text = totalIncome.toCurrencyString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = IncomeGreen
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Outgoings",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                    Text(
                        text = totalOutgoings.toCurrencyString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ExpenseRed
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                    val remaining = totalIncome - totalOutgoings
                    Text(
                        text = remaining.toCurrencyString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (remaining >= 0) IncomeGreen else ExpenseRed
                    )
                }
            }

            // Mini spending bar
            if (totalIncome > 0) {
                val spentFraction = (totalOutgoings / totalIncome).coerceIn(0.0, 1.0).toFloat()
                val animatedProgress = remember(totalOutgoings, totalIncome) { Animatable(0f) }
                LaunchedEffect(spentFraction) {
                    animatedProgress.snapTo(0f)
                    animatedProgress.animateTo(spentFraction, animationSpec = tween(800))
                }
                val barColor = if (spentFraction > 0.9f) ExpenseRed else FixedCostOrange
                val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                val currentBarColor = barColor
                val currentTrackColor = trackColor
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                ) {
                    val radius = 3.dp.toPx()
                    drawRoundRect(color = currentTrackColor, size = size, cornerRadius = CornerRadius(radius))
                    if (animatedProgress.value > 0f) {
                        drawRoundRect(
                            color = currentBarColor,
                            size = Size(size.width * animatedProgress.value, size.height),
                            cornerRadius = CornerRadius(radius)
                        )
                    }
                }
                Text(
                    text = "${(spentFraction * 100).toInt()}% of income spent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
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
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (goalsCount > 0) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎯 $goalsCount ${if (goalsCount == 1) "goal" else "goals"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "${(avgGoalProgress * 100).toInt()}% average progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                    )
                }
            }
            if (debtCount > 0) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "💳 ${totalDebt.toCurrencyString()} owed",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ExpenseRed
                    )
                    Text(
                        text = "across $debtCount ${if (debtCount == 1) "debt" else "debts"} — see Goals tab",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountBalanceDetailCard(
    balance: AccountBalance,
    rollover: BalanceRollover?,
    isClickable: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEditRollover: () -> Unit,
    onDeleteRollover: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(if (isClickable) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = balance.account,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isClickable) {
                        Text(
                            text = "Tap to view transactions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
                Text(
                    text = balance.remainingBalance.toCurrencyString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (balance.remainingBalance >= 0) IncomeGreen else ExpenseRed
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename account",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete account",
                        tint = ExpenseRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

            balance.itemCostThisMonth?.let { cost ->
                BalanceRow(label = "This Month", value = cost.toCurrencyString())
            }

            balance.subscriptionCost?.let { sub ->
                BalanceRow(label = "Subscription", value = sub.toCurrencyString())
            }

            balance.variance?.let { v ->
                BalanceRow(
                    label = "Variance",
                    value = v.toCurrencyString(),
                    valueColor = if (v >= 0) IncomeGreen else ExpenseRed
                )
            }

            // Rollover section
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Previous Period Rollover",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (rollover != null) {
                        Text(
                            text = rollover.rolloverAmount.toCurrencyString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (rollover.rolloverAmount >= 0) IncomeGreen else ExpenseRed
                        )
                        Text(
                            text = "Recorded ${rollover.recordedDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    } else {
                        Text(
                            text = "None recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEditRollover, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit rollover",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (rollover != null) {
                        IconButton(onClick = onDeleteRollover, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear rollover",
                                tint = ExpenseRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            balance.shouldBuySub?.let { rec ->
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Text(
                    text = "Recommendation: $rec",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BalanceRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}
