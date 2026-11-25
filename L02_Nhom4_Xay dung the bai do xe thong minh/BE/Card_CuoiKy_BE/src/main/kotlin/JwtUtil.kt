package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

object JwtUtil {
    fun generateToken(admin: Admin): String {
        return JWT.create()
            .withAudience(JwtConfig.audience)
            .withIssuer(JwtConfig.issuer)
            .withClaim("username", admin.username)
            .withClaim("role", admin.role)
            .withClaim("permissions", admin.permissions) // Nếu là List<String>, sẽ thành JSON array
            .withExpiresAt(Date(System.currentTimeMillis() + JwtConfig.validityInMs))
            .sign(Algorithm.HMAC256(JwtConfig.secret))
    }
}