package com.projectcenter.app.ui.auth

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectcenter.app.core.security.SecureTokenStore
import com.projectcenter.app.data.auth.AuthRepository
import com.projectcenter.app.domain.models.GitHubUser
import com.projectcenter.app.domain.models.VercelUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val isLoggedIn: Boolean = false,
    val user: GitHubUser? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val oauthConfigured: Boolean = false,
    val info: String? = null,
    val vercelLoggedIn: Boolean = false,
    val vercelUsername: String? = null,
    val vercelLoading: Boolean = false,
    val vercelError: String? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = SecureTokenStore(application)
    private val authRepo = AuthRepository(application, tokenStore)

    private val _state = MutableStateFlow(
        AuthState(
            isLoggedIn = tokenStore.isLoggedIn(),
            user = tokenStore.getUserLogin()?.let { GitHubUser(0, it, null, null, null) },
            oauthConfigured = authRepo.isOAuthConfigured(),
            vercelLoggedIn = tokenStore.isVercelLoggedIn(),
            vercelUsername = tokenStore.getVercelUser()
        )
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun startLogin() {
        _state.value = _state.value.copy(error = null, info = null, isLoading = true)
        val result = authRepo.startOAuthLogin()
        result.onSuccess {
            _state.value = _state.value.copy(
                isLoading = false,
                info = "Complete sign-in in the browser. Passkeys and saved accounts work there."
            )
        }.onFailure { e ->
            _state.value = _state.value.copy(
                isLoading = false,
                error = e.message ?: "Could not open GitHub login"
            )
        }
    }

    fun signInWithToken(token: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, info = null)
            authRepo.signInWithPersonalAccessToken(token)
                .onSuccess { user ->
                    _state.value = AuthState(
                        isLoggedIn = true,
                        user = user,
                        isLoading = false,
                        oauthConfigured = authRepo.isOAuthConfigured()
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Token sign-in failed"
                    )
                }
        }
    }

    fun openGitHubLogin() = authRepo.openGitHubLoginPage()
    fun openVercelLogin() = authRepo.openVercelLoginPage()

    fun handleOAuthCallback(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            authRepo.handleOAuthCallback(uri)
                .onSuccess { user ->
                    _state.value = AuthState(
                        isLoggedIn = true,
                        user = user,
                        isLoading = false,
                        oauthConfigured = authRepo.isOAuthConfigured()
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Login failed"
                    )
                }
        }
    }

    fun logout() {
        authRepo.logout()
        _state.value = AuthState(
            isLoggedIn = false,
            user = null,
            oauthConfigured = authRepo.isOAuthConfigured()
        )
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    // --- Vercel ---

    fun signInWithVercelToken(token: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(vercelLoading = true, vercelError = null)
            authRepo.signInWithVercelToken(token)
                .onSuccess { user: VercelUser ->
                    _state.value = _state.value.copy(
                        vercelLoggedIn = true,
                        vercelUsername = user.username,
                        vercelLoading = false
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        vercelLoading = false,
                        vercelError = e.message ?: "Vercel token sign-in failed"
                    )
                }
        }
    }

    fun logoutVercel() {
        authRepo.logoutVercel()
        _state.value = _state.value.copy(vercelLoggedIn = false, vercelUsername = null)
    }
}
