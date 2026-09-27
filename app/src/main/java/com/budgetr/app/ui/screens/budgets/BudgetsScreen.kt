package com.budgetr.app.ui.screens.budgets

import androidx.compose.foundation.layout.Arrangement
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(onNavigateBack: () -> Unit, viewModel: BudgetsViewModel = hiltViewModel()) {
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
                Text(
                    text = "Set a cap for each category to get warned before you overspend this pay period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
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
