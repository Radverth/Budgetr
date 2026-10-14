package com.budgetr.app.ui.screens.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.data.repository.DailyTargetRepository
import com.budgetr.app.data.repository.DailyTargetState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class DailyTargetUiState(
    val state: DailyTargetState? = null,
    val message: String? = null
)

@HiltViewModel
class DailyTargetViewModel @Inject constructor(
    private val repository: DailyTargetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyTargetUiState())
    val uiState: StateFlow<DailyTargetUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observe().collect { state -> _uiState.update { it.copy(state = state) } }
        }
    }

    fun addTransfer(amount: Double, targetDay: Date?) {
        val state = _uiState.value.state ?: return
        viewModelScope.launch {
            repository.addTransfer(state, amount, targetDay)
            _uiState.update { it.copy(message = if (amount > 0) "Borrowed from later days" else "Paid forward to later days") }
        }
    }

    fun undoTransfer(id: Long) {
        viewModelScope.launch {
            repository.undoTransfer(id)
            _uiState.update { it.copy(message = "Undone") }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
