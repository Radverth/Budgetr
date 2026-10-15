package com.budgetr.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Redraws every Budgetr home-screen widget from the latest data on the phone. */
object WidgetRefresher {
    suspend fun refresh(context: Context) {
        runCatching { BudgetrWidget().updateAll(context) }
    }
}
