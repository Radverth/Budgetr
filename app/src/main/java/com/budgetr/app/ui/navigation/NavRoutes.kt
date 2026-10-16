package com.budgetr.app.ui.navigation

object NavRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"

    // Bottom nav tabs (used inside MainScreen's nested NavHost)
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val ACCOUNT_BALANCES = "account_balances"
    const val GOALS = "goals"
    const val BUDGET_HUB = "budget"

    // Reached from the gear on Home
    const val SETTINGS = "settings"

    // Reachable from Home, not a bottom nav tab
    const val BUDGETS = "budgets"
    const val INSIGHTS = "insights"
    const val PAYDAY_PLAN = "payday_plan"
    const val DAILY_TARGET = "daily_target"
    const val CATEGORY_RULES = "category_rules"
    const val CASH_FLOW = "cash_flow"
    const val PERIOD_REVIEW = "period_review"
}
