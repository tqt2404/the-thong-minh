package viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import data.model.AuthRequest
import data.model.AuthResponse
import data.repository.LoginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import utils.AuthPrefs

sealed class LoginUiState {
    object Initial : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val token: String, val username: String, val role: String) : LoginUiState()
    data class Error(val msg: String) : LoginUiState()
}

class LoginScreenModel(
    private val loginRepository: LoginRepository
) : ScreenModel {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Initial)
    val loginState: StateFlow<LoginUiState> = _loginState

    var username by mutableStateOf("")
    var password by mutableStateOf("")

    fun login() {
        _loginState.value = LoginUiState.Loading
        screenModelScope.launch(Dispatchers.Default) {
            try {
                val resp = loginRepository.login(AuthRequest(username, password))
                if (resp.isSuccessful && resp.body() != null) {
                    val data = resp.body()!!
                    AuthPrefs.saveToken(data.token)
                    _loginState.value = LoginUiState.Success(data.token, data.username, data.role)
                } else {
                    AuthPrefs.clearToken()
                    _loginState.value = LoginUiState.Error("Sai tài khoản hoặc mật khẩu")
                }
            } catch (e: Exception) {
                AuthPrefs.clearToken()
                _loginState.value = LoginUiState.Error("Lỗi kết nối: ${e.message}")
            }
        }
    }

    fun logout() {
        AuthPrefs.clearToken()
        username = ""
        password = ""
        _loginState.value = LoginUiState.Initial
    }
}