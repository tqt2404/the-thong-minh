package data.model

data class SignSensitiveOperationRequest(
    val operation: String,
    val data: String
)
data class SignSensitiveOperationResponse(
    val message: String,
    val signature: String
)