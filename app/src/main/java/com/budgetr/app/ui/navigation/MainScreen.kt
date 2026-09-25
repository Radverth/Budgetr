package com.budgetr.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.budgetr.app.ui.screens.balances.AccountBalancesScreen
import com.budgetr.app.ui.screens.goals.GoalsScreen
import com.budgetr.app.ui.screens.settings.SettingsScreen
import com.budgetr.app.ui.screens.transactions.TransactionsScreen

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(NavRoutes.ACCOUNT_BALANCES, "Accounts", Icons.Default.AccountBalance),
    BottomNavItem(NavRoutes.TRANSACTIONS, "Transactions", Icons.Default.List),
    BottomNavItem(NavRoutes.GOALS, "Goals", Icons.Default.Savings),
    BottomNavItem(NavRoutes.SETTINGS, "Settings", Icons.Default.Settings)
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
                    // Treat "transactions_tab/{tabName}" as part of the Transactions tab
                    val isSelected = if (item.route == NavRoutes.TRANSACTIONS) {
                        currentDestination?.hierarchy?.any {
                            it.route == NavRoutes.TRANSACTIONS || it.route == "transactions_tab/{tabName}"
                        } == true
                    } else {
                        currentDestination?.hierarchy?.any { it.route == item.route } == true
                    }
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(item.route) {
                                val isAccountsTab = item.route == NavRoutes.ACCOUNT_BALANCES
                                popUpTo(NavRoutes.ACCOUNT_BALANCES) { saveState = !isAccountsTab }
                                launchSingleTop = true
                                restoreState = !isAccountsTab
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.ACCOUNT_BALANCES,
            modifier = Modifier.padding(innerPadding)
        ) {
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
                GoalsScreen()
            }
            composable(NavRoutes.SETTINGS) {
                SettingsScreen(onSignOut = onSignOut)
            }
        }
    }
}
