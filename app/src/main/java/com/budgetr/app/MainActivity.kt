package com.budgetr.app

import android.content.Intent
import com.budgetr.app.widget.QuickAddActivity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.budgetr.app.ui.navigation.BudgetrNavGraph
import com.budgetr.app.ui.theme.BudgetrTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_ADD_EXPENSE = "com.budgetr.app.ADD_EXPENSE"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_ADD_EXPENSE) {
            startActivity(Intent(this, QuickAddActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == ACTION_ADD_EXPENSE) {
            startActivity(Intent(this, QuickAddActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            BudgetrTheme {
                BudgetrNavGraph()
            }
        }
    }
}
