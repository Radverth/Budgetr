package com.budgetr.app.ui.screens.balances

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.data.model.AccountBalance
import com.budgetr.app.data.model.BalanceRollover
import com.budgetr.app.ui.theme.ExpenseRed
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                ) {
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = balance.account,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
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
            }

            Text(
                text = balance.remainingBalance.toCurrencyString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (balance.remainingBalance >= 0) IncomeGreen else ExpenseRed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isClickable) {
                Text(
                    text = "Tap to view transactions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (rollover != null) {
                        Text(
                            text = rollover.rolloverAmount.toCurrencyString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (rollover.rolloverAmount >= 0) IncomeGreen else ExpenseRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Recorded ${rollover.recordedDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
