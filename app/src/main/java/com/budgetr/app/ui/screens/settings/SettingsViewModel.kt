package com.budgetr.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.api.DriveFile
import com.budgetr.app.data.repository.SheetsRepository
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
    val showTimePicker: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager,
    private val authManager: AuthManager,
    private val repository: SheetsRepository
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
            reminderMinute = prefs.getReminderMinute()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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

    fun signOut(onComplete: () -> Unit) {
        authManager.signOut(onComplete)
    }
}
