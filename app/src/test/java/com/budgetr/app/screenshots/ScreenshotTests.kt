package com.budgetr.app.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.budgetr.app.data.model.DailyTransfer
import com.budgetr.app.data.model.Envelope
import com.budgetr.app.data.model.Transaction
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.data.repository.DailyTargetState
import com.budgetr.app.ui.screens.budgethub.BudgetHubContent
import com.budgetr.app.ui.screens.budgethub.BudgetHubUiState
import com.budgetr.app.ui.screens.budgets.EnvelopeCard
import com.budgetr.app.ui.screens.daily.DailyTargetCard
import com.budgetr.app.ui.screens.daily.DailyTransferDialog
import com.budgetr.app.ui.screens.daily.DayRow
import com.budgetr.app.ui.screens.daily.TransferKind
import com.budgetr.app.ui.screens.home.AttentionItem
import com.budgetr.app.ui.screens.home.BudgetAlertUiItem
import com.budgetr.app.ui.screens.home.GoalSuggestionUiItem
import com.budgetr.app.ui.screens.home.HomeHeader
import com.budgetr.app.ui.screens.home.NeedsAttentionCard
import com.budgetr.app.ui.screens.home.RecurringReviewUiItem
import com.budgetr.app.ui.screens.home.ThisPeriodCard
import com.budgetr.app.ui.screens.transactions.TransactionItem
import com.budgetr.app.ui.theme.BudgetrTheme
import com.budgetr.app.util.DailyTargetCalculator
import com.budgetr.app.util.EnvelopeStatus
import com.budgetr.app.util.PayPeriod
import com.budgetr.app.util.SpendingTrend
import com.budgetr.app.util.TagSpend
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Renders key components with fixed sample data so every CI run saves phone-sized PNGs
 * (build/outputs/roborazzi, uploaded as the "screenshots" artifact). They're for looking at,
 * not comparing: nothing fails if a picture changes.
 *
 * Each subclass below runs every test at one screen size / theme.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTests(private val prefix: String) {

    @get:Rule
    val compose = createComposeRule()

    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.UK)
    private fun day(s: String) = fmt.parse(s)!!

    private fun spend(date: String, amount: Double, info: String = "Shop", tag: String? = null) =
        Transaction(rowIndex = 2, date = date, info = info, amount = -amount, category = TransactionCategory.ONE_OFF_COST, account = "Monzo", tag = tag)

    private val period = PayPeriod(day("25/09/2026"), day("26/10/2026"))

    private fun dailyState(spentToday: Double): DailyTargetState {
        val transfers = listOf(DailyTransfer(id = 1, periodStart = "25/09/2026", createdOn = "12/10/2026", amount = 10.0))
        val target = DailyTargetCalculator.calculate(
            period = period,
            pot = 465.0,
            transactions = (25..30).map { spend("%02d/09/2026".format(it), 13.0) } +
                (1..12).map { spend("%02d/10/2026".format(it), 14.5) } +
                listOf(spend("13/10/2026", 9.5), spend("14/10/2026", spentToday)),
            transfers = transfers,
            today = day("14/10/2026")
        )
        return DailyTargetState(target, period, "25/09/2026", 465.0, potIsManual = false, transfers = transfers)
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            BudgetrTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        content()
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${prefix}_$name.png")
    }

    @Test
    fun dailyTargetCard() = shot("01_daily_target_card") {
        DailyTargetCard(state = dailyState(spentToday = 4.2), onOpen = {}, onTransfer = {}, modifier = Modifier.fillMaxWidth())
    }

    @Test
    fun dailyTargetCardOver() = shot("02_daily_target_card_over") {
        DailyTargetCard(state = dailyState(spentToday = 48.0), onOpen = {}, onTransfer = {}, modifier = Modifier.fillMaxWidth())
    }

    @Test
    fun dayRows() = shot("03_day_rows") {
        val target = dailyState(spentToday = 4.2).target
        val i = target.todayIndex
        DayRow(day = target.days[i - 1], base = target.base, isToday = false, isFuture = false)
        DayRow(day = target.days[i], base = target.base, isToday = true, isFuture = false)
        DayRow(day = target.days[i + 1], base = target.base, isToday = false, isFuture = true)
    }

    @Test
    fun categoryBudgets() = shot("04_category_budgets") {
        EnvelopeCard(EnvelopeStatus(Envelope("Groceries", 250.0, rollover = true, carriedOver = 18.5), spent = 131.4, daysLeft = 12), onEdit = {})
        EnvelopeCard(EnvelopeStatus(Envelope("Eating out", 60.0), spent = 72.0, daysLeft = 12), onEdit = {})
        EnvelopeCard(EnvelopeStatus(Envelope("Transport", 80.0), spent = 66.0, daysLeft = 12), onEdit = {})
    }

    @Test
    fun transactionRows() = shot("05_transactions") {
        TransactionItem(transaction = spend("14/10/2026", 4.2, "Tesco Express", "Groceries"), onEdit = {}, onDelete = {})
        TransactionItem(
            transaction = Transaction(3, "26/09/2026", "Salary", 2150.0, TransactionCategory.SALARY, "Monzo"),
            onEdit = {}, onDelete = {}
        )
        TransactionItem(
            transaction = Transaction(4, "25/09/2026", "Rent and a very long description that should be cut off", -825.0, TransactionCategory.FIXED_COST, "Monzo"),
            onEdit = {}, onDelete = {}
        )
        TransactionItem(transaction = spend("13/10/2026", 9.5, "Bus"), onEdit = {}, onDelete = {}, selectionMode = true, selected = true)
    }

    private val attention = listOf(
        AttentionItem.PaydayPlan,
        AttentionItem.BudgetAlert(BudgetAlertUiItem("Eating out", 72.0, 60.0, isOver = true), resetDays = 12),
        AttentionItem.GoalSuggestion(GoalSuggestionUiItem("Holiday", TransactionCategory.ONE_OFF_COST, 42.0)),
        AttentionItem.SubscriptionReview(RecurringReviewUiItem("Monzo", "Netflix", 10.99, ageDays = 210))
    )

    @Composable
    private fun periodCard() = ThisPeriodCard(
        income = 2150.0,
        fixedCosts = 1180.0,
        oneOffCosts = 412.35,
        byTag = listOf(TagSpend("Groceries", 168.2), TagSpend("Eating out", 72.0), TagSpend(null, 61.0), TagSpend("Transport", 54.5)),
        streakDays = 2,
        trend = SpendingTrend(thisWeek = 61.4, lastWeek = 88.0),
        onSeeHistory = {},
        onBudgets = {},
        modifier = Modifier.fillMaxWidth()
    )

    @Test
    fun needsAttention() = shot("07_needs_attention") {
        NeedsAttentionCard(
            items = attention, onPlanPayday = {}, onViewBudgets = {}, onAddToGoal = {},
            onRemindLater = {}, onDismiss = {}, modifier = Modifier.fillMaxWidth()
        )
    }

    @Test
    fun thisPeriod() = shot("08_this_period") { periodCard() }

    /** Roughly what Home shows above the fold, minus the header. */
    @Test
    fun homeTop() = shot("09_home_top") {
        DailyTargetCard(state = dailyState(spentToday = 4.2), onOpen = {}, onTransfer = {}, modifier = Modifier.fillMaxWidth())
        NeedsAttentionCard(
            items = attention.take(2), onPlanPayday = {}, onViewBudgets = {}, onAddToGoal = {},
            onRemindLater = {}, onDismiss = {}, modifier = Modifier.fillMaxWidth()
        )
        periodCard()
    }

    @Test
    fun budgetHub() {
        compose.setContent {
            BudgetrTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BudgetHubContent(
                        state = BudgetHubUiState(
                            daily = dailyState(spentToday = 4.2),
                            budgetCount = 3,
                            budgetsOver = 1,
                            nextPayday = period.nextPayday,
                            historyPeriods = 4
                        ),
                        onOpenBudgets = {}, onOpenDailyTarget = {}, onOpenPaydayPlan = {}, onOpenHistory = {}, onTransfer = {}
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${prefix}_10_budget_hub.png")
    }

    @Test
    fun homeHeader() {
        compose.setContent {
            BudgetrTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column { HomeHeader(userName = "Tom", totalAvailable = 557.65, topPadding = 24.dp, onOpenSettings = {}) }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${prefix}_11_home_header.png")
    }

    /** Dialogs open in their own window, which never reports idle under Robolectric 4.12
     *  (AppNotIdleException), so this is off until Robolectric/Roborazzi are upgraded. */
    @Ignore("Compose dialog windows never go idle under Robolectric 4.12")
    @Test
    fun borrowDialog() {
        compose.setContent {
            BudgetrTheme {
                DailyTransferDialog(state = dailyState(spentToday = 4.2), kind = TransferKind.BORROW, onConfirm = { _, _ -> }, onDismiss = {})
            }
        }
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/${prefix}_06_borrow_dialog.png")
    }
}

// The app class sets up Hilt and encrypted storage, which these tests don't need
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h780dp-xxhdpi")
class SmallPhoneLightScreenshots : ScreenshotTests("360_light")

@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class PhoneLightScreenshots : ScreenshotTests("412_light")

@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-night-xxhdpi")
class PhoneDarkScreenshots : ScreenshotTests("412_dark")
