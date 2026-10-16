package com.budgetr.app.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.repository.BillDueDayRepository
import com.budgetr.app.data.repository.DailyTargetRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.BillsCalendar
import com.budgetr.app.util.CalendarDay
import com.budgetr.app.util.CalendarEvent
import com.budgetr.app.util.DaySpendOn
import com.budgetr.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

data class BillsCalendarUiState(
    val isLoading: Boolean = true,
    val year: Int = 0,
    /** 0-based, like [Calendar.MONTH]. */
    val month0: Int = 0,
    val days: List<CalendarDay> = emptyList(),
    val selected: Date? = null,
    /** Bills and income in the next seven days, from today. */
    val comingUp: List<CalendarEvent> = emptyList(),
    val isCurrentMonth: Boolean = true
) {
    val selectedDay: CalendarDay? get() = selected?.let { s -> days.firstOrNull { it.inMonth && sameDay(it.date, s) } }
}

internal fun sameDay(a: Date, b: Date): Boolean {
    val x = Calendar.getInstance().apply { time = a }
    val y = Calendar.getInstance().apply { time = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

@HiltViewModel
class BillsCalendarViewModel @Inject constructor(
    private val sheetsRepository: SheetsRepository,
    private val billDueDayRepository: BillDueDayRepository,
    private val dailyTargetRepository: DailyTargetRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillsCalendarUiState(selected = Date()))
    val uiState: StateFlow<BillsCalendarUiState> = _uiState.asStateFlow()

    /** Months from this one: 0 = this month, -1 = last month. */
    private val monthOffset = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(
                sheetsRepository.getAllTransactions(),
                billDueDayRepository.getDueDays(),
                dailyTargetRepository.observe(),
                monthOffset
            ) { transactions, dueDays, daily, offset ->
                val today = Date()
                val shown = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, offset) }
                val year = shown.get(Calendar.YEAR)
                val month0 = shown.get(Calendar.MONTH)
                val payDay = prefs.getPayDay()
                val spend = daily.target.days.map { DaySpendOn(it.date, it.spent, it.target) }
                val days = BillsCalendar.grid(year, month0, BillsCalendar.events(year, month0, transactions, dueDays, payDay), spend, today)

                // The next seven days can run into next month
                val now = Calendar.getInstance()
                val next = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, 1) }
                val weekEnd = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 7) }.time
                val comingUp = (BillsCalendar.events(now.get(Calendar.YEAR), now.get(Calendar.MONTH), transactions, dueDays, payDay) +
                    BillsCalendar.events(next.get(Calendar.YEAR), next.get(Calendar.MONTH), transactions, dueDays, payDay))
                    .filter { (it.date.after(today) || sameDay(it.date, today)) && it.date.before(weekEnd) }

                BillsCalendarUiState(isLoading = false, year = year, month0 = month0, days = days, comingUp = comingUp, isCurrentMonth = offset == 0)
            }.collect { state -> _uiState.update { state.copy(selected = it.selected) } }
        }
    }

    fun showMonth(delta: Int) {
        monthOffset.update { it + delta }
        // Select the 1st of other months, today in this one
        val target = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, monthOffset.value) }
        _uiState.update { it.copy(selected = if (monthOffset.value == 0) Date() else target.time) }
    }

    fun select(date: Date) = _uiState.update { it.copy(selected = date) }
}
