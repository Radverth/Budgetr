package com.budgetr.app.util

import kotlin.math.abs

/**
 * Plain words for the daily target when a day starts behind (earlier days overspent, or
 * nothing has arrived yet in "My income varies"), instead of showing negative targets
 * like "spent of -£12.00".
 */
object DailyTargetWording {

    /** "£12.00 left today", "£4.00 over today" (overspent today), or "£16.20 behind today" (started behind). */
    fun headline(leftToday: Double, todayTarget: Double): String = when {
        leftToday >= 0 -> "${leftToday.toCurrencyString()} left today"
        todayTarget < 0 -> "${abs(leftToday).toCurrencyString()} behind today"
        else -> "${abs(leftToday).toCurrencyString()} over today"
    }

    fun spentToday(spent: Double, target: Double): String = when {
        target >= 0 -> "${spent.toCurrencyString()} spent of ${target.toCurrencyString()} today"
        spent > 0.005 -> "${spent.toCurrencyString()} spent today, after starting ${abs(target).toCurrencyString()} behind"
        else -> "Started today ${abs(target).toCurrencyString()} behind from earlier days"
    }

    fun tomorrow(tomorrowIfNoMoreSpent: Double?): String = when {
        tomorrowIfNoMoreSpent == null -> "Last day before payday"
        tomorrowIfNoMoreSpent >= 0 -> "Tomorrow: ${tomorrowIfNoMoreSpent.toCurrencyString()} if you stop now"
        else -> "Tomorrow starts ${abs(tomorrowIfNoMoreSpent).toCurrencyString()} behind, even if you stop now"
    }

    /** Short form for day rows: "£4.20 of £15.00", or "£4.20 · started £12.00 behind". */
    fun spentOf(spent: Double, target: Double): String =
        if (target >= 0) "${spent.toCurrencyString()} of ${target.toCurrencyString()}"
        else "${spent.toCurrencyString()} · started ${abs(target).toCurrencyString()} behind"
}
