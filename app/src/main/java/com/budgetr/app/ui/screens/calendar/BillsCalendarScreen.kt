package com.budgetr.app.ui.screens.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.budgetr.app.util.CalendarDay
import com.budgetr.app.util.CalendarEvent
import com.budgetr.app.util.CalendarEventKind
import com.budgetr.app.util.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val WEEKDAYS = listOf("M", "T", "W", "T", "F", "S", "S")

/** Bills, income and each day's spending against target, a month at a time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsCalendarScreen(
    onNavigateBack: () -> Unit,
    viewModel: BillsCalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bills calendar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        BillsCalendarContent(
            state = uiState,
            onMonth = viewModel::showMonth,
            onSelect = viewModel::select,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BillsCalendarContent(
    state: BillsCalendarUiState,
    onMonth: (Int) -> Unit,
    onSelect: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isLoading) {
        Column(modifier = modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }
    val title = SimpleDateFormat("MMMM yyyy", Locale.UK).format(Calendar.getInstance().apply { clear(); set(state.year, state.month0, 1) }.time)
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onMonth(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                        }
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onMonth(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                        }
                    }
                    if (!state.isCurrentMonth) {
                        TextButton(onClick = { onMonth(0 - monthsAway(state)) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Back to this month")
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        WEEKDAYS.forEach { day ->
                            Text(day, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        }
                    }
                    state.days.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                DayCell(
                                    day = day,
                                    selected = state.selected?.let { day.inMonth && sameDay(day.date, it) } == true,
                                    onClick = { if (day.inMonth) onSelect(day.date) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LegendItem({ BillIcon() }, "Bill")
                        LegendItem({ IncomeIcon() }, "Income")
                        LegendItem({ SpendDot(under = true) }, "Under target")
                        LegendItem({ SpendDot(under = false) }, "Over target")
                    }
                }
            }
        }
        state.selectedDay?.let { day -> item { DayDetails(day) } }
        if (state.comingUp.isNotEmpty()) item { ComingUp(state.comingUp) }
    }
}

/** How many months the shown month is from this one. */
private fun monthsAway(state: BillsCalendarUiState): Int {
    val now = Calendar.getInstance()
    return (state.year - now.get(Calendar.YEAR)) * 12 + (state.month0 - now.get(Calendar.MONTH))
}

@Composable
private fun DayCell(day: CalendarDay, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dayNumber = Calendar.getInstance().apply { time = day.date }.get(Calendar.DAY_OF_MONTH)
    val bills = day.events.count { it.kind == CalendarEventKind.BILL }
    val income = day.events.any { it.kind == CalendarEventKind.INCOME }
    val description = buildList {
        add(SimpleDateFormat("EEEE d MMMM", Locale.UK).format(day.date))
        if (bills > 0) add(if (bills == 1) "1 bill" else "$bills bills")
        if (income) add("income")
        day.spend?.let { add(if (it.underTarget) "under target" else "over target") }
    }.joinToString(", ")
    Column(
        modifier = modifier
            .aspectRatio(0.85f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(enabled = day.inMonth, onClick = onClick)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .then(if (day.isToday && day.inMonth) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$dayNumber",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    day.isToday && day.inMonth -> MaterialTheme.colorScheme.onPrimary
                    day.inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(12.dp)) {
            if (bills > 0) BillIcon()
            if (income) IncomeIcon()
            day.spend?.let { SpendDot(it.underTarget) }
        }
    }
}

@Composable
private fun BillIcon() = Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(12.dp))

@Composable
private fun IncomeIcon() = Icon(Icons.Default.Payments, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(12.dp))

/** Filled for under target, a hollow ring for over, so it doesn't rely on colour alone. */
@Composable
private fun SpendDot(under: Boolean) {
    val color = if (under) IncomeGreen else ExpenseRed
    Canvas(Modifier.size(8.dp)) {
        if (under) drawCircle(color) else drawCircle(color, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun LegendItem(marker: @Composable () -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        marker()
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun DayDetails(day: CalendarDay) {
    InfoCard {
        Text(SimpleDateFormat("EEEE d MMMM", Locale.UK).format(day.date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        day.spend?.let { spend ->
            Text(
                text = "Spent ${spend.spent.toCurrencyString()} of ${spend.target.toCurrencyString()} target" +
                    if (spend.underTarget) "" else " · over by ${(spend.spent - spend.target).toCurrencyString()}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (day.events.isEmpty()) {
            Text("Nothing due", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        day.events.forEach { EventRow(it, showDate = false) }
        if (day.events.any { it.onPayday }) {
            Text(
                "Bills marked \"on payday\" have no due day yet. Set the day they really go out on the cash-flow forecast.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ComingUp(events: List<CalendarEvent>) {
    InfoCard {
        Text("Next 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        events.forEach { EventRow(it, showDate = true) }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, showDate: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (event.kind == CalendarEventKind.BILL) BillIcon() else IncomeIcon()
        Spacer(Modifier.width(8.dp))
        if (showDate) {
            Text(SimpleDateFormat("EEE d", Locale.UK).format(event.date), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
        }
        Text(
            text = event.label + if (event.onPayday) " (on payday)" else "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = (if (event.amount > 0) "+" else "") + event.amount.toCurrencyString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (event.amount > 0) IncomeGreen else MaterialTheme.colorScheme.onSurface
        )
    }
}

