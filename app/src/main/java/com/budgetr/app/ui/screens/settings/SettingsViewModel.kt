package com.budgetr.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.api.DriveFile
import com.budgetr.app.data.repository.DailyTargetRepository
import com.budgetr.app.data.repository.SheetsRepository
import com.budgetr.app.util.PaydayPlanCalculator
import com.budgetr.app.util.AuthManager
import com.budgetr.app.util.PreferencesManager
import com.budgetr.app.util.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val spreadsheetId: String = "",
    val spreadsheetName: String = "",
    val userEmail: String? = null,
    val userName: String? = null,
    val savedMessage: String? = null,
    val availableSheets: List<DriveFile> = emptyList(),
    val isLoadingSheets: Boolean = false,
    val showSheetPicker: Boolean = false,
    val sheetPickerError: String? = null,
    val payDay: Int = 26,
    val showPayDayPicker: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val showTimePicker: Boolean = false,
    val spendingPromptEnabled: Boolean = true,
    val spendingPromptThreshold: Double = 20.0,
    val showSpendingPromptThresholdDialog: Boolean = false,
    val spendingPromptThresholdInput: String = "",
    val dailyTargetOnHome: Boolean = true,
    val dailyCountBudgeted: Boolean = true,
    val dailyPot: Double? = null,
    val dailyPotIsManual: Boolean = false,
    val dailyBase: Double? = null,
    val showDailyPotDialog: Boolean = false,
    val dailyPotInput: String = "",
    val homeShowPeriod: Boolean = true,
    val homeShowGoals: Boolean = true,
    val homeShowAccounts: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager,
    private val authManager: AuthManager,
    private val repository: SheetsRepository,
    private val dailyTargetRepository: DailyTargetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            spreadsheetId = prefs.getSpreadsheetId() ?: "",
            spreadsheetName = prefs.getSpreadsheetName() ?: "",
            userEmail = authManager.getUserEmail(),
            userName = authManager.getUserName(),
            payDay = prefs.getPayDay(),
            reminderEnabled = prefs.isReminderEnabled(),
            reminderHour = prefs.getReminderHour(),
            reminderMinute = prefs.getReminderMinute(),
            spendingPromptEnabled = prefs.isSpendingPromptEnabled(),
            spendingPromptThreshold = prefs.getSpendingPromptThreshold(),
            dailyTargetOnHome = prefs.isDailyTargetOnHome(),
            dailyCountBudgeted = prefs.isDailyTargetCountingBudgeted(),
            homeShowPeriod = prefs.isHomeShowingPeriod(),
            homeShowGoals = prefs.isHomeShowingGoals(),
            homeShowAccounts = prefs.isHomeShowingAccounts()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dailyTargetRepository.observe().collect { state ->
                _uiState.update { it.copy(dailyPot = state.pot, dailyPotIsManual = state.potIsManual, dailyBase = state.target.base) }
            }
        }
    }

    fun setHomeShowPeriod(show: Boolean) {
        prefs.setHomeShowingPeriod(show)
        _uiState.update { it.copy(homeShowPeriod = show) }
    }

    fun setHomeShowGoals(show: Boolean) {
        prefs.setHomeShowingGoals(show)
        _uiState.update { it.copy(homeShowGoals = show) }
    }

    fun setHomeShowAccounts(show: Boolean) {
        prefs.setHomeShowingAccounts(show)
        _uiState.update { it.copy(homeShowAccounts = show) }
    }

    fun setDailyTargetOnHome(show: Boolean) {
        prefs.setDailyTargetOnHome(show)
        dailyTargetRepository.notifySettingsChanged()
        _uiState.update { it.copy(dailyTargetOnHome = show) }
    }

    fun setDailyCountBudgeted(count: Boolean) {
        prefs.setDailyTargetCountingBudgeted(count)
        dailyTargetRepository.notifySettingsChanged()
        _uiState.update { it.copy(dailyCountBudgeted = count) }
    }

    fun showDailyPotDialog() = _uiState.update {
        it.copy(showDailyPotDialog = true, dailyPotInput = prefs.getDailyPotOverride()?.toString() ?: "")
    }

    fun dismissDailyPotDialog() = _uiState.update { it.copy(showDailyPotDialog = false) }

    fun setDailyPotInput(value: String) = _uiState.update { it.copy(dailyPotInput = value) }

    /** Blank switches back to working the amount out automatically. */
    fun confirmDailyPot() {
        val input = _uiState.value.dailyPotInput
        val amount = PaydayPlanCalculator.parseAmount(input)
        if (input.isNotBlank() && amount == null) return
        prefs.setDailyPotOverride(amount)
        dailyTargetRepository.notifySettingsChanged()
        _uiState.update { it.copy(showDailyPotDialog = false, savedMessage = "Daily spend target updated") }
    }

    /** Works the pot out again from current income and fixed costs. */
    fun recalculateDailyPot() {
        prefs.setDailyPotOverride(null)
        dailyTargetRepository.recalculatePot()
        _uiState.update { it.copy(showDailyPotDialog = false, savedMessage = "Recalculated from your income and fixed costs") }
    }

    fun loadSpreadsheets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSheets = true, showSheetPicker = true, sheetPickerError = null) }
            try {
                val sheets = repository.listSpreadsheets()
                _uiState.update { it.copy(availableSheets = sheets, isLoadingSheets = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingSheets = false,
                        showSheetPicker = false,
                        sheetPickerError = "Could not load sheets. If this is your first time, please sign out and back in to grant Google Drive access."
                    )
                }
            }
        }
    }

    fun selectSpreadsheet(id: String, name: String) {
        prefs.setSpreadsheetId(id)
        prefs.setSpreadsheetName(name)
        _uiState.update {
            it.copy(
                spreadsheetId = id,
                spreadsheetName = name,
                showSheetPicker = false,
                savedMessage = "\"$name\" selected!"
            )
        }
    }

    fun dismissSheetPicker() = _uiState.update { it.copy(showSheetPicker = false) }

    fun clearSavedMessage() = _uiState.update { it.copy(savedMessage = null) }

    fun showPayDayPicker() = _uiState.update { it.copy(showPayDayPicker = true) }

    fun dismissPayDayPicker() = _uiState.update { it.copy(showPayDayPicker = false) }

    fun setPayDay(day: Int) {
        prefs.setPayDay(day)
        _uiState.update { it.copy(payDay = day, showPayDayPicker = false, savedMessage = "Pay day set to the ${day}${ordinalSuffix(day)} of each month") }
    }

    private fun ordinalSuffix(n: Int): String = when {
        n in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }

    /** Call only after POST_NOTIFICATIONS has been granted (or isn't required below API 33) —
     *  the actual runtime permission prompt has to be triggered from the Composable. */
    fun setReminderEnabled(enabled: Boolean) {
        prefs.setReminderEnabled(enabled)
        if (enabled) {
            ReminderScheduler.schedule(context, prefs.getReminderHour(), prefs.getReminderMinute())
        } else {
            ReminderScheduler.cancel(context)
        }
        _uiState.update { it.copy(reminderEnabled = enabled) }
    }

    fun showTimePicker() = _uiState.update { it.copy(showTimePicker = true) }
    fun dismissTimePicker() = _uiState.update { it.copy(showTimePicker = false) }

    fun setReminderTime(hour: Int, minute: Int) {
        prefs.setReminderTime(hour, minute)
        _uiState.update { it.copy(reminderHour = hour, reminderMinute = minute, showTimePicker = false) }
        if (_uiState.value.reminderEnabled) {
            ReminderScheduler.schedule(context, hour, minute)
        }
    }

    fun setSpendingPromptEnabled(enabled: Boolean) {
        prefs.setSpendingPromptEnabled(enabled)
        _uiState.update { it.copy(spendingPromptEnabled = enabled) }
    }

    fun showSpendingPromptThresholdDialog() = _uiState.update {
        it.copy(showSpendingPromptThresholdDialog = true, spendingPromptThresholdInput = it.spendingPromptThreshold.toString())
    }

    fun dismissSpendingPromptThresholdDialog() = _uiState.update { it.copy(showSpendingPromptThresholdDialog = false) }

    fun setSpendingPromptThresholdInput(value: String) = _uiState.update { it.copy(spendingPromptThresholdInput = value) }

    fun confirmSpendingPromptThreshold() {
        val amount = _uiState.value.spendingPromptThresholdInput.toDoubleOrNull() ?: return
        prefs.setSpendingPromptThreshold(amount)
        _uiState.update { it.copy(spendingPromptThreshold = amount, showSpendingPromptThresholdDialog = false) }
    }

    fun signOut(onComplete: () -> Unit) {
        authManager.signOut(onComplete)
    }
}
