package com.budgetr.app.ui.screens.forecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.data.repository.BillRow
import com.budgetr.app.util.AccountForecast
import com.budgetr.app.util.ForecastWarning
import com.budgetr.app.util.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun dayText(date: Date) = SimpleDateFormat("EEE d MMM", Locale.UK).format(date)

private fun ordinal(day: Int) = "$day" + when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

/** Each account's balance day by day until payday, with a warning before any would dip below £0. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowScreen(
    onNavigateBack: () -> Unit,
    viewModel: CashFlowViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    uiState.editingBill?.let { bill ->
        DueDayDialog(bill = bill, onPick = { viewModel.setDueDay(bill, it) }, onDismiss = viewModel::dismissBill)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cash-flow forecast") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        CashFlowContent(
            state = uiState,
            onSpendingAccount = viewModel::setSpendingAccount,
            onEditBill = viewModel::editBill,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

@Composable
internal fun CashFlowContent(
    state: CashFlowUiState,
    onSpendingAccount: (String?) -> Unit,
    onEditBill: (BillRow) -> Unit,
    modifier: Modifier = Modifier
) {
    val forecast = state.forecast
    if (state.isLoading || forecast == null) {
        Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.isLoading) CircularProgressIndicator()
            else Text("Add an account to see where your money is heading.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { OutlookCard(warnings = forecast.warnings, nextPayday = state.nextPayday) }
        item {
            SpendingAccountRow(
                accounts = state.accounts,
                current = forecast.spendingAccount,
                isAuto = state.spendingAccountIsAuto,
                spendPerDay = state.spendPerDay,
                onPick = onSpendingAccount
            )
        }
        items(forecast.accounts, key = { "account-${it.account}" }) { account ->
            AccountForecastCard(account = account, nextPayday = state.nextPayday)
        }
        if (state.bills.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("When bills leave", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Bills count as paid on payday unless you set the day they really go out. Tap one to change it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column {
                        state.bills.forEachIndexed { index, bill ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            BillRowItem(bill = bill, showAccount = state.accounts.size > 1, onClick = { onEditBill(bill) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlookCard(warnings: List<ForecastWarning>, nextPayday: Date?) {
    val ok = warnings.isEmpty()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ok) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Every account stays above £0 until payday" + (nextPayday?.let { ", ${dayText(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                warnings.forEach { warning -> WarningRow(warning) }
            }
        }
    }
}

@Composable
private fun WarningRow(warning: ForecastWarning) {
    val ink = MaterialTheme.colorScheme.onErrorContainer
    val today = remember { SimpleDateFormat("yyyyMMdd", Locale.UK).format(Date()) }
    val isToday = SimpleDateFormat("yyyyMMdd", Locale.UK).format(warning.date) == today
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = ink, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = if (isToday) "${warning.account} is below £0 today" else "${warning.account} goes below £0 on ${dayText(warning.date)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = ink
            )
            Text(
                text = when {
                    warning.fromAccount != null && isToday -> "Move ${warning.moveAmount.toCurrencyString()} from ${warning.fromAccount} to cover it."
                    warning.fromAccount != null -> "Move ${warning.moveAmount.toCurrencyString()} from ${warning.fromAccount} before then."
                    else -> "It's short by up to ${warning.shortfall.toCurrencyString()}, and no other account can cover it. Spending less or moving a bill would help."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ink
            )
        }
    }
}

@Composable
private fun SpendingAccountRow(
    accounts: List<String>,
    current: String?,
    isAuto: Boolean,
    spendPerDay: Double,
    onPick: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Change spending account") { expanded = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Day-to-day spending comes from", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    Text(
                        text = (current ?: "No account") + if (isAuto) " (where you spend most)" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Work it out for me") }, onClick = { onPick(null); expanded = false })
                accounts.forEach { account ->
                    DropdownMenuItem(text = { Text(account) }, onClick = { onPick(account); expanded = false })
                }
            }
        }
        Text(
            text = "About ${spendPerDay.toCurrencyString()} a day until payday, from your daily target.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
internal fun AccountForecastCard(account: AccountForecast, nextPayday: Date?) {
    var selected by remember(account) { mutableStateOf<Int?>(null) }
    val low = account.lowest
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.account,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = account.endBalance.toCurrencyString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (account.endBalance < 0) ExpenseRed else MaterialTheme.colorScheme.onSurface
                )
            }
            // What the chart is pointing at: the lowest point, or the day being touched
            val focus = selected?.let { account.days.getOrNull(it) }
            Text(
                text = if (focus != null) "${dayText(focus.date)}: ${focus.balance.toCurrencyString()}"
                else "Lowest ${low.balance.toCurrencyString()} on ${dayText(low.date)} · day before payday on the right",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2
            )
            BalanceChart(account = account, selected = selected, onSelect = { selected = it })
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(
                    text = nextPayday?.let { "Payday ${dayText(it)}" } ?: "Payday",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (account.events.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    account.events.take(4).forEach { event ->
                        Row {
                            Text(
                                text = SimpleDateFormat("d MMM", Locale.UK).format(event.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.width(52.dp)
                            )
                            Text(event.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Text(
                                text = (if (event.amount > 0) "+" else "") + event.amount.toCurrencyString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (event.amount > 0) IncomeGreen else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (account.events.size > 4) {
                        Text("and ${account.events.size - 4} more", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

/** One account's balance from today to the day before payday. Below £0 is drawn in red, the
 *  lowest point is marked, and touching the chart shows that day's balance. */
