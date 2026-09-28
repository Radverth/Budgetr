package com.budgetr.app.ui.screens.login

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetr.app.util.AuthManager
import com.budgetr.app.util.PreferencesManager
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun getSignInIntent(): Intent = authManager.getSignInIntent()

    fun handleSignInResult(data: Intent?, onSuccess: () -> Unit, onNeedsOnboarding: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account: GoogleSignInAccount = task.await()

                // Fetch a real OAuth access token for Sheets + Drive scopes on IO thread
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        context,
                        account.account!!,
                        AuthManager.TOKEN_SCOPE
                    )
                }

                authManager.handleSignInResult(account, token)
                _uiState.update { it.copy(isLoading = false) }
                if (prefs.hasSpreadsheet()) {
                    onSuccess()
                } else {
                    onNeedsOnboarding()
                }
            } catch (e: ApiException) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Sign-in failed: ${e.statusCode}")
                }
            } catch (e: UserRecoverableAuthException) {
                // Google didn't grant Sheets/Drive access even though the consent screen asked
                // for it — happens with e.g. Workspace accounts an admin has restricted. Retrying
                // the same flow won't help; the user needs a different account or admin approval.
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Google didn't grant access to Sheets/Drive for this account. Try a different account, or ask your Google Workspace admin to allow it."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Unknown error")
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
