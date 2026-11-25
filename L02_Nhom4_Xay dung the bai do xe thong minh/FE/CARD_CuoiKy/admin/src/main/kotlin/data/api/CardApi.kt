package data.api

import data.model.SignSensitiveOperationRequest
import data.model.SignSensitiveOperationResponse
import retrofit2.Response
import retrofit2.http.*

data class CreateCardResponse(val id: Int)
data class UpdatePublicKeyRequest(val id: Int, val publicKey: String)
data class ApiResponse(val success: Boolean, val error: String? = null)
data class BackendPublicKeyResponse(val publicKey: String)


interface CardApi {
    @GET("/backend-public-key")
    suspend fun getBackendPublicKey(@Header("Authorization") token: String): Response<BackendPublicKeyResponse>

    @POST("/cards")
    suspend fun createCard(@Header("Authorization") token: String): Response<CreateCardResponse>

    @PUT("/cards")
    suspend fun updatePublicKey(
        @Header("Authorization") token: String,
        @Body request: UpdatePublicKeyRequest
    ): Response<ApiResponse>

    @POST("/sign-sensitive")
    suspend fun signSensitiveOperation(
        @Header("Authorization") token: String,
        @Body req: SignSensitiveOperationRequest
    ): SignSensitiveOperationResponse

}