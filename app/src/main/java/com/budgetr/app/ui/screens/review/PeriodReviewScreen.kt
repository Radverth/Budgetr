package com.budgetr.app.ui.screens.review

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.data.model.PeriodReview
import com.budgetr.app.data.model.ReviewDestination
import com.budgetr.app.data.model.ReviewReceipt
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.util.PaydayPlanCalculator
import com.budgetr.app.util.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private fun shortDate(text: String): String = runCatching {
    SimpleDateFormat("d MMM", Locale.UK).format(SimpleDateFormat("dd/MM/yyyy", Locale.UK).parse(text)!!)
}.getOrDefault(text)

private fun pounds(pence: Long) = (pence / 100.0).toCurrencyString()

/** How a finished pay period went, and the one choice of where its leftover goes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodReviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: PeriodReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pay period review") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        PeriodReviewContent(
            state = uiState,
            onSelect = viewModel::select,
            onConfirm = viewModel::allocate,
            onRetry = viewModel::retry,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeriodReviewContent(
    state: PeriodReviewUiState,
    onSelect: (String) -> Unit,
    onConfirm: (ReviewDestination, Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val review = state.selected
    if (state.isLoading || review == null) {
        Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.isLoading) CircularProgressIndicator()
            else Text(
                text = "Your first review appears after your next payday, once a full pay period has finished.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.reviews.size > 1) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.reviews.take(6).forEach { r ->
                        FilterChip(
                            selected = r.periodStart == review.periodStart,
                            onClick = { onSelect(r.periodStart) },
                            label = { Text("${shortDate(r.periodStart)} – ${shortDate(r.periodEnd)}") }
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "${shortDate(review.periodStart)} – ${shortDate(review.periodEnd)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item { SpendingCard(review) }
        review.daysUnderTarget?.let { under ->
            item { DaysCard(under = under, total = review.totalDays ?: under) }
        }
        if (review.categories.isNotEmpty()) item { CategoriesCard(review) }
        item {
            LeftoverCard(
                review = review,
                isLatest = state.isLatest,
                receipt = state.receipt,
                availablePence = state.availablePence,
                goals = state.goals,
                categories = state.categories,
                isSaving = state.isSaving,
                error = state.error,
                onConfirm = onConfirm,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun ReviewCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Muted(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun SpendingCard(review: PeriodReview) {
    ReviewCard {
        Text("Day-to-day spending", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val planned = review.planned
        if (planned == null) {
            Text(review.spending.toCurrencyString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Muted("The plan wasn't saved for this period, so there's nothing to compare with.")
        } else {
            val diff = planned - review.spending
            val under = diff >= 0
            Text(
                text = "${review.spending.toCurrencyString()} of ${planned.toCurrencyString()} planned",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = if (planned > 0) (review.spending / planned).toFloat().coerceIn(0f, 1f) else 1f,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (under) IncomeGreen else ExpenseRed,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            )
            StatusLine(
                good = under,
                text = if (under) "${diff.toCurrencyString()} under plan" else "${(-diff).toCurrencyString()} over plan"
            )
        }
        Muted("Bills and fixed costs: ${review.fixedCosts.toCurrencyString()}")
    }
}

/** A result with an icon and words, so it never relies on colour alone. */
@Composable
private fun StatusLine(good: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (good) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (good) IncomeGreen else ExpenseRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DaysCard(under: Int, total: Int) {
    ReviewCard {
        Text("Days under target", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("$under of $total days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Muted("Counted against each day's target from the plan saved at the end of the period, including borrowing and paying forward.")
    }
}

@Composable
private fun CategoriesCard(review: PeriodReview) {
    val planned = review.categories.filter { it.planned != null }
    val unplanned = review.categories.filter { it.planned == null }
    val best = planned.filter { (it.variance ?: 0.0) > 0.005 }.maxByOrNull { it.variance!! }
    val worst = planned.filter { (it.variance ?: 0.0) < -0.005 }.minByOrNull { it.variance!! }
    ReviewCard {
        Text("Spending categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        best?.let { StatusLine(true, "Most under budget: ${it.tag}, ${it.variance!!.toCurrencyString()} under") }
        worst?.let { StatusLine(false, "Most over budget: ${it.tag}, ${(-it.variance!!).toCurrencyString()} over") }
        planned.forEach { category ->
            CategoryRow(category.tag, "${category.spent.toCurrencyString()} of ${category.planned!!.toCurrencyString()}",
                if ((category.variance ?: 0.0) < -0.005) ExpenseRed else null)
        }
        if (unplanned.isNotEmpty()) {
            Text("Without a budget", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            unplanned.sortedByDescending { it.spent }.take(5).forEach { CategoryRow(it.tag, it.spent.toCurrencyString(), null) }
        }
    }
}

@Composable
private fun CategoryRow(name: String, value: String, valueColor: Color?) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LeftoverCard(
    review: PeriodReview,
    isLatest: Boolean,
    receipt: ReviewReceipt?,
    availablePence: Long,
    goals: List<String>,
    categories: List<String>,
    isSaving: Boolean,
    error: String?,
    onConfirm: (ReviewDestination, Long) -> Unit,
    onRetry: () -> Unit
) {
    ReviewCard {
        val end = review.endBalancePence
        if (end <= 0) {
            Text(if (end < 0) "Finished ${pounds(abs(end))} short" else "Nothing left over",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Muted("That's been carried into this pay period.")
            return@ReviewCard
        }
        Text("${pounds(end)} left over", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        when {
            receipt?.complete == true -> StatusLine(true, receiptText(receipt))
            receipt != null -> {
                StatusLine(false, "Still saving: ${receiptText(receipt)}")
                Muted("Google Sheets didn't confirm it. Trying again finishes the same choice; it won't add the money twice.")
                Button(onClick = onRetry, enabled = !isSaving) { Text(if (isSaving) "Saving…" else "Try again") }
            }
            !isLatest -> Muted("It was carried into the next pay period.")
            availablePence <= 0 -> Muted("It's been carried into this pay period. None of it is free to move now, since it's already set aside or spent.")
            else -> {
                Muted("It's already carried into this pay period. You can give up to ${pounds(availablePence)} of it a job.")
                ReviewChoice(availablePence, goals, categories, isSaving, onConfirm)
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ExpenseRed) }
    }
}

private fun receiptText(receipt: ReviewReceipt): String = when (val d = receipt.destination) {
    ReviewDestination.NextPeriod -> "Kept for this pay period"
    is ReviewDestination.Goal -> "${pounds(receipt.amountPence)} added to ${d.name}"
    is ReviewDestination.Category -> "${pounds(receipt.amountPence)} added to the ${d.tag} budget"
}

/**
 * Pick where the leftover goes, then confirm. Nothing is applied until "Confirm choice".
 * Amounts above [availablePence] can't be submitted, and nothing can while [saving].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewChoice(
    availablePence: Long,
    goals: List<String>,
    categories: List<String>,
    saving: Boolean,
    onConfirm: (ReviewDestination, Long) -> Unit
) {
    var destination by remember { mutableStateOf<ReviewDestination>(ReviewDestination.NextPeriod) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    val amountPence = PaydayPlanCalculator.parseAmount(amountText)?.let { (it * 100).roundToLong() }
    val needsAmount = destination != ReviewDestination.NextPeriod
    val canReview = !saving && (!needsAmount || (amountPence != null && amountPence in 1..availablePence))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val options = listOf<ReviewDestination>(ReviewDestination.NextPeriod) +
                goals.map { ReviewDestination.Goal(it) } + categories.map { ReviewDestination.Category(it) }
            options.forEach { option ->
                FilterChip(
                    selected = destination == option,
                    onClick = { destination = option; confirming = false },
                    label = {
                        Text(
                            text = when (option) {
                                ReviewDestination.NextPeriod -> "Keep for this period"
                                is ReviewDestination.Goal -> "Goal: ${option.name}"
                                is ReviewDestination.Category -> "Budget: ${option.tag}"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
        if (needsAmount) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.take(10); confirming = false },
                label = { Text("Amount (£)") },
                supportingText = { Text("Up to ${pounds(availablePence)}") },
                isError = amountPence != null && amountPence > availablePence,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!confirming) {
            OutlinedButton(onClick = { confirming = true }, enabled = canReview, modifier = Modifier.fillMaxWidth()) { Text("Review choice") }
        } else {
            val amount = amountPence ?: 0
            Text(
                text = when (val d = destination) {
                    ReviewDestination.NextPeriod -> "Keep the leftover for this pay period? Nothing moves."
                    is ReviewDestination.Goal -> "Add ${pounds(amount)} to ${d.name}? It updates the goal in your Google Sheet and takes the same amount out of what you can spend."
                    is ReviewDestination.Category -> "Add ${pounds(amount)} to the ${d.tag} budget for this pay period?"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Text("You can only choose once for each pay period.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onConfirm(destination, if (needsAmount) amount else 0L) },
                    enabled = canReview
                ) { Text(if (saving) "Saving…" else "Confirm choice") }
                TextButton(onClick = { confirming = false }, enabled = !saving) { Text("Back") }
            }
        }
    }
}
