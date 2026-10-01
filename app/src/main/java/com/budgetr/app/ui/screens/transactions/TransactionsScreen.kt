package com.budgetr.app.ui.screens.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.data.model.SortOrder
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.FixedCostOrange
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.ui.theme.TransferGrey
import com.budgetr.app.util.toCurrencyString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.isSelecting) { viewModel.cancelSelection() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (uiState.showBulkCategoryDialog) {
        var tag by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = viewModel::dismissBulkCategoryDialog,
            title = {
                val n = uiState.selectedRows.size
                Text(if (n == 1) "Categorise 1 transaction" else "Categorise $n transactions")
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.isAssigningCategory) {
                        CircularProgressIndicator()
                        Text("Saving spending categories…")
                    } else {
                        Text("This replaces the spending category on every selected transaction.")
                        SpendTagPicker(knownTags = uiState.knownTags, selected = tag, onSelect = { tag = it })
                        if (tag == null) Text("No category selected. Clear categories removes their existing categories.")
                        uiState.bulkCategoryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.assignSpendingCategory(tag) },
                    enabled = !uiState.isAssigningCategory && uiState.selectedRows.isNotEmpty() && uiState.bulkCategoryError == null
                ) { Text(if (tag == null) "Clear categories" else "Apply category") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBulkCategoryDialog, enabled = !uiState.isAssigningCategory) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val selectedAccount = uiState.selectedAccount
    if (uiState.showAddSheet && selectedAccount != null) {
        AddEditTransactionSheet(
            existingTransaction = uiState.transactionToEdit,
            currentAccount = selectedAccount,
            accounts = uiState.accounts,
            addSaveCount = uiState.addSaveCount,
            payDay = uiState.payDay,
            debts = uiState.debts,
            savingsGoals = uiState.savingsGoals,
            spendingPromptEnabled = uiState.spendingPromptEnabled,
            spendingPromptThreshold = uiState.spendingPromptThreshold,
            knownTags = uiState.knownTags,
            tagSuggestions = uiState.tagSuggestions,
            onSave = viewModel::saveTransaction,
            onSaveTransfer = viewModel::saveTransfer,
            onDismiss = viewModel::dismissSheet
        )
    }

    uiState.transactionToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete \"${tx.info}\"?") },
            confirmButton = {
                TextButton(onClick = viewModel::deleteTransaction) {
                    Text("Delete", color = ExpenseRed)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                actions = {
                    // Add transaction button
                    IconButton(onClick = viewModel::showAddSheet, enabled = uiState.selectedAccount != null && !uiState.isSelecting) {
                        Icon(Icons.Default.Add, contentDescription = "Add transaction", tint = MaterialTheme.colorScheme.primary)
                    }
                    // Sort button
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = order.displayName,
                                            fontWeight = if (uiState.sortOrder == order) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Account tabs — scrollable so tabs never get squeezed as accounts are added or renamed
            if (uiState.accounts.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.accounts.indexOf(uiState.selectedAccount).coerceAtLeast(0),
                    edgePadding = 16.dp
                ) {
                    uiState.accounts.forEach { account ->
                        Tab(
                            selected = uiState.selectedAccount == account,
                            onClick = { viewModel.selectAccount(account) },
                            text = {
                                Text(
                                    text = account,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            if (uiState.isSelecting) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${uiState.selectedRows.size} selected", modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::cancelSelection, enabled = !uiState.isAssigningCategory) { Text("Cancel") }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val selectableCount = uiState.transactions.count {
                            it.category == TransactionCategory.ONE_OFF_COST && it.rowIndex > 1
                        }
                        TextButton(onClick = viewModel::selectAllShown, enabled = !uiState.isRefreshing && !uiState.isAssigningCategory && selectableCount > 0) {
                            Text(if (selectableCount > 0 && uiState.selectedRows.size == selectableCount) "Deselect all" else "Select all shown")
                        }
                        TextButton(onClick = viewModel::showBulkCategoryDialog, enabled = uiState.selectedRows.isNotEmpty() && !uiState.isRefreshing && !uiState.isAssigningCategory) {
                            Text("Set category")
                        }
                    }
                }
            } else {
                TextButton(
                    onClick = viewModel::startSelection,
                    enabled = uiState.selectedAccount != null && !uiState.isRefreshing,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { Text("Bulk assign spending categories") }
            }

            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Search transactions...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            // Category filters
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.categoryFilter == null,
                        onClick = { viewModel.setCategoryFilter(null) },
                        label = { Text("All") }
                    )
                }
                items(TransactionCategory.entries.filter { it != TransactionCategory.UNKNOWN }) { cat ->
                    FilterChip(
                        selected = uiState.categoryFilter == cat,
                        onClick = { viewModel.setCategoryFilter(if (uiState.categoryFilter == cat) null else cat) },
                        label = { Text(cat.displayName) }
                    )
                }
            }

            // Spending category filters — only while viewing one-off costs
            if (uiState.categoryFilter == TransactionCategory.ONE_OFF_COST) {
                LazyRow(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Keep the selected tag visible even once nothing uses it, so it can be cleared
                    val tagChips = uiState.tagFilter
                        ?.takeIf { selected -> uiState.knownTags.none { it.equals(selected, ignoreCase = true) } }
                        ?.let { uiState.knownTags + it }
                        ?: uiState.knownTags
                    items(tagChips) { tag ->
                        FilterChip(
                            selected = uiState.tagFilter == tag,
                            onClick = { viewModel.setTagFilter(if (uiState.tagFilter == tag) null else tag) },
                            label = { Text(tag) }
                        )
                    }
                }
            }

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
                    .nestedScroll(pullRefreshState.nestedScrollConnection)
            ) {
                if (uiState.transactions.isEmpty() && !uiState.isRefreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = when {
                                uiState.accounts.isEmpty() -> "No accounts yet.\nAdd one from the Accounts tab."
                                uiState.searchQuery.isNotBlank() -> "No matching transactions."
                                uiState.tagFilter != null -> "No one-off costs tagged ${uiState.tagFilter}."
                                else -> "No transactions found.\nPull down to refresh."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(
                            items = uiState.transactions,
                            key = { "${it.account}-${it.rowIndex}" }
                        ) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                onEdit = { viewModel.showEditSheet(transaction) },
                                onDelete = { viewModel.confirmDelete(transaction) },
                                selectionMode = uiState.isSelecting,
                                selected = transaction.rowIndex in uiState.selectedRows,
                                selectionEnabled = !uiState.isRefreshing && !uiState.isAssigningCategory,
                                onToggleSelection = { viewModel.toggleSelection(transaction) }
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
}

@Composable
private fun TransactionItem(
    transaction: Transaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    selectionEnabled: Boolean = true,
    onToggleSelection: () -> Unit = {}
) {
    val amountColor = when (transaction.category) {
        TransactionCategory.INCOME,
        TransactionCategory.SALARY,
        TransactionCategory.RECURRING_INCOME -> IncomeGreen
        TransactionCategory.TRANSFER -> TransferGrey
        TransactionCategory.FIXED_COST -> FixedCostOrange
        else -> ExpenseRed
    }

    Card(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                Text(
                    text = transaction.info,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        text = transaction.tag ?: transaction.category.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = amountColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = transaction.amount.toCurrencyString(),
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp)
            )

            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() },
                    enabled = selectionEnabled && transaction.category == TransactionCategory.ONE_OFF_COST && transaction.rowIndex > 1,
                    modifier = Modifier.semantics { contentDescription = "Select ${transaction.info}, ${transaction.date}" }
                )
            } else Row {
                IconButton(
                    onClick = onEdit,
                    modifier = androidx.compose.ui.Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = androidx.compose.ui.Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = androidx.compose.ui.Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = ExpenseRed,
                        modifier = androidx.compose.ui.Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
