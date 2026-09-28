package com.budgetr.app.util

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager
) {
    companion object {
        const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.metadata.readonly"
        const val TOKEN_SCOPE = "oauth2:$SHEETS_SCOPE $DRIVE_SCOPE"
    }

    // Sheets/Drive scopes are requested here (not just fetched later via GoogleAuthUtil) so the
    // consent screen the user sees during sign-in already covers them. Previously they were only
    // requested when fetching the access token, which silently throws UserRecoverableAuthException
    // for any account that hasn't explicitly granted them — with no UI anywhere to grant it. That
    // left already-"signed in" accounts (per GoogleSignIn's own cache) permanently unable to reach
    // the API, which looked like login being broken and was really the only thing "clear app data"
    // fixed: it dropped the stale signed-in state and forced a full re-consent.
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestProfile()
        .requestScopes(Scope(SHEETS_SCOPE), Scope(DRIVE_SCOPE))
        .build()

    private val client: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(): Intent = client.signInIntent

    // Requires a usable access token too, not just a cached account: an account can be
    // "signed in" per GoogleSignIn's cache while missing scope consent (see above), which
    // otherwise let the app treat the session as valid even though every API call would fail.
    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null && prefs.getAccessToken() != null

    fun handleSignInResult(account: GoogleSignInAccount, accessToken: String) {
        prefs.setAccessToken(accessToken)
        prefs.setUserEmail(account.email)
        prefs.setUserName(account.displayName)
        prefs.setUserPhoto(account.photoUrl?.toString())
    }

    fun getAccessToken(): String? = prefs.getAccessToken()

    /**
     * Clears the cached token and fetches a fresh one. Runs synchronously — call only from
     * a background thread (OkHttp interceptor threads are fine).
     */
    fun refreshToken(): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return try {
            GoogleAuthUtil.clearToken(context, prefs.getAccessToken() ?: return null)
            val newToken = GoogleAuthUtil.getToken(context, account.account!!, TOKEN_SCOPE)
            prefs.setAccessToken(newToken)
            newToken
        } catch (e: UserRecoverableAuthException) {
            // Scope consent is missing or was revoked, and there's no foreground activity to show
            // the recovery prompt from a background interceptor thread. Drop the stored token so
            // isSignedIn() goes false and the next launch routes back to Login for a clean
            // re-consent, instead of leaving the app stuck "signed in" with a token that can never
            // work.
            prefs.setAccessToken(null)
            null
        } catch (e: Exception) {
            null
        }
    }

    fun signOut(onComplete: () -> Unit) {
        client.signOut().addOnCompleteListener {
            prefs.clearAll()
            onComplete()
        }
    }

    fun getUserName(): String? = prefs.getUserName()
    fun getUserEmail(): String? = prefs.getUserEmail()
    fun getUserPhoto(): String? = prefs.getUserPhoto()
}
