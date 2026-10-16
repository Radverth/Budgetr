package com.budgetr.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.budgetr.app.ui.screens.calendar.BillsCalendarScreen
import com.budgetr.app.ui.screens.debts.DebtPlannerScreen
import com.budgetr.app.ui.screens.annual.AnnualBillsScreen
import com.budgetr.app.ui.screens.balances.AccountBalancesScreen
import com.budgetr.app.ui.screens.budgethub.BudgetHubScreen
import com.budgetr.app.ui.screens.budgets.BudgetsScreen
import com.budgetr.app.ui.screens.forecast.CashFlowScreen
import com.budgetr.app.ui.screens.goals.GoalsScreen
import com.budgetr.app.ui.screens.daily.DailyTargetScreen
import com.budgetr.app.ui.screens.home.HomeScreen
import com.budgetr.app.ui.screens.insights.InsightsScreen
import com.budgetr.app.ui.screens.plan.PaydayPlanScreen
import com.budgetr.app.ui.screens.review.PeriodReviewScreen
import com.budgetr.app.ui.screens.rules.CategoryRulesScreen
import com.budgetr.app.ui.screens.settings.SettingsScreen
import com.budgetr.app.ui.screens.transactions.TransactionsScreen

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** Other routes that belong to this tab, so it stays highlighted on them. */
    val alsoSelectedFor: Set<String> = emptySet()
)

private val bottomNavItems = listOf(
    BottomNavItem(NavRoutes.HOME, "Home", Icons.Default.Home),
    BottomNavItem(NavRoutes.TRANSACTIONS, "Transactions", Icons.Default.List, setOf("transactions_tab/{tabName}")),
    BottomNavItem(
        NavRoutes.BUDGET_HUB, "Budget", Icons.Default.PieChart,
        setOf(NavRoutes.BUDGETS, NavRoutes.DAILY_TARGET, NavRoutes.PAYDAY_PLAN, NavRoutes.INSIGHTS)
    ),
    BottomNavItem(NavRoutes.ACCOUNT_BALANCES, "Accounts", Icons.Default.AccountBalance),
    BottomNavItem(NavRoutes.GOALS, "Goals", Icons.Default.Savings)
)

@Composable
fun MainScreen(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    val isSelected = currentDestination?.hierarchy?.any {
                        it.route == item.route || it.route in item.alsoSelectedFor
                    } == true
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(item.route) {
                                // Home always reloads fresh (it's the at-a-glance dashboard);
                                // other tabs keep their scroll/filter state when revisited.
                                val isHomeTab = item.route == NavRoutes.HOME
                                popUpTo(NavRoutes.HOME) { saveState = !isHomeTab }
                                launchSingleTop = true
                                restoreState = !isHomeTab
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = {
                            Text(
                                text = item.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.HOME,
            // consumeWindowInsets prevents HomeScreen's own Scaffold (which has no top bar and
            // manually pads for the status bar) from double-counting the inset this outer
            // Scaffold already reserved.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) }
        ) {
            composable(NavRoutes.HOME) {
                HomeScreen(
                    onNavigateToAddTransaction = {
                        navController.navigate(NavRoutes.TRANSACTIONS) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToBudgets = {
                        navController.navigate(NavRoutes.BUDGETS) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToInsights = {
                        navController.navigate(NavRoutes.INSIGHTS) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToPaydayPlan = {
                        navController.navigate(NavRoutes.PAYDAY_PLAN) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToDailyTarget = {
                        navController.navigate(NavRoutes.DAILY_TARGET) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(NavRoutes.SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToForecast = {
                        navController.navigate(NavRoutes.CASH_FLOW) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToReview = {
                        navController.navigate(NavRoutes.PERIOD_REVIEW) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(NavRoutes.BUDGET_HUB) {
                BudgetHubScreen(
                    onOpenBudgets = { navController.navigate(NavRoutes.BUDGETS) { launchSingleTop = true } },
                    onOpenDailyTarget = { navController.navigate(NavRoutes.DAILY_TARGET) { launchSingleTop = true } },
                    onOpenPaydayPlan = { navController.navigate(NavRoutes.PAYDAY_PLAN) { launchSingleTop = true } },
                    onOpenHistory = { navController.navigate(NavRoutes.INSIGHTS) { launchSingleTop = true } },
                    onOpenRules = { navController.navigate(NavRoutes.CATEGORY_RULES) { launchSingleTop = true } },
                    onOpenForecast = { navController.navigate(NavRoutes.CASH_FLOW) { launchSingleTop = true } },
                    onOpenReview = { navController.navigate(NavRoutes.PERIOD_REVIEW) { launchSingleTop = true } },
                    onOpenAnnualBills = { navController.navigate(NavRoutes.ANNUAL_BILLS) { launchSingleTop = true } },
                    onOpenCalendar = { navController.navigate(NavRoutes.BILLS_CALENDAR) { launchSingleTop = true } }
                )
            }
            composable(NavRoutes.BILLS_CALENDAR) {
                BillsCalendarScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(NavRoutes.ANNUAL_BILLS) {
                AnnualBillsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(NavRoutes.PERIOD_REVIEW) {
                PeriodReviewScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(NavRoutes.CASH_FLOW) {
                CashFlowScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(NavRoutes.CATEGORY_RULES) {
                CategoryRulesScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(NavRoutes.DAILY_TARGET) {
                DailyTargetScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(NavRoutes.BUDGETS) {
                BudgetsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPaydayPlan = {
                        navController.navigate(NavRoutes.PAYDAY_PLAN) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(NavRoutes.PAYDAY_PLAN) {
                PaydayPlanScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(NavRoutes.INSIGHTS) {
                InsightsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(NavRoutes.TRANSACTIONS) {
                TransactionsScreen()
            }
            composable(
                route = "transactions_tab/{tabName}",
                arguments = listOf(navArgument("tabName") { type = NavType.StringType })
            ) {
                // The account name is passed as the raw nav arg; TransactionsViewModel reads
                // it straight from SavedStateHandle, so no need to thread it through here.
                TransactionsScreen()
            }
            composable(NavRoutes.ACCOUNT_BALANCES) {
                AccountBalancesScreen(
                    onNavigateToTransactions = { account ->
                        // Account names can contain spaces/special characters, so they must be
                        // URL-encoded as a path segment.
                        navController.navigate("transactions_tab/${Uri.encode(account)}") {
                            popUpTo(NavRoutes.ACCOUNT_BALANCES) { saveState = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(NavRoutes.GOALS) {
                GoalsScreen(onOpenDebtPlanner = { navController.navigate(NavRoutes.DEBT_PLANNER) { launchSingleTop = true } })
            }
            composable(NavRoutes.DEBT_PLANNER) {
                DebtPlannerScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(NavRoutes.SETTINGS) {
                SettingsScreen(onSignOut = onSignOut, onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
