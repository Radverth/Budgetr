package com.budgetr.app.ui.screens.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.data.model.CategoryRule
import com.budgetr.app.ui.theme.ExpenseRed

/** "Description contains TESCO → Groceries" rules, plus suggestions from past spending. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRulesScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryRulesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        val canUndo = uiState.deleted != null
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (canUndo) "Undo" else null,
            duration = SnackbarDuration.Short
        )
        if (canUndo && result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        viewModel.clearMessage()
    }

    uiState.dialog?.let { dialog ->
        RuleDialog(
            dialog = dialog,
            knownTags = uiState.knownTags,
            onChange = viewModel::updateDialog,
            onSave = viewModel::confirmDialog,
            onDelete = { id -> uiState.rules.firstOrNull { it.rule.id == id }?.let { viewModel.delete(it.rule) } },
            onDismiss = viewModel::dismissDialog
        )
    }
    uiState.confirmApply?.let { row ->
        ApplyDialog(row = row, isApplying = uiState.isApplying, onConfirm = viewModel::applyToPast, onDismiss = viewModel::dismissApply)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Category rules") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        CategoryRulesContent(
            state = uiState,
            onAdd = viewModel::showAdd,
            onEdit = viewModel::showEdit,
            onApply = viewModel::askApply,
            onAddSuggestion = viewModel::addSuggestion,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

@Composable
internal fun CategoryRulesContent(
    state: CategoryRulesUiState,
    onAdd: () -> Unit,
    onEdit: (CategoryRule) -> Unit,
    onApply: (RuleRow) -> Unit,
    onAddSuggestion: (CategoryRule) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isLoading) {
        Column(modifier = modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "When a one-off cost's description contains the words in a rule, its spending category is filled in for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        if (state.rules.isEmpty()) {
            item {
                Text(
                    text = "No rules yet. Add one, or start from a suggestion below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        items(state.rules, key = { "rule-${it.rule.id}" }) { row ->
            RuleCard(row = row, onEdit = { onEdit(row.rule) }, onApply = { onApply(row) })
        }
        item {
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add rule")
            }
        }
        if (state.suggestions.isNotEmpty()) {
            item {
                Column {
                    Text("Suggested from your spending", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp))
                    Text(
                        text = "You've given these the same category more than once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            items(state.suggestions, key = { "suggestion-${it.pattern.lowercase()}" }) { rule ->
                SuggestionRow(rule = rule, onAdd = { onAddSuggestion(rule) })
            }
        }
    }
}

@Composable
private fun RuleSummary(rule: CategoryRule, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "“${rule.pattern}”",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "sets category to",
            modifier = Modifier.padding(horizontal = 8.dp).size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = rule.tag,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun RuleCard(row: RuleRow, onEdit: () -> Unit, onApply: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Edit rule", onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = if (row.pastCount > 0) 4.dp else 14.dp)) {
            RuleSummary(row.rule, Modifier.padding(end = 8.dp))
            Text(
                text = "Description contains these words",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (row.pastCount > 0) {
                TextButton(onClick = onApply, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(if (row.pastCount == 1) "Apply to 1 past cost" else "Apply to ${row.pastCount} past costs")
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(rule: CategoryRule, onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RuleSummary(rule, Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text("Add") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleDialog(
    dialog: RuleDialogState,
    knownTags: List<String>,
    onChange: ((RuleDialogState) -> RuleDialogState) -> Unit,
    onSave: () -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dialog.id == null) "Add rule" else "Edit rule") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = dialog.pattern,
                    onValueChange = { value -> onChange { it.copy(pattern = value.take(40), error = null) } },
                    label = { Text("Description contains") },
                    placeholder = { Text("e.g. TESCO") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dialog.tag,
                    onValueChange = { value -> onChange { it.copy(tag = value.take(24), error = null) } },
                    label = { Text("Spending category") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    knownTags.forEach { tag ->
                        FilterChip(
                            selected = tag.equals(dialog.tag.trim(), ignoreCase = true),
                            onClick = { onChange { it.copy(tag = tag, error = null) } },
                            label = { Text(tag, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                Text(
                    text = "Capital letters don't matter. Rules only fill in new costs; use \"Apply to past costs\" for older ones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                dialog.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ExpenseRed) }
                dialog.id?.let { id ->
                    TextButton(onClick = { onDelete(id) }, colors = ButtonDefaults.textButtonColors(contentColor = ExpenseRed)) {
                        Text("Delete this rule")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ApplyDialog(row: RuleRow, isApplying: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val count = if (row.pastCount == 1) "1 past cost" else "${row.pastCount} past costs"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set $count to ${row.rule.tag}?") },
        text = {
            Text(
                buildString {
                    append("This changes the spending category in your Google Sheet for one-off costs whose description contains “${row.rule.pattern}”.")
                    if (row.pastRetagged > 0) {
                        append(if (row.pastRetagged == 1) " 1 of them has a different category now." else " ${row.pastRetagged} of them have a different category now.")
                    }
                }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isApplying) {
                if (isApplying) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Apply")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isApplying) { Text("Cancel") } }
    )
}
