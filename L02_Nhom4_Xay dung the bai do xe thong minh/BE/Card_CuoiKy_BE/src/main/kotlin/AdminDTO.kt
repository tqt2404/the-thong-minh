package com.example

import kotlinx.serialization.Serializable

@Serializable
data class AdminLoginRequest(val username: String, val password: String)

@Serializable
data class AdminLoginResponse(
    val token: String,
    val username: String,
    val role: String,
    val permissions: List<String>
)