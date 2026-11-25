package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

data class UpdatePublicKeyRequest(val id: Int, val publicKey: String)
data class BackendPublicKeyResponse(val publicKey: String)


data class SignSensitiveOperationRequest(
    val operation: String,
    val data: String
)
data class SignSensitiveOperationResponse(
    val message: String,
    val signature: String
)
data class CardPublicKeyResponse(
    val id: Int,
    val publicKey: String?
)


fun Route.cardRoutes() {

    authenticate("auth-jwt") {


        get("/backend-public-key") {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "admin") {
                call.respond(HttpStatusCode.Forbidden, "You don't have admin permission")
                return@get
            }
            val pubKey = KeyUtil.getPublicKey() as RSAPublicKey
            val modulus = pubKey.modulus.toByteArray()
            val exponent = pubKey.publicExponent.toByteArray()
            val modulusHex = modulus.joinToString("") { "%02X".format(it) }
            val exponentHex = exponent.joinToString("") { "%02X".format(it) }
            val formatted = "${modulus.size}:$modulusHex:${exponent.size}:$exponentHex"
            call.respond(BackendPublicKeyResponse(formatted))
        }

        post("/cards") {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "admin") {
                call.respond(HttpStatusCode.Forbidden, "You don't have admin permission")
                return@post
            }
            val id = CardDAO.createCard()
            call.respond(mapOf("id" to id))
        }

        put("/cards") {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "admin") {
                call.respond(HttpStatusCode.Forbidden, "You don't have admin permission")
                return@put
            }
            val request = call.receive<UpdatePublicKeyRequest>()
            val updated = CardDAO.updatePublicKey(request.id, request.publicKey)
            if (updated) {
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Card not found"))
            }
        }

        post("/sign-sensitive") {
            val principal = call.principal<JWTPrincipal>()!!
            val role = principal.payload.getClaim("role").asString()
            if (role != "admin") {
                call.respond(HttpStatusCode.Forbidden, "You don't have admin permission")
                return@post
            }
            val req = call.receive<SignSensitiveOperationRequest>()
            val message = buildOperationMessage(req)
            val sigBytes = signWithBackendKey(message)

            println("DEBUG response:")
            println("  message base64 length: ${Base64.getEncoder().encodeToString(message).length}")
            println("  signature base64 length: ${Base64.getEncoder().encodeToString(sigBytes).length}")

            call.respond(
                SignSensitiveOperationResponse(
                    message = Base64.getEncoder().encodeToString(message),
                    signature = Base64.getEncoder().encodeToString(sigBytes)
                )
            )
        }
    }
}


fun buildOperationMessage(req: SignSensitiveOperationRequest): ByteArray {
    val role: Byte = 0x01
    val operationBytes = req.operation.toByteArray(Charsets.UTF_8)
    val dataBytes = req.data.toByteArray(Charsets.UTF_8)
    val message = operationBytes + dataBytes + byteArrayOf(role)

    println("=== BACKEND BUILD MESSAGE ===")
    println("Message size: ${message.size}")
    println("Last 5 bytes: ${message.takeLast(5).joinToString(" ") { "%02X".format(it) }}")
    println("Last byte should be 01: ${String.format("%02X", message.last())}")

    return message
}

fun signWithBackendKey(message: ByteArray): ByteArray {
    val privateKey = KeyUtil.getPrivateKey()
    val signature = Signature.getInstance("SHA1withRSA")
    signature.initSign(privateKey)
    signature.update(message)
    return signature.sign()
}
