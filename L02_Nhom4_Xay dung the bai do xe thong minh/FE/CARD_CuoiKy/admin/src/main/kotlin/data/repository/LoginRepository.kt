package data.repository

import data.api.LoginApi
import data.model.AuthRequest
import data.model.AuthResponse
import retrofit2.Response

class LoginRepository(private val api: LoginApi) {
    suspend fun login(req: AuthRequest): Response<AuthResponse> {
        return api.login(req)
    }
}