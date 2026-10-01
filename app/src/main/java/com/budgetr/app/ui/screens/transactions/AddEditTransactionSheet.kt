package com.budgetr.app.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.budgetr.app.data.model.Debt
import com.budgetr.app.data.model.SavingsGoal
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.util.SpendTags
import com.budgetr.app.util.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class LinkOption(val label: String, val action: TransactionLinkAction?)

/**
 * Returns the pay date for the given [payDay] day-of-month.
 * - If today >= payDay: use payDay of this month.
 * - If today < payDay: use payDay of last month.
 * - If the resolved date falls on Saturday: move back 1 day to Friday.
 * - If it falls on Sunday: move back 2 days to Friday.
 */
internal fun getPayDate(payDay: Int = 26): String {
    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.DAY_OF_MONTH)

    if (today < payDay) {
        cal.add(Calendar.MONTH, -1)
    }
    cal.set(Calendar.DAY_OF_MONTH, payDay)

    // Adjust if pay day lands on a weekend → move to preceding Friday
    when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> cal.add(Calendar.DAY_OF_MONTH, -1)
        Calendar.SUNDAY -> cal.add(Calendar.DAY_OF_MONTH, -2)
    }

    return fmt.format(cal.time)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionSheet(
    existingTransaction: Transaction?,
    currentAccount: String,
    accounts: List<String>,
    addSaveCount: Int,
    payDay: Int = 26,
    debts: List<Debt> = emptyList(),
    savingsGoals: List<SavingsGoal> = emptyList(),
    spendingPromptEnabled: Boolean = true,
    spendingPromptThreshold: Double = 20.0,
    knownTags: List<String> = SpendTags.DEFAULTS,
    tagSuggestions: Map<String, String> = emptyMap(),
    onSave: (Transaction, TransactionLinkAction?) -> Unit,
    onSaveTransfer: (source: Transaction, destination: Transaction) -> Unit,
    onDismiss: () -> Unit,
    presetCategory: TransactionCategory? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(Date())
    val isEdit = existingTransaction != null

    val initialCategory = existingTransaction?.category ?: presetCategory ?: TransactionCategory.ONE_OFF_COST
    var date by remember {
        mutableStateOf(
            existingTransaction?.date ?: if (initialCategory == TransactionCategory.FIXED_COST || initialCategory == TransactionCategory.SALARY) getPayDate(payDay) else today
        )
    }
    var info by remember { mutableStateOf(existingTransaction?.info ?: "") }
    var amount by remember {
        mutableStateOf(existingTransaction?.amount?.let { if (it < 0) (-it).toString() else it.toString() } ?: "")
    }
    var category by remember { mutableStateOf(initialCategory) }
    var selectedAccount by remember { mutableStateOf(existingTransaction?.account ?: currentAccount) }
    var transferToAccount by remember { mutableStateOf<String?>(null) }
    var applyPayDate by remember { mutableStateOf(false) }
    var activeMonths by remember {
        mutableStateOf<Set<Int>>(existingTransaction?.activeMonths?.toSet() ?: emptySet())
    }
    var tag by remember { mutableStateOf(existingTransaction?.tag) }
    // Once the user picks a tag themselves, stop auto-filling it from the description
    var tagTouched by remember { mutableStateOf(existingTransaction != null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var tabExpanded by remember { mutableStateOf(false) }
    var transferToExpanded by remember { mutableStateOf(false) }
    var savedBanner by remember { mutableStateOf(false) }

    // "Link to" a debt/goal — a one-time balance adjustment applied alongside this transaction.
    // Only offered when adding (not editing), since it isn't reversible if the transaction is
    // later changed or deleted.
    val linkOptions = remember(debts, savingsGoals) {
        buildList {
            add(LinkOption("None", null))
            debts.forEach { debt ->
                add(LinkOption("Pay down \"${debt.name}\"", TransactionLinkAction(LinkTargetType.DEBT, debt.name, LinkDirection.DECREASE)))
                add(LinkOption("Add to \"${debt.name}\" (new charge)", TransactionLinkAction(LinkTargetType.DEBT, debt.name, LinkDirection.INCREASE)))
            }
            savingsGoals.forEach { goal ->
                add(LinkOption("Contribute to \"${goal.name}\"", TransactionLinkAction(LinkTargetType.GOAL, goal.name, LinkDirection.INCREASE)))
                add(LinkOption("Withdraw from \"${goal.name}\"", TransactionLinkAction(LinkTargetType.GOAL, goal.name, LinkDirection.DECREASE)))
            }
        }
    }
    var selectedLinkOption by remember { mutableStateOf(linkOptions.first()) }
    var linkExpanded by remember { mutableStateOf(false) }

    var showReflectionPrompt by remember { mutableStateOf(false) }

    // Auto-set date based on category and applyPayDate toggle
    LaunchedEffect(category) {
        if (!isEdit) {
            when (category) {
                TransactionCategory.FIXED_COST,
                TransactionCategory.SALARY -> date = getPayDate(payDay)
                TransactionCategory.TRANSFER -> date = if (applyPayDate) getPayDate(payDay) else today
                TransactionCategory.RECURRING_INCOME -> date = today // user picks their recurring day via date picker
                else -> {
                    date = today
                    applyPayDate = false
                }
            }
        }
    }

    LaunchedEffect(info) {
        if (!tagTouched) tag = tagSuggestions[info.trim().lowercase()]
    }

    LaunchedEffect(applyPayDate) {
        if (!isEdit && category == TransactionCategory.TRANSFER) {
            date = if (applyPayDate) getPayDate(payDay) else today
        }
    }

    // Reset form after a successful add (addSaveCount increments each time). Only applies to the
    // add flow — without the isEdit guard, opening an edit sheet after any earlier add in this
    // session would immediately wipe the fields this composable just loaded from
    // existingTransaction, since LaunchedEffect fires on first composition regardless of whether
    // the key actually changed.
    LaunchedEffect(addSaveCount) {
        if (!isEdit && addSaveCount > 0) {
            info = ""
            amount = ""
            transferToAccount = null
            applyPayDate = false
            tag = null
            tagTouched = false
            savedBanner = true
            date = when (category) {
                TransactionCategory.FIXED_COST, TransactionCategory.SALARY -> getPayDate(payDay)
                TransactionCategory.RECURRING_INCOME -> today
                else -> today
            }
        }
    }

    // Clear the "Saved!" banner after a short moment
    LaunchedEffect(savedBanner) {
        if (savedBanner) {
            kotlinx.coroutines.delay(2000)
            savedBanner = false
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(Date(millis))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEdit) "Edit Transaction" else "Add Transaction",
                    style = MaterialTheme.typography.titleLarge
                )
                AnimatedVisibility(visible = savedBanner) {
                    Surface(
                        color = IncomeGreen.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Saved!",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = IncomeGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Date field
            OutlinedTextField(
                value = date,
                onValueChange = {},
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) { Text("Pick") }
                },
                supportingText = if (category == TransactionCategory.RECURRING_INCOME) {
                    { Text("Pick the day this income recurs each month (e.g. 9th). Fridays replace weekends.", style = MaterialTheme.typography.labelSmall) }
                } else null
            )

            // Description
            OutlinedTextField(
                value = info,
                onValueChange = { info = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (£)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            // Category dropdown
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = category.displayName,
                    onValueChange = {},
                    label = { Text("Category") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    TransactionCategory.entries
                        .filter { it != TransactionCategory.UNKNOWN }
                        .forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.displayName) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                    if (cat != TransactionCategory.TRANSFER) {
                                        transferToAccount = null
                                        applyPayDate = false
                                    }
                                }
                            )
                        }
                }
            }

            // Account (source) dropdown
            ExposedDropdownMenuBox(
                expanded = tabExpanded,
                onExpandedChange = { tabExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedAccount,
                    onValueChange = {},
                    label = { Text(if (category == TransactionCategory.TRANSFER) "Transfer From" else "Account") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tabExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = tabExpanded,
                    onDismissRequest = { tabExpanded = false }
                ) {
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account) },
                            onClick = {
                                selectedAccount = account
                                tabExpanded = false
                                if (transferToAccount == account) transferToAccount = null
                            }
                        )
                    }
                }
            }

            // Transfer destination — only shown for Transfer category
            AnimatedVisibility(visible = category == TransactionCategory.TRANSFER) {
                ExposedDropdownMenuBox(
                    expanded = transferToExpanded,
                    onExpandedChange = { transferToExpanded = it }
                ) {
                    OutlinedTextField(
                        value = transferToAccount ?: "Select destination account",
                        onValueChange = {},
                        label = { Text("Transfer To") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = transferToExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = transferToExpanded,
                        onDismissRequest = { transferToExpanded = false }
                    ) {
                        accounts.filter { it != selectedAccount }.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account) },
                                onClick = {
                                    transferToAccount = account
                                    transferToExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Optional pay-date toggle for transfers
            AnimatedVisibility(visible = category == TransactionCategory.TRANSFER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Apply pay date",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Sets date to ${getPayDate(payDay)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = applyPayDate,
                        onCheckedChange = { applyPayDate = it }
                    )
                }
            }

            // Spending category — only for one-off costs
            AnimatedVisibility(visible = category == TransactionCategory.ONE_OFF_COST) {
                SpendTagPicker(
                    knownTags = knownTags,
                    selected = tag,
                    onSelect = {
                        tag = it
                        tagTouched = true
                    }
                )
            }

            // Month restriction — only shown for Fixed Cost
            AnimatedVisibility(visible = category == TransactionCategory.FIXED_COST) {
                ActiveMonthsPicker(
                    activeMonths = activeMonths,
                    onToggleMonth = { month ->
                        activeMonths = if (activeMonths.contains(month)) {
                            activeMonths - month
                        } else {
                            activeMonths + month
                        }
                    }
                )
            }

            // Link to a debt/goal — only offered for new cost transactions, since the balance
            // adjustment isn't reversible if this transaction is edited or deleted later.
            val showLinkPicker = !isEdit &&
                (category == TransactionCategory.ONE_OFF_COST || category == TransactionCategory.FIXED_COST) &&
                linkOptions.size > 1
            AnimatedVisibility(visible = showLinkPicker) {
                ExposedDropdownMenuBox(
                    expanded = linkExpanded,
                    onExpandedChange = { linkExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedLinkOption.label,
                        onValueChange = {},
                        label = { Text("Link to a debt or goal (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = linkExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = linkExpanded,
                        onDismissRequest = { linkExpanded = false }
                    ) {
                        linkOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedLinkOption = option
                                    linkExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val parsedAmount = amount.toDoubleOrNull() ?: 0.0
            val isTransfer = category == TransactionCategory.TRANSFER
            val saveEnabled = info.isNotBlank() && amount.isNotBlank() &&
                    (!isTransfer || isEdit || transferToAccount != null)

            val performSave = {
                val signedAmount = when (category) {
                    TransactionCategory.INCOME,
                    TransactionCategory.SALARY,
                    TransactionCategory.RECURRING_INCOME -> parsedAmount
                    else -> -parsedAmount
                }

                if (isTransfer && !isEdit && transferToAccount != null) {
                    val source = Transaction(
                        rowIndex = 0,
                        date = date,
                        info = info,
                        amount = -parsedAmount,
                        category = TransactionCategory.TRANSFER,
                        account = selectedAccount
                    )
                    val destination = Transaction(
                        rowIndex = 0,
                        date = date,
                        info = info,
                        amount = parsedAmount,
                        category = TransactionCategory.TRANSFER,
                        account = transferToAccount!!
                    )
                    onSaveTransfer(source, destination)
                } else {
                    val resolvedActiveMonths = if (category == TransactionCategory.FIXED_COST && activeMonths.isNotEmpty()) {
                        activeMonths.sorted()
                    } else null
                    onSave(
                        Transaction(
                            rowIndex = existingTransaction?.rowIndex ?: 0,
                            date = date,
                            info = info,
                            amount = signedAmount,
                            category = category,
                            account = selectedAccount,
                            activeMonths = resolvedActiveMonths,
                            tag = if (category == TransactionCategory.ONE_OFF_COST) SpendTags.normalise(tag) else null
                        ),
                        if (showLinkPicker) selectedLinkOption.action else null
                    )
                }
            }

            if (showReflectionPrompt) {
                SpendingReflectionDialog(
                    amount = parsedAmount,
                    onSaveAnyway = {
                        showReflectionPrompt = false
                        performSave()
                    },
                    onReconsider = { showReflectionPrompt = false }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text(if (isEdit) "Cancel" else "Done") }

                Button(
                    onClick = {
                        val needsReflection = !isEdit && spendingPromptEnabled &&
                            category == TransactionCategory.ONE_OFF_COST &&
                            parsedAmount >= spendingPromptThreshold
                        if (needsReflection) showReflectionPrompt = true else performSave()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = saveEnabled
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun SpendingReflectionDialog(amount: Double, onSaveAnyway: () -> Unit, onReconsider: () -> Unit) {
    AlertDialog(
        onDismissRequest = onReconsider,
        title = { Text("Before you spend ${amount.toCurrencyString()}…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Do you really need this right now?")
                Text("Would waiting 24 hours change your mind?")
                Text("Is there a cheaper way to get the same thing?")
            }
        },
        confirmButton = {
            TextButton(onClick = onSaveAnyway) { Text("Save anyway") }
        },
        dismissButton = {
            TextButton(onClick = onReconsider, colors = ButtonDefaults.textButtonColors(contentColor = ExpenseRed)) {
                Text("Let me reconsider")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SpendTagPicker(
    knownTags: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    var showNewTagDialog by remember { mutableStateOf(false) }
    // A tag typed in this session isn't in knownTags until it's saved, so keep it visible
    val tags = if (selected != null && knownTags.none { it.equals(selected, ignoreCase = true) }) {
        knownTags + selected
    } else knownTags

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Spending category (optional)", style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tags.forEach { tag ->
                val isSelected = tag.equals(selected, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(if (isSelected) null else tag) },
                    label = { Text(tag, style = MaterialTheme.typography.labelMedium) }
                )
            }
            FilterChip(
                selected = false,
                onClick = { showNewTagDialog = true },
                label = { Text("+ New", style = MaterialTheme.typography.labelMedium) }
            )
        }
    }

    if (showNewTagDialog) {
        var newTag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewTagDialog = false },
            title = { Text("New spending category") },
            text = {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it.take(24) },
                    label = { Text("Name, e.g. Pets") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSelect(SpendTags.normalise(newTag))
                        showNewTagDialog = false
                    },
                    enabled = SpendTags.normalise(newTag) != null
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showNewTagDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private val MONTH_NAMES = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ActiveMonthsPicker(
    activeMonths: Set<Int>,
    onToggleMonth: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Active months", style = MaterialTheme.typography.bodyMedium)
            if (activeMonths.isEmpty()) {
                Text(
                    "All months",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Text(
            text = "Leave all unselected to show every month.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MONTH_NAMES.forEachIndexed { index, name ->
                val month = index + 1
                FilterChip(
                    selected = activeMonths.contains(month),
                    onClick = { onToggleMonth(month) },
                    label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}
