package data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class VerifyPaymentRequest(
    val cardId: String,
    val amount: Long,
    val newBalance: Long,
    val message: String,
    val signature: String
)

@Serializable
data class VerifyPaymentResponse(
    val success: Boolean,
    val message: String,
   // val backendSignature: String
)

@Serializable
data class VerifyUpdatePinRequest(
    val cardId: String,
    val newPin: String,
    val message: String,
    val signature: String
)

@Serializable
data class VerifyUpdatePinResponse(
    val success: Boolean,
    val message: String,
    //val backendSignature: String
)
@Serializable
data class VerifyCardAuthRequest(
    val cardId: String,
    val message: String,
    val signature: String
)

@Serializable
data class VerifyCardAuthResponse(
    val success: Boolean,
    val message: String
)


interface CardApi {
    suspend fun verifyPayment(request: VerifyPaymentRequest): VerifyPaymentResponse
    suspend fun verifyUpdatePin(request: VerifyUpdatePinRequest): VerifyUpdatePinResponse
    suspend fun verifyCardAuth(request: VerifyCardAuthRequest): VerifyCardAuthResponse
}

class CardApiImpl(private val client: HttpClient, private val baseUrl: String) : CardApi {
    override suspend fun verifyPayment(request: VerifyPaymentRequest): VerifyPaymentResponse {
        return client.post("$baseUrl/verify-payment") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun verifyUpdatePin(request: VerifyUpdatePinRequest): VerifyUpdatePinResponse {
        return client.post("$baseUrl/verify-update-pin") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    override suspend fun verifyCardAuth(request: VerifyCardAuthRequest): VerifyCardAuthResponse {
        return client.post("$baseUrl/verify-card-auth") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}