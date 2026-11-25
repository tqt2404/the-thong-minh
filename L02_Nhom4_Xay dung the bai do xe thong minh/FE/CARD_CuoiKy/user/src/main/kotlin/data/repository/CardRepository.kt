package data.repository

import data.api.*
import data.api.RetrofitInstance.cardApi

class CardRepository(private val api: CardApi) {
    suspend fun verifyPayment(
        cardId: String,
        amount: Long,
        newBalance: Long,
        message: String,
        signature: String
    ): VerifyPaymentResponse {
        return api.verifyPayment(
            VerifyPaymentRequest(
                cardId = cardId,
                amount = amount,
                newBalance = newBalance,
                message = message,
                signature = signature
            )
        )
    }

    suspend fun verifyUpdatePin(
        cardId: String,
        newPin: String,
        message: String,
        signature: String
    ): VerifyUpdatePinResponse {
        return cardApi.verifyUpdatePin(
            VerifyUpdatePinRequest(
                cardId = cardId,
                newPin = newPin,
                message = message,
                signature = signature
            )
        )
    }

    suspend fun verifyCardAuth(
        cardId: String,
        message: String,
        signature: String
    ): VerifyCardAuthResponse {
        return api.verifyCardAuth(
            VerifyCardAuthRequest(
                cardId = cardId,
                message = message,
                signature = signature
            )
        )
    }
}