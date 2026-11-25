package data.repository

import data.api.*
import data.api.RetrofitInstance.cardApi
import data.model.SignSensitiveOperationRequest
import data.model.SignSensitiveOperationResponse
import utils.AuthPrefs

class CardRepository(private val api: CardApi) {

    suspend fun getBackendPublicKey(): Result<String> {
        val token = AuthPrefs.getToken()
        val resp = api.getBackendPublicKey("Bearer $token")
        return if (resp.isSuccessful && resp.body() != null) {
            Result.success(resp.body()!!.publicKey)
        } else {
            Result.failure(Exception(resp.errorBody()?.string() ?: "Unknown error"))
        }
    }

    suspend fun createCard(): Result<Int> {
        val token = AuthPrefs.getToken()
        val resp = api.createCard("Bearer $token")
        return if (resp.isSuccessful && resp.body() != null) {
            Result.success(resp.body()!!.id)
        } else {
            Result.failure(Exception(resp.errorBody()?.string() ?: "Unknown error"))
        }
    }

    suspend fun updatePublicKey(id: Int, publicKey: String): Result<Unit> {
        val token = AuthPrefs.getToken()
        val resp = api.updatePublicKey("Bearer $token", UpdatePublicKeyRequest(id, publicKey))
        return if (resp.isSuccessful && resp.body()?.success == true) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(resp.body()?.error ?: resp.errorBody()?.string() ?: "Unknown error"))
        }
    }

    suspend fun signSensitiveOperation(token: String, req: SignSensitiveOperationRequest): SignSensitiveOperationResponse {
        return cardApi.signSensitiveOperation("Bearer $token", req)
    }
}