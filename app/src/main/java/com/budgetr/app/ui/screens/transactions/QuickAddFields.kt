package com.budgetr.app.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetr.app.data.model.Transaction

/** Shared with phone-size screenshots so they render the actual entry controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickAddFields(
    amount: String,
    onAmountChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    suggestions: List<Transaction>,
    onSuggestion: (Transaction) -> Unit,
    amountFocus: FocusRequester
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            label = { Text("Amount (£)") },
            modifier = Modifier.fillMaxWidth().focusRequester(amountFocus),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            singleLine = true
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        val matching = suggestions.filter { it.info.contains(description.trim(), ignoreCase = true) }
        if (matching.isNotEmpty()) {
            Text("Recent & frequent", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                matching.forEach { transaction ->
                    AssistChip(
                        onClick = { onSuggestion(transaction) },
                        label = { Text(transaction.info, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    }
}
