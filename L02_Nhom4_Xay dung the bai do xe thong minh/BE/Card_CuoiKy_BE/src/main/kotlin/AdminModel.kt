package com.example

data class Admin(
    val id: String,
    val username: String,
    val passwordHash: String,
    val role: String,
    val permissions: List<String>,
    val createdAt: String
)