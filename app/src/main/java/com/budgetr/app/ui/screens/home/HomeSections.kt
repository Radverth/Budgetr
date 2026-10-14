package com.budgetr.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetr.app.ui.theme.ExpenseRed
import com.budgetr.app.ui.theme.FixedCostOrange
import com.budgetr.app.ui.theme.IncomeGreen
import com.budgetr.app.util.SpendingTrend
import com.budgetr.app.util.TagSpend
import com.budgetr.app.util.toCurrencyString

/** One row of Home's "Needs attention" card. [key] identifies it for dismissing. */
sealed interface AttentionItem {
    val key: String

    data object PaydayPlan : AttentionItem {
        override val key = "payday"
    }

    data class BudgetAlert(val alert: BudgetAlertUiItem, val resetDays: Int) : AttentionItem {
        override val key = "alert:${alert.label.lowercase()}"
    }

    data class GoalSuggestion(val suggestion: GoalSuggestionUiItem) : AttentionItem {
        override val key = "goal:${suggestion.goalName}:${suggestion.category.name}"
    }

    data class SubscriptionReview(val review: RecurringReviewUiItem) : AttentionItem {
        override val key = "review:${review.account}:${review.info}"
    }
}

/** Everything that wants a decision, in one list, most urgent first. Dismissed keys are left out. */
internal fun attentionItems(
    paydayPlanPending: Boolean,
    alerts: List<BudgetAlertUiItem>,
    resetDays: Int,
    suggestions: List<GoalSuggestionUiItem>,
    reviews: List<RecurringReviewUiItem>,
    dismissed: Set<String>
): List<AttentionItem> = buildList {
    if (paydayPlanPending) add(AttentionItem.PaydayPlan)
    alerts.sortedByDescending { it.isOver }.forEach { add(AttentionItem.BudgetAlert(it, resetDays)) }
    suggestions.forEach { add(AttentionItem.GoalSuggestion(it)) }
    reviews.forEach { add(AttentionItem.SubscriptionReview(it)) }
}.filter { it.key !in dismissed }

