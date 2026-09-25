package com.budgetr.app.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetr.app.R
import com.budgetr.app.data.model.TransactionCategory
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.ui.screens.transactions.AddEditTransactionSheet
import com.budgetr.app.ui.screens.transactions.TransactionsViewModel
import com.budgetr.app.ui.theme.BudgetrTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Lightweight activity launched from the home screen widget.
 *
 * Normal categories (One Off, Fixed): uses a transparent/translucent window theme so the
 * bottom sheet appears as an overlay on the launcher.
 *
 * Transfer (EXTRA_FULL_APP = true): switches to the solid app theme before onCreate so the
 * window has a proper background — necessary because the transfer form requires a destination
 * account picker and benefits from a full-screen context.
 */
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CATEGORY = "quick_add_category"
        const val EXTRA_FULL_APP = "quick_add_full_app"
    }

    @Inject lateinit var repository: SheetsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must call setTheme before super.onCreate so the window background is applied correctly
        if (intent.getBooleanExtra(EXTRA_FULL_APP, false)) {
            setTheme(R.style.Theme_Budgetr)
        }
        super.onCreate(savedInstanceState)

        val presetCategory = intent.getStringExtra(EXTRA_CATEGORY)
            ?.let { runCatching { TransactionCategory.valueOf(it) }.getOrNull() }
            ?: TransactionCategory.ONE_OFF_COST

        setContent {
            BudgetrTheme {
                val viewModel: TransactionsViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.showAddSheet()
                }

                val selectedAccount = uiState.selectedAccount

                // After a successful save, refresh balances then update widget and close
                LaunchedEffect(uiState.addSaveCount) {
                    if (uiState.addSaveCount > 0) {
                        try {
                            repository.refreshAccountBalances()
                            val manager = GlanceAppWidgetManager(applicationContext)
                            val ids = manager.getGlanceIds(BudgetrWidget::class.java)
                            ids.forEach { BudgetrWidget().update(applicationContext, it) }
                        } catch (_: Exception) {}
                        finish()
                    }
                }

                if (uiState.showAddSheet && selectedAccount != null) {
                    AddEditTransactionSheet(
                        existingTransaction = null,
                        currentAccount = selectedAccount,
                        accounts = uiState.accounts,
                        addSaveCount = uiState.addSaveCount,
                        onSave = viewModel::saveTransaction,
                        onSaveTransfer = viewModel::saveTransfer,
                        onDismiss = { finish() },
                        presetCategory = presetCategory
                    )
                }
            }
        }
    }
}
