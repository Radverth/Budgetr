package com.budgetr.app.ui.screens.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.data.model.DailyTransfer
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.util.DailyTargetDay
import com.budgetr.app.util.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTargetScreen(onNavigateBack: () -> Unit, viewModel: DailyTargetViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var transferKind by remember { mutableStateOf<TransferKind?>(null) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val state = uiState.state
    if (transferKind != null && state != null) {
        DailyTransferDialog(
            state = state,
            kind = transferKind!!,
            onConfirm = { amount, day ->
                viewModel.addTransfer(amount, day)
                transferKind = null
            },
            onDismiss = { transferKind = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily spend target") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val target = state.target
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                DailyTargetCard(state = state, onOpen = {}, onTransfer = { transferKind = it }, modifier = Modifier.fillMaxWidth())
            }
            item {
                Text(
                    text = "${state.pot.toCurrencyString()} for non-fixed spending this pay period " +
                        (if (state.potIsManual) "(set by you in Settings)" else "(worked out on payday)") +
                        ", split into ${target.days.size} days of ${target.base.toCurrencyString()}. " +
                        "Whatever a day doesn't spend rolls into the next.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            if (state.transfers.isNotEmpty()) {
                item { SectionTitle("Borrowed and paid forward") }
                items(state.transfers, key = { "transfer-${it.id}" }) { transfer ->
                    TransferRow(transfer = transfer, onUndo = { viewModel.undoTransfer(transfer.id) })
                }
            }
            item { SectionTitle("Day by day") }
            itemsIndexed(target.days, key = { _, day -> "day-${day.date.time}" }) { index, day ->
                DayRow(day = day, base = target.base, isToday = index == target.todayIndex, isFuture = index > target.todayIndex)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun TransferRow(transfer: DailyTransfer, onUndo: () -> Unit) {
    val parse = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
    fun pretty(text: String) = runCatching { shortDay(parse.parse(text)!!) }.getOrDefault(text)
    val borrowed = transfer.amount > 0
    val amount = abs(transfer.amount).toCurrencyString()
    val other = transfer.targetDay?.let { (if (borrowed) "from " else "to ") + pretty(it) } ?: "spread over later days"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "↔ ${pretty(transfer.createdOn)}: ${if (borrowed) "borrowed" else "paid forward"} $amount, $other",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onUndo) { Text("Undo") }
        }
    }
}

/** One day: what it had, what was spent, and a thin bar of spent against target. Future days
 *  show what they'd start with if nothing more is spent before them. */
@Composable
internal fun DayRow(day: DailyTargetDay, base: Double, isToday: Boolean, isFuture: Boolean) {
    val over = !isFuture && day.left < 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isToday) "Today · ${shortDay(day.date)}" else shortDay(day.date),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when {
                        isFuture -> "${day.target.toCurrencyString()} if you stop now"
                        over -> "${(-day.left).toCurrencyString()} over"
                        else -> "${day.left.toCurrencyString()} left"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (over) ExpenseRed else MaterialTheme.colorScheme.onSurface
                )
            }
            val details = buildList {
                if (!isFuture) add("Spent ${day.spent.toCurrencyString()} of ${day.target.toCurrencyString()}")
                if (abs(day.carriedIn) >= 0.005) {
                    add((if (day.carriedIn > 0) "+" else "−") + "${abs(day.carriedIn).toCurrencyString()} from earlier days")
                }
                // A borrow or pay-forward changed this day's own share
                if (abs(day.allocation - base) >= 0.005) add("↔ own share ${day.allocation.toCurrencyString()}")
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (!isFuture && day.target > 0) {
                val fraction = (day.spent / day.target).toFloat().coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (over) ExpenseRed else MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