@Composable
internal fun NeedsAttentionCard(
    items: List<AttentionItem>,
    onPlanPayday: () -> Unit,
    onViewBudgets: () -> Unit,
    onAddToGoal: (GoalSuggestionUiItem) -> Unit,
    onRemindLater: (RecurringReviewUiItem) -> Unit,
    onDismiss: (AttentionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            Text(
                text = if (items.size == 1) "Needs attention" else "Needs attention · ${items.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                when (item) {
                    AttentionItem.PaydayPlan -> AttentionRow(
                        emoji = "💷",
                        title = "It's payday: plan your money",
                        detail = "Cover bills, goals and debts, then give the rest a job.",
                        actionLabel = "Plan now",
                        onAction = onPlanPayday,
                        onDismiss = { onDismiss(item) }
                    )
                    is AttentionItem.BudgetAlert -> AttentionRow(
                        emoji = if (item.alert.isOver) "⛔" else "⚠️",
                        title = "${item.alert.label}: ${item.alert.spend.toCurrencyString()} of ${item.alert.limit.toCurrencyString()}",
                        detail = (if (item.alert.isOver) "Over budget" else "Nearly at the limit") +
                            if (item.resetDays == 1) " · resets tomorrow" else " · resets in ${item.resetDays} days",
                        titleColor = if (item.alert.isOver) ExpenseRed else null,
                        actionLabel = "Budgets",
                        onAction = onViewBudgets,
                        onDismiss = { onDismiss(item) }
                    )
                    is AttentionItem.GoalSuggestion -> AttentionRow(
                        emoji = "🎯",
                        title = "${item.suggestion.underspendAmount.toCurrencyString()} left in ${item.suggestion.category.displayName}",
                        detail = "Move it to \"${item.suggestion.goalName}\"?",
                        actionLabel = "Add",
                        onAction = { onAddToGoal(item.suggestion) },
                        onDismiss = { onDismiss(item) }
                    )
                    is AttentionItem.SubscriptionReview -> AttentionRow(
                        emoji = "🔁",
                        title = "Still need ${item.review.info}?",
                        detail = "${item.review.amount.toCurrencyString()} · unchanged for ${item.review.ageDays / 30} months",
                        actionLabel = "Keep it",
                        onAction = { onRemindLater(item.review) },
                        onDismiss = { onRemindLater(item.review) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AttentionRow(
    emoji: String,
    title: String,
    detail: String?,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    titleColor: Color? = null
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(onClick = onAction) { Text(actionLabel) }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

/**
 * The pay period in one card: money in, out and left, what the outgoings are made of, the
 * biggest spending categories and habits. Replaces the separate income/outgoings tiles,
 * breakdown donut, habits card and navigation cards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThisPeriodCard(
    income: Double,
    fixedCosts: Double,
    oneOffCosts: Double,
    byTag: List<TagSpend>,
    streakDays: Int?,
    trend: SpendingTrend?,
    onSeeHistory: () -> Unit,
    onBudgets: () -> Unit,
    modifier: Modifier = Modifier
) {
    val out = fixedCosts + oneOffCosts
    val left = income - out
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This pay period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Row(modifier = Modifier.fillMaxWidth()) {
                PeriodFigure("In", income, IncomeGreen, Modifier.weight(1f))
                PeriodFigure("Out", out, ExpenseRed, Modifier.weight(1f))
                PeriodFigure("Left", left, if (left < 0) ExpenseRed else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }

            if (income > 0 || out > 0) {
                SplitBar(fixedCosts = fixedCosts, oneOffCosts = oneOffCosts, left = left.coerceAtLeast(0.0))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LegendLabel("Fixed", fixedCosts, FixedCostOrange)
                    LegendLabel("One-off", oneOffCosts, ExpenseRed)
                    if (left > 0) LegendLabel("Left", left, IncomeGreen)
                }
            }

            val tagged = byTag.filter { it.tag != null }
            if (tagged.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = "Top spending categories",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                val max = tagged.maxOf { it.amount }.coerceAtLeast(0.01)
                tagged.take(3).forEach { item -> CategoryBar(item.tag!!, item.amount, (item.amount / max).toFloat()) }
            }

            val habits = buildList {
                streakDays?.let { add(if (it == 1) "🔥 1-day no-spend streak" else "🔥 $it-day no-spend streak") }
                trend?.let { t ->
                    val change = t.percentChange
                    add(
                        "📊 ${t.thisWeek.toCurrencyString()} this week" + when {
                            change == null -> ""
                            change > 0 -> ", up ${change.toInt()}%"
                            change < 0 -> ", down ${-change.toInt()}%"
                            else -> ", same as last week"
                        }
                    )
                }
            }
            if (habits.isNotEmpty()) {
                Text(
                    text = habits.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onBudgets) { Text("Budgets") }
                TextButton(onClick = onSeeHistory) { Text("Spending history") }
            }
        }
    }
}

@Composable
private fun PeriodFigure(label: String, amount: Double, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(
            text = amount.toCurrencyString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Fixed, one-off and what's left as one bar, with a 2dp gap between segments. */
@Composable
private fun SplitBar(fixedCosts: Double, oneOffCosts: Double, left: Double) {
    val segments = listOf(fixedCosts to FixedCostOrange, oneOffCosts to ExpenseRed, left to IncomeGreen).filter { it.first > 0 }
    Row(
        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        segments.forEach { (amount, color) ->
            Box(Modifier.weight(amount.toFloat()).height(10.dp).background(color))
        }
    }
}

@Composable
private fun LegendLabel(label: String, amount: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = "$label ${amount.toCurrencyString()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun CategoryBar(tag: String, amount: Double, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(tag, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(amount.toCurrencyString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
