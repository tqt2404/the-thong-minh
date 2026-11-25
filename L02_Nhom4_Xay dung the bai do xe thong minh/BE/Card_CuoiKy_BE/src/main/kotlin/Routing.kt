package com.example

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.auth.*
import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import com.example.PasswordUtil

fun Application.configureRouting() {
    routing {
        post("/login") {
            val req = call.receive<AdminLoginRequest>()
            val admin = AdminDAO.findByUsername(req.username)
            if (admin != null && PasswordUtil.verify(req.password, admin.passwordHash)) {
                val token = JwtUtil.generateToken(admin)
                call.respond(AdminLoginResponse(token, admin.username, admin.role, admin.permissions))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Sai tài khoản/mật khẩu")
            }
        }

        authenticate("auth-jwt") {
            get("/admin/protected") {
                val principal = call.principal<JWTPrincipal>()!!
                val role = principal.payload.getClaim("role").asString()
                if (role != "admin") {
                    call.respond(HttpStatusCode.Forbidden, "You don't have admin permission")
                } else {
                    call.respondText("Xin chào Admin!")
                }
            }
        }

        cardRoutes()
        paymentRoutes()
        updatePinRoutes()
        cardAuthRoutes()
    }
}