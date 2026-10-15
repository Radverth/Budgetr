package com.budgetr.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.util.SetupFixedCost
import com.budgetr.app.util.SetupSummary
import com.budgetr.app.util.toCurrencyString

/** Frame shared by every guided-setup step: progress, a title, the step, and Back / Next. */
@Composable
internal fun SetupStepFrame(
    stepNumber: Int,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String = "Next",
    nextEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Step $stepNumber of ${SETUP_STEPS.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = stepNumber.toFloat() / SETUP_STEPS.size,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { content() }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onNext, enabled = nextEnabled, shape = RoundedCornerShape(24.dp)) { Text(nextLabel) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaydayStep(payDay: Int, onPayDay: (Int) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    SetupStepFrame(
        stepNumber = 1,
        title = "When are you paid?",
        subtitle = "Pick the day of the month. If it falls on a weekend, Budgetr uses the Friday before.",
        onBack = onBack,
        onNext = onNext
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..31).forEach { day ->
                FilterChip(
                    selected = day == payDay,
                    onClick = { onPayDay(day) },
                    label = { Text(day.toString(), modifier = Modifier.width(22.dp)) }
                )
            }
        }
    }
}

private val ACCOUNT_SUGGESTIONS = listOf("Current account", "Savings", "Credit card", "Joint account", "Cash")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountsStep(
    accounts: List<String>,
    input: String,
    error: String?,
    onInput: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    SetupStepFrame(
        stepNumber = 2,
        title = "Your accounts",
        subtitle = "Each account gets its own tab in your Google Sheet. You can add more later.",
        onBack = onBack,
        onNext = onNext,
        nextEnabled = accounts.isNotEmpty()
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            accounts.forEach { account ->
                InputChip(
                    selected = true,
                    onClick = { if (accounts.size > 1) onRemove(account) },
                    label = { Text(account) },
                    trailingIcon = if (accounts.size > 1) {
                        { Icon(Icons.Default.Close, contentDescription = "Remove $account") }
                    } else null
                )
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            label = { Text("Add an account, e.g. Monzo") },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            trailingIcon = { TextButton(onClick = { onAdd(input) }, enabled = input.isNotBlank()) { Text("Add") } },
            modifier = Modifier.fillMaxWidth()
        )
        val unused = ACCOUNT_SUGGESTIONS.filter { s -> accounts.none { it.equals(s, ignoreCase = true) } }
        if (unused.isNotEmpty()) {
            Text("Suggestions", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                unused.forEach { suggestion -> AssistChip(onClick = { onAdd(suggestion) }, label = { Text("+ $suggestion") }) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IncomeStep(
    incomeInput: String,
    accounts: List<String>,
    incomeAccount: String?,
    onIncome: (String) -> Unit,
    onAccount: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    SetupStepFrame(
        stepNumber = 3,
        title = "What do you take home?",
        subtitle = "Your usual pay after tax for one pay period. Leave it blank to skip; you can add it later.",
        onBack = onBack,
        onNext = onNext,
        nextLabel = if (incomeInput.isBlank()) "Skip" else "Next"
    ) {
        OutlinedTextField(
            value = incomeInput,
            onValueChange = onIncome,
            label = { Text("Take-home pay (£)") },
            prefix = { Text("£") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        if (accounts.size > 1) {
            Text("Paid into", style = MaterialTheme.typography.labelMedium)
            val chosen = incomeAccount ?: accounts.first()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                accounts.forEach { account ->
                    FilterChip(selected = account == chosen, onClick = { onAccount(account) }, label = { Text(account) })
                }
            }
        }
    }
}

private val FIXED_COST_PRESETS = listOf("Rent", "Mortgage", "Council tax", "Energy", "Water", "Phone", "Internet", "Streaming", "Insurance", "Gym")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FixedCostsStep(
    costs: List<SetupFixedCost>,
    accounts: List<String>,
    onAdd: (String) -> Unit,
    onUpdate: (Int, SetupFixedCost) -> Unit,
    onRemove: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    SetupStepFrame(
        stepNumber = 4,
        title = "Your bills",
        subtitle = "Fixed costs that leave every month: rent, council tax, phone, subscriptions. Tap one to add it.",
        onBack = onBack,
        onNext = onNext,
        nextLabel = if (costs.isEmpty()) "Skip" else "Next"
    ) {
        val unused = FIXED_COST_PRESETS.filter { p -> costs.none { it.name.equals(p, ignoreCase = true) } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            unused.forEach { preset -> AssistChip(onClick = { onAdd(preset) }, label = { Text("+ $preset") }) }
            AssistChip(onClick = { onAdd("") }, label = { Text("+ Other") })
        }
        costs.forEachIndexed { index, cost ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = cost.name,
                            onValueChange = { onUpdate(index, cost.copy(name = it)) },
                            label = { Text("Bill") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = cost.amountInput,
                            onValueChange = { onUpdate(index, cost.copy(amountInput = it)) },
                            label = { Text("£") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(96.dp)
                        )
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove ${cost.name.ifBlank { "bill" }}")
                        }
                    }
                    if (accounts.size > 1) {
                        // Tapping moves to the next account, which keeps the row short
                        TextButton(onClick = {
                            val next = accounts[(accounts.indexOf(cost.account) + 1).mod(accounts.size)]
                            onUpdate(index, cost.copy(account = next))
                        }) { Text("Paid from ${cost.account} ▸") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SummaryStep(summary: SetupSummary, isCreating: Boolean, onBack: () -> Unit, onCreate: () -> Unit) {
    SetupStepFrame(
        stepNumber = 5,
        title = "Here's your budget",
        subtitle = "Budgetr splits what's left after bills into a daily amount. Whatever you don't spend rolls into tomorrow.",
        onBack = onBack,
        onNext = onCreate,
        nextLabel = "Create my budget",
        nextEnabled = !isCreating
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summary.income > 0) {
                    Text("You have", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text(
                        text = "${summary.perDay.toCurrencyString()} a day",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.pot > 0) IncomeGreen else ExpenseRed
                    )
                    Text(
                        text = "for the next ${summary.days} days, until payday",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryLine("Take-home pay", summary.income, IncomeGreen)
                    SummaryLine("Bills", -summary.fixedCosts, MaterialTheme.colorScheme.onSurface)
                    SummaryLine("Left for everything else", summary.pot, MaterialTheme.colorScheme.onSurface, bold = true)
                    if (summary.pot <= 0) {
                        Text(
                            text = "Your bills are more than your pay. You can still continue and adjust things in the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseRed
                        )
                    }
                } else {
                    Text(
                        text = "Add your pay later and Budgetr will work out a daily amount for you.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, amount: Double, color: Color, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        Text(amount.toCurrencyString(), style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}
