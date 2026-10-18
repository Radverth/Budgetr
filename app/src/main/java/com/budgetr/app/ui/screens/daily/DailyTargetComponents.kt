package com.budgetr.app.ui.screens.daily

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetr.app.data.repository.DailyTargetState
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.util.PaydayPlanCalculator
import com.budgetr.app.util.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class TransferKind { BORROW, PAY_FORWARD }

internal fun shortDay(date: Date): String = SimpleDateFormat("EEE d MMM", Locale.UK).format(date)

/** Home's headline: what's left to spend today, with rollover and borrow / pay forward. */
@Composable
fun DailyTargetCard(
    state: DailyTargetState,
    onOpen: () -> Unit,
    onTransfer: (TransferKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val target = state.target
    val today = target.today
    Card(
        modifier = modifier.clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Today's spend target",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = if (target.isOverToday) "${(-target.leftToday).toCurrencyString()} over today"
                else "${target.leftToday.toCurrencyString()} left today",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (target.isOverToday) ExpenseRed else IncomeGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${today.spent.toCurrencyString()} spent of ${today.target.toCurrencyString()} today",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = target.tomorrowIfNoMoreSpent?.let { "Tomorrow: ${it.toCurrencyString()} if you stop now" }
                    ?: "Last day before payday",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = (state.irregular?.let { "From ${it.received.toCurrencyString()} received so far · " }
                    ?: "Base ${target.base.toCurrencyString()} a day · ") +
                    if (target.daysLeft == 1) "payday tomorrow" else "${target.daysLeft} days to payday",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (state.pot <= 0 && !state.potIsManual) {
                Text(
                    text = state.irregular?.let {
                        if (it.received > 0) "Bills and payments (${it.commitments.toCurrencyString()}) come first. Spending money starts once more arrives."
                        else "No income has arrived this pay period yet. Add it as Income when it does."
                    } ?: "No income found for this pay period yet. Set your spending money in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { onTransfer(TransferKind.BORROW) }, enabled = target.borrowable > 0) {
                    Text("Borrow")
                }
                OutlinedButton(onClick = { onTransfer(TransferKind.PAY_FORWARD) }, enabled = target.payableForward > 0) {
                    Text("Pay forward")
                }
            }
        }
    }
}

/** Borrow from later days, or pay today's money forward, either spread over the rest of the
 *  period or against one chosen day. Shows the effect before saving. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DailyTransferDialog(
    state: DailyTargetState,
    kind: TransferKind,
    onConfirm: (amount: Double, targetDay: Date?) -> Unit,
    onDismiss: () -> Unit
) {
    val target = state.target
    val laterDays = target.days.drop(target.todayIndex + 1)
    var amountInput by remember { mutableStateOf("") }
    var oneDay by remember { mutableStateOf<Date?>(null) }
    var spread by remember { mutableStateOf(true) }

    val amount = PaydayPlanCalculator.parseAmount(amountInput)?.takeIf { it > 0 }
    val limit = if (kind == TransferKind.BORROW) target.borrowable else target.payableForward
    val signed = (amount ?: 0.0) * if (kind == TransferKind.BORROW) 1 else -1
    val chosenDay = laterDays.firstOrNull { it.date == oneDay }
    val error = when {
        amount == null -> null
        amount > limit + 0.005 -> if (kind == TransferKind.BORROW) {
            "You can borrow up to ${limit.toCurrencyString()}, which is everything the later days have."
        } else {
            "You can pay forward up to ${limit.toCurrencyString()}, which is what's left today."
        }
        else -> null
    }
    val warning = if (!spread && chosenDay != null && kind == TransferKind.BORROW && chosenDay.allocation - (amount ?: 0.0) < 0) {
        "${shortDay(chosenDay.date)} will start below £0 unless earlier days leave something over."
    } else null
    val canSave = amount != null && error == null && (spread || chosenDay != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == TransferKind.BORROW) "Borrow from later days" else "Pay forward to later days") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Amount (£)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text("Up to ${limit.toCurrencyString()}") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = spread, onClick = { spread = true }, label = { Text("Spread over the rest") })
                    FilterChip(
                        selected = !spread,
                        onClick = { spread = false },
                        label = { Text(if (kind == TransferKind.BORROW) "From one day" else "To one day") }
                    )
                }
                if (!spread) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        laterDays.forEach { day ->
                            FilterChip(
                                selected = day.date == oneDay,
                                onClick = { oneDay = day.date },
                                label = { Text(shortDay(day.date), style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
                if (amount != null && error == null) {
                    Text(
                        text = previewText(state, signed, if (spread) null else chosenDay?.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ExpenseRed) }
                warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(signed, if (spread) null else chosenDay?.date) }, enabled = canSave) {
                Text(if (kind == TransferKind.BORROW) "Borrow" else "Pay forward")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun previewText(state: DailyTargetState, signed: Double, oneDay: Date?): String {
    val target = state.target
    val today = target.today
    val todayLine = "Today ${today.target.toCurrencyString()} → ${(today.target + signed).toCurrencyString()}"
    if (oneDay != null) {
        val day = target.days.first { it.date == oneDay }
        return "$todayLine · ${shortDay(day.date)}'s share ${day.allocation.toCurrencyString()} → " +
            (day.allocation - signed).toCurrencyString()
    }
    val later = target.days.size - target.todayIndex - 1
    val each = abs(signed) / later
    return "$todayLine · each later day ${if (signed > 0) "−" else "+"}${each.toCurrencyString()}"
}
