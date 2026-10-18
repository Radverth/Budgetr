package com.budgetr.app.ui.screens.transactions

import androidx.compose.material3.Button
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.platform.LocalContext
import com.budgetr.app.util.CsvShare
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import com.budgetr.app.util.DailyTargetWording
import com.budgetr.app.util.toCurrencyString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    var showDateRangePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (uiState.showFailedChanges) {
        FailedChangesDialog(
            changes = uiState.pendingChanges,
            onRetry = viewModel::retryChange,
            onDiscard = viewModel::discardChange,
            onDismiss = viewModel::dismissFailedChanges
        )
    }

    BackHandler(enabled = uiState.isSelecting) { viewModel.cancelSelection() }

    LaunchedEffect(uiState.undoable) {
        val deleted = uiState.undoable ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Deleted \"${deleted.info}\"",
            actionLabel = "Undo",
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(deleted)
        viewModel.clearUndo()
    }

    if (showDateRangePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setDateRange(pickerState.selectedStartDateMillis, pickerState.selectedEndDateMillis)
                        showDateRangePicker = false
                    },
                    enabled = pickerState.selectedStartDateMillis != null
                ) { Text("Show") }
            },
            dismissButton = { TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
        }
    }

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

    val addAccount = uiState.transactionToEdit?.account ?: uiState.accountForAdding
    if (uiState.showAddSheet && addAccount != null) {
        AddEditTransactionSheet(
            existingTransaction = uiState.transactionToEdit,
            currentAccount = addAccount,
            accounts = uiState.accounts,
            addSaveCount = uiState.addSaveCount,
            payDay = uiState.payDay,
            debts = uiState.debts,
            savingsGoals = uiState.savingsGoals,
            spendingPromptEnabled = uiState.spendingPromptEnabled,
            spendingPromptThreshold = uiState.spendingPromptThreshold,
            knownTags = uiState.knownTags,
            tagSuggestions = uiState.tagSuggestions,
            categoryRules = uiState.categoryRules,
            descriptionHistory = uiState.descriptionHistory,
            onSave = viewModel::saveTransaction,
            onSaveTransfer = viewModel::saveTransfer,
            onDismiss = viewModel::dismissSheet
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                actions = {
                    // Add transaction button
                    IconButton(onClick = viewModel::showAddSheet, enabled = uiState.accountForAdding != null && !uiState.isSelecting) {
                        Icon(Icons.Default.Add, contentDescription = "Add transaction", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Filter by dates")
                    }
                    // Exports exactly what the list is showing: tab, filters, search and dates
                    IconButton(
                        onClick = {
                            CsvShare.share(
                                context,
                                uiState.visibleTransactions,
                                CsvShare.label(uiState.selectedAccount, uiState.isAllAccounts)
                            )
                        },
                        enabled = uiState.visibleTransactions.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export as CSV")
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
                    selectedTabIndex = if (uiState.isAllAccounts) 0 else uiState.accounts.indexOf(uiState.selectedAccount).coerceAtLeast(0) + 1,
                    edgePadding = 16.dp
                ) {
                    Tab(
                        selected = uiState.isAllAccounts,
                        onClick = { viewModel.selectAccount(ALL_ACCOUNTS) },
                        text = { Text("All") }
                    )
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
                        val selectableCount = uiState.visibleTransactions.count { it.isCategorisable }
                        TextButton(onClick = viewModel::selectAllShown, enabled = !uiState.isRefreshing && !uiState.isAssigningCategory && selectableCount > 0) {
                            Text(if (selectableCount > 0 && uiState.selectedRows.size == selectableCount) "Deselect all" else "Select all shown")
                        }
                        TextButton(onClick = viewModel::showBulkCategoryDialog, enabled = uiState.selectedRows.isNotEmpty() && !uiState.isRefreshing && !uiState.isAssigningCategory) {
                            Text("Set category")
                        }
                    }
                }
            }

            PendingSyncBanner(
                changes = uiState.pendingChanges,
                isSyncing = uiState.isSyncing,
                onSyncNow = viewModel::syncNow,
                onReview = viewModel::showFailedChanges,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )

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

            if (uiState.searchQuery.isNotBlank() && !uiState.isAllAccounts) {
                TextButton(
                    onClick = { viewModel.selectAccount(ALL_ACCOUNTS) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { Text("Search all accounts") }
            }

            uiState.dateRange?.let { (start, end) ->
                val fmt = SimpleDateFormat("d MMM", Locale.UK)
                FilterChip(
                    selected = true,
                    onClick = { viewModel.setDateRange(null, null) },
                    label = { Text("${fmt.format(Date(start))} – ${fmt.format(Date(end))}") },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear dates", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

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
                val shown = uiState.visibleTransactions
                if (shown.isEmpty() && !uiState.isRefreshing) {
                    val unfiltered = uiState.searchQuery.isBlank() && uiState.categoryFilter == null && uiState.dateRange == null
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = when {
                                uiState.accounts.isEmpty() -> "No accounts yet.\nAdd one from the Accounts tab."
                                uiState.searchQuery.isNotBlank() -> "No matching transactions."
                                uiState.tagFilter != null -> "No one-off costs tagged ${uiState.tagFilter}."
                                uiState.dateRange != null -> "Nothing between those dates."
                                else -> "No transactions yet."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                        if (unfiltered && uiState.accountForAdding != null) {
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = viewModel::showAddSheet) { Text("Add a transaction") }
                        }
                    }
                } else {
                    // Day headers only make sense when the list is in date order
                    val byDay = uiState.sortOrder == SortOrder.DATE_DESC || uiState.sortOrder == SortOrder.DATE_ASC
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        if (!uiState.isSelecting) {
                            item(key = "hint") {
                                Text(
                                    text = "Tap to edit · swipe ← to delete, → to set a category · long-press to select",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }
                        val groups = if (byDay) shown.groupBy { it.date }.toList() else listOf("" to shown)
                        groups.forEach { (date, dayTransactions) ->
                            if (byDay) {
                                item(key = "day-$date") { DayHeader(date = date, totals = uiState.dayTotals[date]) }
                            }
                            items(items = dayTransactions, key = { "${it.account}-${it.rowIndex}" }) { transaction ->
                                SwipeableTransactionRow(
                                    transaction = transaction,
                                    swipeEnabled = !uiState.isSelecting && !uiState.isAssigningCategory,
                                    onDelete = { viewModel.deleteWithUndo(transaction) },
                                    onSetCategory = { viewModel.categoriseOne(transaction) }
                                ) {
                                    TransactionItem(
                                        transaction = transaction,
                                        onClick = {
                                            if (uiState.isSelecting) viewModel.toggleSelection(transaction)
                                            else viewModel.showEditSheet(transaction)
                                        },
                                        onLongClick = { viewModel.startSelectionWith(transaction) },
                                        onDelete = { viewModel.deleteWithUndo(transaction) },
                                        onSetCategory = if (transaction.isCategorisable) {
                                            { viewModel.categoriseOne(transaction) }
                                        } else null,
                                        showAccount = uiState.isAllAccounts,
                                        showDate = !byDay,
                                        selectionMode = uiState.isSelecting,
                                        selected = transaction.key in uiState.selectedRows,
                                        selectionEnabled = !uiState.isRefreshing && !uiState.isAssigningCategory,
                                        onToggleSelection = { viewModel.toggleSelection(transaction) }
                                    )
                                }
                            }
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

/** "Wed 14 Oct" (or Today / Yesterday), with that day's one-off spend against its daily
 *  target when the daily spend target covers the day. */
@Composable
internal fun DayHeader(date: String, totals: DayTotals?) {
    val parse = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
    val parsed = runCatching { parse.parse(date) }.getOrNull()
    val label = parsed?.let { d ->
        val today = parse.format(Date())
        val yesterday = parse.format(Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000))
        when (date) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> SimpleDateFormat("EEE d MMM", Locale.UK).format(d)
        }
    } ?: date
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f)
        )
        if (totals != null && (totals.spent > 0 || totals.target > 0)) {
            val over = totals.spent > totals.target + 0.005
            Text(
                text = DailyTargetWording.spentOf(totals.spent, totals.target),
                style = MaterialTheme.typography.labelMedium,
                color = if (over) ExpenseRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/** Swipe left to delete, right to set a spending category (one-off costs only). The row springs
 *  back either way; a delete hides it at once and the snackbar offers Undo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTransactionRow(
    transaction: Transaction,
    swipeEnabled: Boolean,
    onDelete: () -> Unit,
    onSetCategory: () -> Unit,
    content: @Composable () -> Unit
) {
    val canCategorise = transaction.isCategorisable
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.StartToEnd -> if (canCategorise) onSetCategory()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = swipeEnabled && canCategorise,
        enableDismissFromEndToStart = swipeEnabled,
        backgroundContent = {
            val toDelete = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val toCategory = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        when {
                            toDelete -> ExpenseRed
                            toCategory -> MaterialTheme.colorScheme.primary
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toDelete) Arrangement.End else Arrangement.Start
            ) {
                if (toDelete) Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                if (toCategory) Icon(Icons.Default.Label, contentDescription = null, tint = Color.White)
            }
        }
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TransactionItem(
    transaction: Transaction,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSetCategory: (() -> Unit)? = null,
    showAccount: Boolean = false,
    /** Off under a day header, which already shows the date. */
    showDate: Boolean = true,
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(CardDefaults.shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select")
            // Swipes aren't usable with a screen reader, so offer the same actions here
            .semantics {
                customActions = buildList {
                    add(CustomAccessibilityAction("Delete") { onDelete(); true })
                    onSetCategory?.let { add(CustomAccessibilityAction("Set spending category") { it(); true }) }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = if (selectionMode) 4.dp else 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Description and amount share the top line; date, category and (on the All tab)
            // account get the full second line, so nothing is squeezed on narrow phones
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.info,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = transaction.amount.toCurrencyString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = amountColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(
                    text = buildList {
                        if (showDate) add(transaction.date)
                        add(transaction.tag ?: transaction.category.displayName)
                        if (showAccount) add(transaction.account)
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (transaction.pending) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = " Not synced yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() },
                    enabled = selectionEnabled && transaction.isCategorisable,
                    modifier = Modifier.semantics { contentDescription = "Select ${transaction.info}, ${transaction.date}" }
                )
            }
        }
    }
}
