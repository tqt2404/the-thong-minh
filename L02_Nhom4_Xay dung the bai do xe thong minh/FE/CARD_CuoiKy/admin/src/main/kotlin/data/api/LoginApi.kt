package data.api

import data.model.AuthRequest
import data.model.AuthResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface LoginApi {
    @POST("/login")
    suspend fun login(@Body req: AuthRequest): Response<AuthResponse>
}