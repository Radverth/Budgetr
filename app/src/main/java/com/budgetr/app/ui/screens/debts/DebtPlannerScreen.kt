package com.budgetr.app.ui.screens.debts

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.util.PayoffPlan
import com.budgetr.app.util.PayoffStrategy
import com.budgetr.app.util.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

private fun monthFromNow(months: Int): String {
    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, months) }
    return SimpleDateFormat("MMMM yyyy", Locale.UK).format(cal.time)
}

private fun shortMonthFromNow(months: Int): String {
    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, months) }
    return SimpleDateFormat("MMM yyyy", Locale.UK).format(cal.time)
}

private fun duration(months: Int): String {
    val years = months / 12
    val rest = months % 12
    return listOfNotNull(
        years.takeIf { it > 0 }?.let { if (it == 1) "1 year" else "$it years" },
        rest.takeIf { it > 0 || years == 0 }?.let { if (it == 1) "1 month" else "$it months" }
    ).joinToString(" ")
}

/** Snowball vs avalanche, how much extra to pay, and when that makes you debt-free. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtPlannerScreen(
    onNavigateBack: () -> Unit,
    viewModel: DebtPlannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debt payoff planner") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        DebtPlannerContent(
            state = uiState,
            onStrategy = viewModel::setStrategy,
            onExtra = viewModel::setExtra,
            onExtraDone = viewModel::saveExtra,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

@Composable
internal fun DebtPlannerContent(
    state: DebtPlannerUiState,
    onStrategy: (PayoffStrategy) -> Unit,
    onExtra: (Double) -> Unit,
    onExtraDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = state.plan
    if (state.isLoading || plan == null) {
        Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.isLoading) CircularProgressIndicator()
            else Text("Nothing owed. Add a debt on the Goals tab to plan paying it off.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ResultCard(state, plan) }
        item { StrategyCard(state.strategy, onStrategy) }
        item { ExtraCard(state, onExtra, onExtraDone) }
        if (plan.months != null && plan.months > 0) item { TimelineCard(plan, state.minimumsOnly) }
        if (plan.payoffs.isNotEmpty()) item { OrderCard(plan) }
    }
}

@Composable
private fun PlannerCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Muted(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun ResultCard(state: DebtPlannerUiState, plan: PayoffPlan) {
    PlannerCard {
        Muted("${state.totalOwed.toCurrencyString()} owed · paying ${(state.totalMinimums + state.extra).toCurrencyString()} a month")
        val months = plan.months
        if (months == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = ExpenseRed)
                Spacer(Modifier.width(8.dp))
                Text("These payments never clear the debt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Muted("The interest added each month is more than you pay. Add more extra each month below.")
        } else {
            DebtFreeLines(state, plan, months)
        }
    }
}

@Composable
private fun DebtFreeLines(state: DebtPlannerUiState, plan: PayoffPlan, months: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Debt-free by ${monthFromNow(months)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${duration(months)} · ${plan.totalInterest.toCurrencyString()} interest", style = MaterialTheme.typography.bodyLarge)
        val baselineMonths = state.minimumsOnly?.months
        val baselineInterest = state.minimumsOnly?.totalInterest ?: 0.0
        if (baselineMonths == null) {
            Text("Minimums alone would never clear it.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        } else if (baselineInterest - plan.totalInterest > 0.5) {
            Text(
                "Saves ${(baselineInterest - plan.totalInterest).toCurrencyString()} interest and " +
                    "${duration(baselineMonths - months)} compared with minimums only.",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
            )
        }
        val otherMonths = state.otherPlan?.months
        if (otherMonths != null && state.debts.size > 1) {
            val otherName = if (state.strategy == PayoffStrategy.AVALANCHE) "Smallest balance first" else "Highest interest first"
            val diff = (state.otherPlan?.totalInterest ?: 0.0) - plan.totalInterest
            Muted(
                when {
                    abs(diff) < 0.5 && otherMonths == months -> "$otherName would work out the same."
                    diff > 0 -> "$otherName would cost ${diff.toCurrencyString()} more interest."
                    else -> "$otherName would save ${(-diff).toCurrencyString()} interest" +
                        if (otherMonths < months) " and ${duration(months - otherMonths)}." else "."
                }
            )
        }
    }
}

@Composable
private fun StrategyCard(selected: PayoffStrategy, onStrategy: (PayoffStrategy) -> Unit) {
    PlannerCard {
        Text("Which debt gets the extra", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        PayoffStrategy.entries.forEach { strategy ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = strategy == selected, role = Role.RadioButton, onClick = { onStrategy(strategy) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = strategy == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(strategy.label, style = MaterialTheme.typography.bodyLarge)
                    Muted(strategy.explanation)
                }
            }
        }
    }
}

@Composable
private fun ExtraCard(state: DebtPlannerUiState, onExtra: (Double) -> Unit, onExtraDone: () -> Unit) {
    PlannerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Extra each month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(state.extra.toCurrencyString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = state.extra.toFloat(),
            onValueChange = { onExtra(it.toDouble()) },
            onValueChangeFinished = onExtraDone,
            valueRange = 0f..state.sliderMax.toFloat(),
            modifier = Modifier.semantics { contentDescription = "Extra each month, ${state.extra.toCurrencyString()}" }
        )
        Muted("On top of ${state.totalMinimums.toCurrencyString()} in minimums. It's included in your payday plan's debt payments from the next time you save the plan.")
    }
}

/** Total owed month by month: this plan against minimums only (dashed), with a legend. */
@Composable
private fun TimelineCard(plan: PayoffPlan, baseline: PayoffPlan?) {
    val months = plan.months ?: return
    val planColor = MaterialTheme.colorScheme.primary
    val baseColor = MaterialTheme.colorScheme.outline
    val ring = MaterialTheme.colorScheme.surfaceVariant
    val baseValues = baseline?.balances?.let { values -> (0..months).map { values.getOrElse(it) { 0.0 } } }
    PlannerCard {
        Text("What you owe over time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(planColor, dashed = false, label = "This plan")
            if (baseValues != null) LegendItem(baseColor, dashed = true, label = "Minimums only")
        }
        val summary = "Owed falls from ${plan.balances.first().toCurrencyString()} to nothing by ${monthFromNow(months)}" +
            (baseValues?.let { ", while minimums only would still owe ${it.last().toCurrencyString()} then" } ?: "")
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = summary }) {
            val pad = 6.dp.toPx()
            val top = maxOf(plan.balances.max(), baseValues?.max() ?: 0.0).takeIf { it > 0 } ?: 1.0
            fun x(i: Int) = i * size.width / months
            fun y(v: Double) = (pad + (1 - v / top) * (size.height - 2 * pad)).toFloat()
            // £0 baseline
            drawLine(baseColor.copy(alpha = 0.4f), Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.dp.toPx())
            fun path(values: List<Double>) = Path().apply {
                values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
            }
            baseValues?.let {
                drawPath(path(it), baseColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
            }
            drawPath(path(plan.balances), planColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // A marker where each debt is cleared
            plan.payoffs.forEach { payoff ->
                val c = Offset(x(payoff.month), y(plan.balances.getOrElse(payoff.month) { 0.0 }))
                drawCircle(ring, radius = 6.dp.toPx(), center = c)
                drawCircle(planColor, radius = 4.dp.toPx(), center = c)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted("Now")
            Muted(shortMonthFromNow(months))
        }
    }
}

@Composable
private fun LegendItem(color: Color, dashed: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(18.dp).height(10.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(
                    color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(),
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun OrderCard(plan: PayoffPlan) {
    PlannerCard {
        Text("Order they're cleared", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        plan.payoffs.forEachIndexed { index, payoff ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(24.dp))
                Text(payoff.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(shortMonthFromNow(payoff.month), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Muted("A dot on the chart marks each one.")
    }
}
