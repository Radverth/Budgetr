package com.budgetr.app.ui.screens.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import com.budgetr.app.util.EnvelopeStatus
import com.budgetr.app.util.SpendTags
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.util.BudgetCapCalculator
import com.budgetr.app.util.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPaydayPlan: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel()
) {
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

    if (uiState.showDialog) {
        BudgetLimitDialog(uiState = uiState, viewModel = viewModel)
    }
    uiState.envelopeDialog?.let { dialog ->
        EnvelopeDialog(
            dialog = dialog,
            availableTags = uiState.knownTags.filter { tag ->
                tag.equals(dialog.originalTag, ignoreCase = true) ||
                    uiState.envelopes.none { it.envelope.tag.equals(tag, ignoreCase = true) }
            },
            viewModel = viewModel
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Set a cap for each category to get warned before you overspend this pay period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    uiState.resetDate?.let { resetDate ->
                        val dateText = SimpleDateFormat("EEE d MMM", Locale.UK).format(resetDate)
                        val daysText = if (uiState.resetDays == 1) "tomorrow" else "in ${uiState.resetDays} days"
                        Text(
                            text = "Caps reset on payday, $dateText ($daysText)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            item { SectionHeader("Spending categories") }
            if (uiState.envelopes.isEmpty()) {
                item {
                    Text(
                        text = "Give categories like Groceries or Eating out their own limit. Tag one-off costs when you add them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            items(uiState.envelopes, key = { "envelope-${it.envelope.tag}" }) { status ->
                EnvelopeCard(status = status, onEdit = { viewModel.showEditEnvelope(status.envelope.tag) })
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::showAddEnvelope, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add category budget")
                    }
                    OutlinedButton(onClick = onNavigateToPaydayPlan, modifier = Modifier.fillMaxWidth()) {
                        Text("Re-plan this pay period")
                    }
                }
            }
            item { SectionHeader("Overall caps") }
            items(uiState.items, key = { it.category.name }) { item ->
                BudgetCapCard(item = item, onEdit = { viewModel.showEditDialog(item.category) })
            }
        }
    }
}

@Composable
private fun BudgetCapCard(item: BudgetCapUiItem, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onEdit, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit budget", tint = MaterialTheme.colorScheme.primary)
                }
            }

            val limit = item.limit
            if (limit == null) {
                Text(
                    text = "${item.spend.toCurrencyString()} spent so far — no cap set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val percentUsed = BudgetCapCalculator.percentUsed(item.spend, limit)
                val isOver = BudgetCapCalculator.isOverLimit(item.spend, limit)
                val isApproaching = BudgetCapCalculator.isApproachingLimit(item.spend, limit)
                val barColor = when {
                    isOver -> ExpenseRed
                    isApproaching -> MaterialTheme.colorScheme.tertiary
                    else -> IncomeGreen
                }
                LinearProgressIndicator(
                    progress = percentUsed.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${item.spend.toCurrencyString()} of ${limit.toCurrencyString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isOver) "Over budget" else "${(percentUsed * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = barColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun EnvelopeCard(status: EnvelopeStatus, onEdit: () -> Unit) {
    val barColor = when {
        status.isOver -> ExpenseRed
        status.isApproaching -> MaterialTheme.colorScheme.tertiary
        else -> IncomeGreen
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
                    text = status.envelope.tag,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${status.envelope.tag} budget", tint = MaterialTheme.colorScheme.primary)
                }
            }
            LinearProgressIndicator(
                progress = status.percentUsed.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${status.spent.toCurrencyString()} of ${status.available.toCurrencyString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (status.isOver) "${(-status.left).toCurrencyString()} over" else "${status.left.toCurrencyString()} left",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = barColor
                )
            }
            val details = buildList {
                if (!status.isOver) add("${status.perDayLeft.toCurrencyString()} a day")
                if (status.envelope.carriedOver > 0) add("${status.envelope.carriedOver.toCurrencyString()} carried over")
                else if (status.envelope.rollover) add("unspent money rolls over")
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EnvelopeDialog(dialog: EnvelopeDialogState, availableTags: List<String>, viewModel: BudgetsViewModel) {
    val canSave = SpendTags.normalise(dialog.tag) != null && (dialog.limitInput.toDoubleOrNull() ?: 0.0) > 0
    AlertDialog(
        onDismissRequest = viewModel::dismissEnvelopeDialog,
        title = { Text(if (dialog.originalTag == null) "Add category budget" else "${dialog.originalTag} budget") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = dialog.tag,
                    onValueChange = { value -> viewModel.updateEnvelopeDialog { it.copy(tag = value.take(24)) } },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableTags.forEach { tag ->
                        FilterChip(
                            selected = tag.equals(dialog.tag.trim(), ignoreCase = true),
                            onClick = { viewModel.updateEnvelopeDialog { it.copy(tag = tag) } },
                            label = { Text(tag, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                OutlinedTextField(
                    value = dialog.limitInput,
                    onValueChange = { value -> viewModel.updateEnvelopeDialog { it.copy(limitInput = value) } },
                    label = { Text("Limit per pay period (£)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Carry unspent money to next pay period",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = dialog.rollover,
                        onCheckedChange = { checked -> viewModel.updateEnvelopeDialog { it.copy(rollover = checked) } }
                    )
                }
                dialog.originalTag?.let { tag ->
                    TextButton(
                        onClick = { viewModel.deleteEnvelope(tag) },
                        colors = ButtonDefaults.textButtonColors(contentColor = ExpenseRed)
                    ) { Text("Remove this budget") }
                }
            }
        },
        confirmButton = { Button(onClick = viewModel::confirmEnvelopeDialog, enabled = canSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = viewModel::dismissEnvelopeDialog) { Text("Cancel") } }
    )
}

@Composable
private fun BudgetLimitDialog(uiState: BudgetsUiState, viewModel: BudgetsViewModel) {
    val category = uiState.editingCategory ?: return
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text("${category.displayName} budget") },
        text = {
            OutlinedTextField(
                value = uiState.limitInput,
                onValueChange = viewModel::setLimitInput,
                label = { Text("Cap per pay period (£, blank to remove)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = { Button(onClick = viewModel::confirmDialog) { Text("Save") } },
        dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") } }
    )
}
