package com.budgetr.app.ui.screens.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.data.model.Debt
import com.budgetr.app.data.model.SavingsGoal
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.util.DebtPayoffCalculator
import com.budgetr.app.util.SavingsGoalCalculator
import com.budgetr.app.util.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(viewModel: GoalsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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

    if (uiState.showGoalDialog) {
        GoalDialog(uiState = uiState, viewModel = viewModel)
    }
    if (uiState.showDebtDialog) {
        DebtDialog(uiState = uiState, viewModel = viewModel)
    }

    uiState.goalToDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteGoalDialog,
            title = { Text("Delete Goal") },
            text = { Text("Delete \"${goal.name}\"? This can't be undone.") },
            confirmButton = {
                Button(onClick = viewModel::confirmDeleteGoal, colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteGoalDialog) { Text("Cancel") } }
        )
    }

    uiState.debtToDelete?.let { debt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDebtDialog,
            title = { Text("Delete Debt") },
            text = { Text("Delete \"${debt.name}\"? This can't be undone.") },
            confirmButton = {
                Button(onClick = viewModel::confirmDeleteDebt, colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteDebtDialog) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goals") },
                actions = {
                    IconButton(onClick = viewModel::showAddGoalDialog) {
                        Icon(Icons.Default.Savings, contentDescription = "Add savings goal", tint = IncomeGreen)
                    }
                    IconButton(onClick = viewModel::showAddDebtDialog) {
                        Icon(Icons.Default.CreditCard, contentDescription = "Add debt", tint = ExpenseRed)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isRefreshing && uiState.goals.isEmpty() && uiState.debts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("Savings Goals") }
            if (uiState.goals.isEmpty()) {
                item { EmptyHint("No savings goals yet. Tap the savings icon above to add one.") }
            } else {
                items(uiState.goals, key = { "goal-${it.name}" }) { goal ->
                    SavingsGoalCard(
                        goal = goal,
                        onEdit = { viewModel.showEditGoalDialog(goal) },
                        onDelete = { viewModel.showDeleteGoalDialog(goal) }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { SectionHeader("Debts") }
            if (uiState.debts.isEmpty()) {
                item { EmptyHint("No debts tracked. Tap the card icon above to add one.") }
            } else {
                items(uiState.debts, key = { "debt-${it.name}" }) { debt ->
                    DebtCard(
                        debt = debt,
                        onEdit = { viewModel.showEditDebtDialog(debt) },
                        onDelete = { viewModel.showDeleteDebtDialog(debt) }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
}

@Composable
private fun SavingsGoalCard(goal: SavingsGoal, onEdit: () -> Unit, onDelete: () -> Unit) {
    val progress = SavingsGoalCalculator.progress(goal.savedAmount, goal.targetAmount)
    val monthsRemaining = goal.targetDate?.let { monthsUntil(it) }
    val suggestion = monthsRemaining?.let {
        SavingsGoalCalculator.suggestedMonthlyContribution(goal.savedAmount, goal.targetAmount, it)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit goal", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete goal", tint = ExpenseRed, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text(
                text = "${goal.savedAmount.toCurrencyString()} saved",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = IncomeGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = IncomeGreen,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "of ${goal.targetAmount.toCurrencyString()} target",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = IncomeGreen,
                    maxLines = 1
                )
            }

            if (goal.targetDate != null) {
                Text(
                    text = if (suggestion != null) {
                        "Save ${suggestion.toCurrencyString()}/month to reach this by ${goal.targetDate}"
                    } else {
                        "Target date: ${goal.targetDate}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun DebtCard(debt: Debt, onEdit: () -> Unit, onDelete: () -> Unit) {
    val months = DebtPayoffCalculator.monthsToPayOff(debt.balance, debt.aprPercent, debt.minPayment)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    debt.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit debt", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete debt", tint = ExpenseRed, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text(
                debt.balance.toCurrencyString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ExpenseRed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("APR", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
                Text("${debt.aprPercent}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Min payment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
                Text(debt.minPayment.toCurrencyString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Text(
                text = when {
                    debt.balance <= 0 -> "Paid off! 🎉"
                    months == null -> "At this payment, this debt won't shrink — consider raising the minimum payment."
                    else -> "Paid off in ~${formatMonths(months)} at this minimum payment"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (months == null && debt.balance > 0) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GoalDialog(uiState: GoalsUiState, viewModel: GoalsViewModel) {
    val isEdit = uiState.editingGoal != null
    AlertDialog(
        onDismissRequest = viewModel::dismissGoalDialog,
        title = { Text(if (isEdit) "Edit Savings Goal" else "New Savings Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.goalNameInput,
                    onValueChange = viewModel::setGoalNameInput,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.goalTargetInput,
                    onValueChange = viewModel::setGoalTargetInput,
                    label = { Text("Target amount (£)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.goalSavedInput,
                    onValueChange = viewModel::setGoalSavedInput,
                    label = { Text("Saved so far (£)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.goalDateInput,
                    onValueChange = viewModel::setGoalDateInput,
                    label = { Text("Target date (DD/MM/YYYY, optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::confirmGoalDialog,
                enabled = !uiState.isSavingGoal && uiState.goalNameInput.isNotBlank() && uiState.goalTargetInput.toDoubleOrNull() != null
            ) {
                if (uiState.isSavingGoal) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissGoalDialog) { Text("Cancel") } }
    )
}

@Composable
private fun DebtDialog(uiState: GoalsUiState, viewModel: GoalsViewModel) {
    val isEdit = uiState.editingDebt != null
    AlertDialog(
        onDismissRequest = viewModel::dismissDebtDialog,
        title = { Text(if (isEdit) "Edit Debt" else "New Debt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.debtNameInput,
                    onValueChange = viewModel::setDebtNameInput,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.debtBalanceInput,
                    onValueChange = viewModel::setDebtBalanceInput,
                    label = { Text("Balance owed (£)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.debtAprInput,
                    onValueChange = viewModel::setDebtAprInput,
                    label = { Text("APR %") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.debtMinPaymentInput,
                    onValueChange = viewModel::setDebtMinPaymentInput,
                    label = { Text("Minimum payment (£/month)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::confirmDebtDialog,
                enabled = !uiState.isSavingDebt && uiState.debtNameInput.isNotBlank() &&
                    uiState.debtBalanceInput.toDoubleOrNull() != null && uiState.debtMinPaymentInput.toDoubleOrNull() != null
            ) {
                if (uiState.isSavingDebt) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissDebtDialog) { Text("Cancel") } }
    )
}

private fun monthsUntil(targetDate: String): Int? {
    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
    val target = runCatching { fmt.parse(targetDate) }.getOrNull() ?: return null
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { time = target }
    val months = (then.get(java.util.Calendar.YEAR) - now.get(java.util.Calendar.YEAR)) * 12 +
        (then.get(java.util.Calendar.MONTH) - now.get(java.util.Calendar.MONTH))
    return max(months, 1)
}

private fun formatMonths(months: Int): String = when {
    months < 1 -> "less than a month"
    months == 1 -> "1 month"
    months < 12 -> "$months months"
    else -> {
        val years = months / 12
        val rem = months % 12
        if (rem == 0) "$years ${if (years == 1) "year" else "years"}"
        else "$years ${if (years == 1) "year" else "years"} $rem ${if (rem == 1) "month" else "months"}"
    }
}
