package data.model

data class AuthResponse(
    val token: String,
    val username: String,
    val role: String,
    val permissions: List<String>
)