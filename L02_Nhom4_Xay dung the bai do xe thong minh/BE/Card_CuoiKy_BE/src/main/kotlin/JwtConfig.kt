package com.example

object JwtConfig {
    const val secret = "change-this-secret"
    const val issuer = "card-be"
    const val audience = "card-admin"
    const val validityInMs = 30L * 24 * 60 * 60 * 1000 // 1 tháng (ms)
}