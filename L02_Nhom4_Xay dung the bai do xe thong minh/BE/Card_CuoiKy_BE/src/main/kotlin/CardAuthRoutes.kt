package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import kotlinx.serialization.Serializable

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

fun Route.cardAuthRoutes() {
    route("/verify-card-auth") {
        post {
            try {
                val req = call.receive<VerifyCardAuthRequest>()

                println("=== VERIFY CARD AUTH REQUEST ===")
                println("Card ID: ${req.cardId}")

                val cardIdInt = req.cardId.toIntOrNull()
                if (cardIdInt == null) {
                    call.respond(HttpStatusCode.BadRequest, VerifyCardAuthResponse(false, "Invalid card ID"))
                    return@post
                }

                // Tìm thẻ trong database
                val card = CardDAO.findById(cardIdInt)
                if (card == null) {
                    call.respond(HttpStatusCode.NotFound, VerifyCardAuthResponse(false, "Card not found"))
                    return@post
                }

                if (card.publicKey == null) {
                    call.respond(HttpStatusCode.BadRequest, VerifyCardAuthResponse(false, "Card not initialized"))
                    return@post
                }

                // Decode message và signature từ Base64
                val messageBytes = Base64.getDecoder().decode(req.message)
                val signatureBytes = Base64.getDecoder().decode(req.signature)

                println("Message: ${String(messageBytes, Charsets.UTF_8)}")

                // Xác thực chữ ký RSA
                val isValid = verifyCardSignature(card.publicKey!!, messageBytes, signatureBytes)
                if (!isValid) {
                    call.respond(HttpStatusCode.Unauthorized, VerifyCardAuthResponse(false, "Invalid signature"))
                    return@post
                }

                println("✓ Card signature verified - Card is authentic")

                // Parse message để kiểm tra format
                val messageStr = String(messageBytes, Charsets.UTF_8)
                val parts = messageStr.split(":")

                // Format: "auth:cardId:timestamp"
                if (parts.size < 3 || parts[0] != "auth") {
                    call.respond(HttpStatusCode.BadRequest, VerifyCardAuthResponse(false, "Invalid message format"))
                    return@post
                }

                if (parts[1] != req.cardId) {
                    call.respond(HttpStatusCode.BadRequest, VerifyCardAuthResponse(false, "Card ID mismatch"))
                    return@post
                }

                println("=== CARD AUTHENTICATED SUCCESSFULLY ===")

                // Trả về success
                call.respond(VerifyCardAuthResponse(
                    success = true,
                    message = "Card authenticated successfully"
                ))

            } catch (e: Exception) {
                println("Card auth error: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, VerifyCardAuthResponse(false, e.message ?: "Unknown error"))
            }
        }
    }
}

/**
 * Xác thực chữ ký RSA từ thẻ (sử dụng lại hàm đã có)
 */
private fun verifyCardSignature(publicKeyStr: String, message: ByteArray, signature: ByteArray): Boolean {
    return try {
        println("=== SIGNATURE VERIFICATION DEBUG ===")
        println("Message: ${String(message, Charsets.UTF_8)}")
        println("Signature length: ${signature.size} bytes")

        val keyBytes = publicKeyStr.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        var pos = 0

        val expLen = ((keyBytes[pos].toInt() and 0xFF) shl 8) or (keyBytes[pos + 1].toInt() and 0xFF)
        pos += 2

        val expBytes = keyBytes.copyOfRange(pos, pos + expLen)
        pos += expLen

        val modLen = ((keyBytes[pos].toInt() and 0xFF) shl 8) or (keyBytes[pos + 1].toInt() and 0xFF)
        pos += 2

        val modBytes = keyBytes.copyOfRange(pos, pos + modLen)

        val keyFactory = KeyFactory.getInstance("RSA")
        val pubKeySpec = RSAPublicKeySpec(
            BigInteger(1, modBytes),
            BigInteger(1, expBytes)
        )
        val publicKey = keyFactory.generatePublic(pubKeySpec) as java.security.interfaces.RSAPublicKey

        val sigBigInt = BigInteger(1, signature)
        val decrypted = sigBigInt.modPow(publicKey.publicExponent, publicKey.modulus)
        val decryptedBytes = decrypted.toByteArray()

        println("Decrypted signature length: ${decryptedBytes.size}")

        var idx = 0

        if (idx < decryptedBytes.size && decryptedBytes[idx] == 0x00.toByte()) {
            idx++
        }

        if (idx >= decryptedBytes.size || decryptedBytes[idx] != 0x01.toByte()) {
            println("ERROR: Invalid PKCS#1 block type")
            return false
        }
        idx++

        var paddingCount = 0
        while (idx < decryptedBytes.size && decryptedBytes[idx] == 0xFF.toByte()) {
            idx++
            paddingCount++
        }
        println("Padding 0xFF count: $paddingCount")

        if (idx >= decryptedBytes.size || decryptedBytes[idx] != 0x00.toByte()) {
            println("ERROR: Invalid PKCS#1 separator")
            return false
        }
        idx++

        val digestInfo = decryptedBytes.copyOfRange(idx, decryptedBytes.size)
        println("DigestInfo length: ${digestInfo.size}")

        val hashBytes = extractHashFromDigestInfo(digestInfo)
        if (hashBytes == null) {
            println("ERROR: Failed to extract hash from DigestInfo")
            return false
        }

        println("Extracted hash (${hashBytes.size} bytes): ${hashBytes.joinToString("") { "%02X".format(it) }}")

        val messageDigest = java.security.MessageDigest.getInstance("SHA-1")
        val expectedHash = messageDigest.digest(message)

        println("Expected hash (${expectedHash.size} bytes): ${expectedHash.joinToString("") { "%02X".format(it) }}")

        val result = hashBytes.contentEquals(expectedHash)
        println("✓ Hash comparison result: $result")
        println("=====================================")

        result
    } catch (e: Exception) {
        println("=== SIGNATURE VERIFICATION FAILED ===")
        println("Error: ${e.message}")
        e.printStackTrace()
        println("=====================================")
        false
    }
}

private fun extractHashFromDigestInfo(digestInfo: ByteArray): ByteArray? {
    return try {
        var idx = 0

        if (idx >= digestInfo.size || digestInfo[idx] != 0x30.toByte()) {
            return null
        }
        idx++

        if (idx >= digestInfo.size) return null
        var len = digestInfo[idx].toInt() and 0xFF
        idx++

        if (len > 0x80) {
            val numLenBytes = len - 0x80
            idx += numLenBytes
        }

        if (idx >= digestInfo.size || digestInfo[idx] != 0x30.toByte()) {
            return null
        }
        idx++

        if (idx >= digestInfo.size) return null
        len = digestInfo[idx].toInt() and 0xFF
        idx++

        idx += len

        if (idx >= digestInfo.size || digestInfo[idx] != 0x04.toByte()) {
            return null
        }
        idx++

        if (idx >= digestInfo.size) return null
        val hashLen = digestInfo[idx].toInt() and 0xFF
        idx++

        if (idx + hashLen > digestInfo.size) {
            return null
        }

        digestInfo.copyOfRange(idx, idx + hashLen)

    } catch (e: Exception) {
        null
    }
}