@Composable
private fun BalanceChart(account: AccountForecast, selected: Int?, onSelect: (Int?) -> Unit) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val ring = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    val days = account.days
    val lowIndex = days.indexOf(account.lowest)
    val summary = "${account.account}: today ${days.first().balance.toCurrencyString()}, lowest ${account.lowest.balance.toCurrencyString()} " +
        "on ${dayText(account.lowest.date)}, ${account.endBalance.toCurrencyString()} the day before payday"

    fun indexAt(x: Float, width: Float): Int =
        if (days.size < 2) 0 else (x / width * (days.size - 1)).let { Math.round(it) }.coerceIn(0, days.size - 1)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .semantics { contentDescription = summary }
            .pointerInput(days) {
                detectTapGestures { offset -> onSelect(indexAt(offset.x, size.width.toFloat())) }
            }
            .pointerInput(days) {
                detectHorizontalDragGestures(
                    onDragEnd = { onSelect(null) },
                    onDragCancel = { onSelect(null) }
                ) { change, _ -> onSelect(indexAt(change.position.x, size.width.toFloat())) }
            }
    ) {
        val pad = 6.dp.toPx()
        val values = days.map { it.balance }
        val top = maxOf(values.max(), 0.0)
        val bottom = minOf(values.min(), 0.0)
        val range = (top - bottom).takeIf { it > 0.0 } ?: 1.0
        fun x(i: Int) = if (days.size < 2) size.width / 2 else i * size.width / (days.size - 1)
        fun y(v: Double) = (pad + (top - v) / range * (size.height - 2 * pad)).toFloat()

        // £0 baseline, recessive
        val zeroY = y(0.0)
        drawLine(grid, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))

        val path = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        clipRect(bottom = zeroY) { drawPath(path, line, style = stroke) }
        clipRect(top = zeroY) { drawPath(path, ExpenseRed, style = stroke) }

        // Lowest point marker with a surface ring
        val lowColor = if (account.lowest.balance < 0) ExpenseRed else line
        val lowCenter = Offset(x(lowIndex), y(account.lowest.balance))
        drawCircle(ring, radius = 6.dp.toPx(), center = lowCenter)
        drawCircle(lowColor, radius = 4.dp.toPx(), center = lowCenter)

        selected?.let { i ->
            val cx = x(i)
            drawLine(ink.copy(alpha = 0.4f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
            val c = Offset(cx, y(values[i]))
            drawCircle(ring, radius = 6.dp.toPx(), center = c)
            drawCircle(if (values[i] < 0) ExpenseRed else line, radius = 4.dp.toPx(), center = c)
        }
    }
}

@Composable
private fun BillRowItem(bill: BillRow, showAccount: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Set due day", onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(bill.info, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(
                    bill.dueDay?.let { "Due on the ${ordinal(it)}" } ?: "On payday",
                    bill.account.takeIf { showAccount }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (bill.dueDay == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f) else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(bill.amount.toCurrencyString(), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DueDayDialog(bill: BillRow, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("When does ${bill.info} go out?") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Day of the month", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..31).forEach { day ->
                        FilterChip(
                            selected = bill.dueDay == day,
                            onClick = { onPick(day) },
                            label = { Text("$day", modifier = Modifier.width(20.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                Text(
                    text = "In shorter months, days past the end count as the last day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("On payday") } }
    )
}
