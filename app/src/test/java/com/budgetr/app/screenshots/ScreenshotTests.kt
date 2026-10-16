package com.budgetr.app.screenshots

import com.budgetr.app.data.model.PeriodReview
import com.budgetr.app.data.model.ReviewCategory
import com.budgetr.app.data.model.ReviewReceipt
import com.budgetr.app.ui.screens.review.PeriodReviewContent
import com.budgetr.app.ui.screens.review.PeriodReviewUiState
import com.budgetr.app.ui.screens.review.ReviewChoice
import com.budgetr.app.data.repository.BillRow
import com.budgetr.app.ui.screens.forecast.CashFlowContent
import com.budgetr.app.ui.screens.forecast.CashFlowUiState
import com.budgetr.app.util.CashFlowCalculator
import com.budgetr.app.util.ForecastWarning
import com.budgetr.app.data.model.CategoryRule
import com.budgetr.app.ui.screens.rules.CategoryRulesContent
import com.budgetr.app.ui.screens.rules.CategoryRulesUiState
import com.budgetr.app.ui.screens.rules.RuleRow
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Assert.assertEquals
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.budgetr.app.data.model.DailyTransfer
import com.budgetr.app.data.model.Envelope
import com.budgetr.app.data.model.PendingChange
import com.budgetr.app.data.model.PendingKind
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
import com.budgetr.app.ui.screens.onboarding.AccountsStep
import com.budgetr.app.ui.screens.settings.BackupRows
import com.budgetr.app.ui.screens.onboarding.FixedCostsStep
import com.budgetr.app.ui.screens.onboarding.PaydayStep
import com.budgetr.app.ui.screens.onboarding.SummaryStep
import com.budgetr.app.ui.screens.transactions.QuickAddFields
import com.budgetr.app.ui.screens.transactions.DayHeader
import com.budgetr.app.ui.screens.transactions.PendingSyncBanner
import com.budgetr.app.ui.screens.transactions.DayTotals
import com.budgetr.app.ui.screens.transactions.TransactionItem
import com.budgetr.app.ui.theme.BudgetrTheme
import com.budgetr.app.util.DailyTargetCalculator
import com.budgetr.app.util.EnvelopeStatus
import com.budgetr.app.util.PayPeriod
import com.budgetr.app.util.SetupFixedCost
import com.budgetr.app.util.SetupSummary
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
    fun typingFiltersSuggestionsAndPickingKeepsTheAmount() {
        val description = mutableStateOf("")
        var picked: Transaction? = null
        val coffee = spend("14/10/2026", 3.00, "Coffee", "Eating out")
        compose.setContent {
            BudgetrTheme {
                QuickAddFields("12.50", {}, description.value, { description.value = it },
                    listOf(coffee, spend("13/10/2026", 40.00, "Tesco")),
                    { picked = it; description.value = it.info }, FocusRequester())
            }
        }
        compose.onNodeWithText("Description").performTextInput("Coff")
        compose.onNodeWithText("Tesco").assertDoesNotExist()
        compose.onNodeWithText("Coffee").performClick()
        assertEquals(coffee, picked)
        assertEquals("Coffee", description.value)
        compose.onNodeWithText("12.50").assertIsDisplayed()
    }

    @Test
    fun quickAddFields() = shot("09_quick_add") {
        Text("Add Transaction", style = MaterialTheme.typography.titleLarge)
        QuickAddFields(
            amount = "12.50", onAmountChange = {}, description = "", onDescriptionChange = {},
            suggestions = listOf("Tesco", "Coffee", "Bus", "A very long description from the weekly supermarket shop").map {
                spend("14/10/2026", 12.50, it, "Groceries")
            },
            onSuggestion = {}, amountFocus = FocusRequester()
        )
        Text("Account: Monzo", style = MaterialTheme.typography.bodyMedium)
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

    /** Same spacing as the real list: rows bring their own 16dp side padding. */
    private fun listShot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            BudgetrTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        content()
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${prefix}_$name.png")
    }

    @Test
    fun transactionRows() = listShot("05_transactions") {
        DayHeader(date = "14/10/2026", totals = DayTotals(spent = 52.7, target = 46.96))
        TransactionItem(transaction = spend("14/10/2026", 4.2, "Tesco Express", "Groceries"), onClick = {}, showDate = false)
        TransactionItem(transaction = spend("14/10/2026", 48.5, "Dinner with Sam", "Eating out"), onClick = {}, showDate = false, showAccount = true)
        DayHeader(date = "13/10/2026", totals = DayTotals(spent = 9.5, target = 42.23))
        TransactionItem(transaction = spend("13/10/2026", 9.5, "Bus"), onClick = {}, showDate = false, selectionMode = true, selected = true)
        DayHeader(date = "26/09/2026", totals = null)
        TransactionItem(
            transaction = Transaction(3, "26/09/2026", "Salary", 2150.0, TransactionCategory.SALARY, "Monzo"),
            onClick = {}, showDate = false
        )
        TransactionItem(
            transaction = Transaction(4, "25/09/2026", "Rent and a very long description that should be cut off", -825.0, TransactionCategory.FIXED_COST, "Monzo"),
            onClick = {}
        )
    }

    private val attention = listOf(
        AttentionItem.PaydayPlan,
        AttentionItem.CashFlowWarning(ForecastWarning("Monzo", day("23/10/2026"), shortfall = 41.5, moveAmount = 45.0, fromAccount = "Halifax")),
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
                            historyPeriods = 4,
                            ruleCount = 3,
                            forecastWarning = ForecastWarning("Monzo", day("23/10/2026"), shortfall = 41.5, moveAmount = 45.0, fromAccount = "Halifax"),
                            forecastReady = true
                        ),
                        onOpenBudgets = {}, onOpenDailyTarget = {}, onOpenPaydayPlan = {}, onOpenHistory = {}, onOpenRules = {},
                        onOpenForecast = {}, onTransfer = {}
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

    private fun screenShot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            BudgetrTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${prefix}_$name.png")
    }

    @Test
    fun setupPayday() = screenShot("12_setup_payday") { PaydayStep(payDay = 26, onPayDay = {}, onBack = {}, onNext = {}) }

    @Test
    fun setupAccounts() = screenShot("13_setup_accounts") {
        AccountsStep(
            accounts = listOf("Monzo", "Halifax Credit Card"),
            input = "Bills/Joint",
            error = "Names can't contain : \\ / ? * [ or ]",
            onInput = {}, onAdd = {}, onRemove = {}, onBack = {}, onNext = {}
        )
    }

    @Test
    fun setupBills() = screenShot("14_setup_bills") {
        FixedCostsStep(
            costs = listOf(SetupFixedCost("Rent", "850", "Monzo"), SetupFixedCost("Council tax", "142.50", "Halifax Credit Card")),
            accounts = listOf("Monzo", "Halifax Credit Card"),
            onAdd = {}, onUpdate = { _, _ -> }, onRemove = {}, onBack = {}, onNext = {}
        )
    }

    @Test
    fun setupSummary() = screenShot("15_setup_summary") {
        SummaryStep(
            summary = SetupSummary(income = 2150.0, fixedCosts = 1180.0, pot = 970.0, days = 31, perDay = 31.29),
            isCreating = false, onBack = {}, onCreate = {}
        )
    }

    @Test
    fun pendingSync() = listShot("16_pending_sync") {
        PendingSyncBanner(
            changes = listOf(
                PendingChange(1, PendingKind.ADD, spend("15/10/2026", 3.4, "Coffee")),
                PendingChange(2, PendingKind.UPDATE, spend("14/10/2026", 9.5, "Bus")),
                PendingChange(3, PendingKind.DELETE, spend("13/10/2026", 4.2, "Tesco"), failedReason = "This transaction was changed or removed in your Google Sheet.")
            ),
            isSyncing = false, onSyncNow = {}, onReview = {},
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        DayHeader(date = "15/10/2026", totals = DayTotals(spent = 3.4, target = 31.29))
        TransactionItem(transaction = spend("15/10/2026", 3.4, "Coffee", "Eating out").copy(rowIndex = -1, pending = true), onClick = {}, showDate = false)
        TransactionItem(transaction = spend("15/10/2026", 12.0, "Lunch", "Eating out"), onClick = {}, showDate = false)
    }

    @Test
    fun backupRows() = listShot("17_backup_rows") {
        BackupRows(lastBackupAt = 1_791_966_000_000, isBusy = false, onBackUp = {}, onRestore = {})
        BackupRows(lastBackupAt = 0, isBusy = true, onBackUp = {}, onRestore = {})
    }

    @Test
    fun categoryRules() = screenShot("18_category_rules") {
        CategoryRulesContent(
            state = CategoryRulesUiState(
                isLoading = false,
                rules = listOf(
                    RuleRow(CategoryRule(1, "TESCO", "Groceries"), pastCount = 14, pastRetagged = 2),
                    RuleRow(CategoryRule(2, "Tesco Petrol Station Leeds Road", "Transport"), pastCount = 0, pastRetagged = 0),
                    RuleRow(CategoryRule(3, "Greggs", "Eating out"), pastCount = 1, pastRetagged = 0)
                ),
                suggestions = listOf(CategoryRule(pattern = "Boots", tag = "Health"), CategoryRule(pattern = "Trainline", tag = "Transport"))
            ),
            onAdd = {}, onEdit = {}, onApply = {}, onAddSuggestion = {}
        )
    }

    @Test
    fun categoryRulesEmpty() = screenShot("19_category_rules_empty") {
        CategoryRulesContent(
            state = CategoryRulesUiState(isLoading = false, suggestions = listOf(CategoryRule(pattern = "Aldi", tag = "Groceries"))),
            onAdd = {}, onEdit = {}, onApply = {}, onAddSuggestion = {}
        )
    }

    private fun bill(account: String, info: String, amount: Double, dueDay: Int?) =
        BillRow(account, info, -amount, dueDay, CashFlowCalculator.dueDate(dueDay, period))

    private fun cashFlowState(monzo: Double): CashFlowUiState {
        val dueDays = mapOf("Monzo" to "Gym" to 18, "Monzo" to "Council tax" to 20, "Halifax" to "Car insurance" to 22)
        val transactions = listOf(
            Transaction(2, "25/09/2026", "Gym", -30.0, TransactionCategory.FIXED_COST, "Monzo"),
            Transaction(3, "25/09/2026", "Council tax", -142.5, TransactionCategory.FIXED_COST, "Monzo"),
            Transaction(4, "25/09/2026", "Rent", -850.0, TransactionCategory.FIXED_COST, "Halifax"),
            Transaction(5, "25/09/2026", "Car insurance", -48.0, TransactionCategory.FIXED_COST, "Halifax"),
            Transaction(6, "24/10/2026", "Lodger", 300.0, TransactionCategory.RECURRING_INCOME, "Halifax")
        )
        val forecast = CashFlowCalculator.forecast(
            period = period, today = day("14/10/2026"),
            balances = linkedMapOf("Monzo" to monzo, "Halifax" to 640.0),
            transactions = transactions,
            dueDays = dueDays.mapKeys { (key, _) -> CashFlowCalculator.billKey(key.first, key.second) },
            spendingAccount = "Monzo",
            expectedSpend = List(12) { 15.0 }
        )
        return CashFlowUiState(
            isLoading = false, forecast = forecast, accounts = listOf("Monzo", "Halifax"), spendingAccountIsAuto = true,
            spendPerDay = 15.0, nextPayday = period.nextPayday,
            bills = listOf(
                bill("Halifax", "Rent", 850.0, null), bill("Monzo", "Gym", 30.0, 18),
                bill("Monzo", "Council tax", 142.5, 20), bill("Halifax", "Car insurance", 48.0, 22)
            )
        )
    }

    @Test
    fun cashFlowWarning() = screenShot("20_cash_flow_warning") {
        CashFlowContent(state = cashFlowState(monzo = 120.0), onSpendingAccount = {}, onEditBill = {})
    }

    @Test
    fun cashFlowOk() = screenShot("21_cash_flow_ok") {
        CashFlowContent(state = cashFlowState(monzo = 420.0), onSpendingAccount = {}, onEditBill = {})
    }

    private fun review(planned: Double?, spending: Double, endPence: Long) = PeriodReview(
        periodStart = "25/09/2026", periodEnd = "25/10/2026", nextPeriodStart = "26/10/2026",
        spending = spending, fixedCosts = 1180.0, planned = planned,
        daysUnderTarget = planned?.let { 19 }, totalDays = planned?.let { 31 },
        categories = if (planned == null) listOf(ReviewCategory("Groceries", 168.2, null), ReviewCategory("Eating out", 72.0, null)) else listOf(
            ReviewCategory("Groceries", 168.2, 200.0),
            ReviewCategory("Christmas presents for the whole family", 95.0, 100.0),
            ReviewCategory("Eating out", 84.5, 60.0),
            ReviewCategory("Transport", 31.0, null)
        ),
        endBalancePence = endPence, reservedCarryPence = 0
    )

    private fun reviewState(review: PeriodReview, receipt: ReviewReceipt? = null, available: Long = 4500) = PeriodReviewUiState(
        isLoading = false, reviews = listOf(review), selectedStart = review.periodStart, receipt = receipt,
        availablePence = available, goals = listOf("Holiday in Portugal next summer", "Emergency fund"), categories = listOf("Groceries", "Eating out")
    )

    @Test
    fun periodReviewReady() = screenShot("22_period_review") {
        PeriodReviewContent(reviewState(review(465.0, 412.35, 5765)), onSelect = {}, onConfirm = { _, _ -> }, onRetry = {})
    }

    @Test
    fun periodReviewOverspentNoPlan() = screenShot("23_period_review_no_plan") {
        PeriodReviewContent(reviewState(review(null, 512.0, -4120)), onSelect = {}, onConfirm = { _, _ -> }, onRetry = {})
    }

    @Test
    fun periodReviewDone() = screenShot("24_period_review_done") {
        PeriodReviewContent(
            reviewState(review(465.0, 498.0, 5765), receipt = ReviewReceipt("op", ReviewReceipt.KIND_GOAL, "Holiday in Portugal next summer", 2500, complete = true), available = 0),
            onSelect = {}, onConfirm = { _, _ -> }, onRetry = {}
        )
    }

    @Test
    fun periodReviewChoice() = shot("25_review_choice") {
        ReviewChoice(4500, listOf("Holiday in Portugal next summer", "Emergency fund"), listOf("Groceries"), saving = false) { _, _ -> }
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
