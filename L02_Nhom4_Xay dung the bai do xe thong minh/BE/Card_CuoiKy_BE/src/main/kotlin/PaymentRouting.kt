package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import kotlinx.serialization.Serializable

@Serializable
data class VerifyPaymentRequest(
    val cardId: String,
    val amount: Long,
    val newBalance: Long,
    val message: String,
    val signature: String
)

@Serializable
data class VerifyPaymentResponse(
    val success: Boolean,
    val message: String,
    //val backendSignature: String
)

fun Route.paymentRoutes() {
    route("/verify-payment") {
        post {
            try {
                val req = call.receive<VerifyPaymentRequest>()

                println("=== VERIFY PAYMENT REQUEST ===")
                println("Card ID: ${req.cardId}")
                println("Amount: ${req.amount}")
                println("New Balance: ${req.newBalance}")

                val cardIdInt = req.cardId.toIntOrNull()
                if (cardIdInt == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid card ID"))
                    return@post
                }

                val card = CardDAO.findById(cardIdInt)
                if (card == null) {
                    // SỬA: Trả về VerifyPaymentResponse với success = false
                    call.respond(HttpStatusCode.NotFound, VerifyPaymentResponse(false, "Card not found"))
                    return@post
                }

                if (card.publicKey == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Card not initialized"))
                    return@post
                }

                val messageBytes = Base64.getDecoder().decode(req.message)
                val signatureBytes = Base64.getDecoder().decode(req.signature)

                println("Message: ${String(messageBytes, Charsets.UTF_8)}")

                println("=== BACKEND VERIFY DEBUG ===")
                println("Message bytes: ${messageBytes.joinToString(" ") { "%02X".format(it) }}")
                println("Message length: ${messageBytes.size}")
                val testHash = java.security.MessageDigest.getInstance("SHA-1")
                val computedHash = testHash.digest(messageBytes)
                println("Backend computed SHA-1: ${computedHash.joinToString("") { "%02X".format(it) }}")
                println("===========================")

                // Verify signature với public key từ database
                val isValid = verifyCardSignature(card.publicKey!!, messageBytes, signatureBytes)
                if (!isValid) {
                    // SỬA
                    call.respond(HttpStatusCode.Unauthorized, VerifyPaymentResponse(false, "Invalid signature"))
                    return@post
                }

                println("✓ Signature verified")

                // Parse và validate message
                val messageStr = String(messageBytes, Charsets.UTF_8)
                val parts = messageStr.split(":")
                if (parts.size < 4 || parts[0] != "pay") {
                    // SỬA
                    call.respond(HttpStatusCode.BadRequest, VerifyPaymentResponse(false, "Invalid message format"))
                    return@post
                }

                if (parts[1] != req.cardId || parts[2].toLongOrNull() != req.amount) {
                    // SỬA
                    call.respond(HttpStatusCode.BadRequest, VerifyPaymentResponse(false, "Message mismatch"))
                    return@post
                }

                println("=== PAYMENT AUTHORIZED (USER MODE) ===")
                println("New balance: ${req.newBalance}")

                // Trả về success - KHÔNG CÒN backend signature
                call.respond(VerifyPaymentResponse(
                    success = true,
                    message = "Payment verified successfully"
                ))

            } catch (e: Exception) {
                println("Payment error: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, VerifyPaymentResponse(false, e.message ?: "Unknown error"))
            }
        }
    }
}

/**
 * Xác thực chữ ký RSA từ thẻ
 * @param publicKeyStr: Public key dạng hex string từ thẻ
 * @param message: Dữ liệu gốc đã được ký
 * @param signature: Chữ ký RSA (128 bytes cho RSA-1024)
 * @return true nếu chữ ký hợp lệ
 */
private fun verifyCardSignature(publicKeyStr: String, message: ByteArray, signature: ByteArray): Boolean {
    return try {
        println("=== SIGNATURE VERIFICATION DEBUG ===")
        println("Message: ${String(message, Charsets.UTF_8)}")
        println("Signature length: ${signature.size} bytes")

        // ===== BƯỚC 1: PARSE PUBLIC KEY TỪ HEX STRING =====
        // Chuyển hex string thành byte array
        val keyBytes = publicKeyStr.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        var pos = 0

        // Đọc Exponent Length (2 bytes)
        val expLen = ((keyBytes[pos].toInt() and 0xFF) shl 8) or (keyBytes[pos + 1].toInt() and 0xFF)
        pos += 2

        // Đọc Exponent (thường là 65537 = 0x010001)
        val expBytes = keyBytes.copyOfRange(pos, pos + expLen)
        pos += expLen

        // Đọc Modulus Length (2 bytes)
        val modLen = ((keyBytes[pos].toInt() and 0xFF) shl 8) or (keyBytes[pos + 1].toInt() and 0xFF)
        pos += 2

        // Đọc Modulus (128 bytes cho RSA-1024)
        val modBytes = keyBytes.copyOfRange(pos, pos + modLen)

        // Tạo RSA Public Key object từ Modulus và Exponent
        val keyFactory = KeyFactory.getInstance("RSA")
        val pubKeySpec = RSAPublicKeySpec(
            BigInteger(1, modBytes),    // Modulus
            BigInteger(1, expBytes)     // Exponent
        )
        val publicKey = keyFactory.generatePublic(pubKeySpec) as java.security.interfaces.RSAPublicKey

        // ===== BƯỚC 2: GIẢI MÃ CHỮ KÝ BẰNG PUBLIC KEY =====
        // Chữ ký RSA thực chất là: signature = message_hash^private_key mod N
        // Để verify, ta làm ngược lại: decrypted = signature^public_key mod N
        val sigBigInt = BigInteger(1, signature)
        val decrypted = sigBigInt.modPow(publicKey.publicExponent, publicKey.modulus)
        val decryptedBytes = decrypted.toByteArray()

        println("Decrypted signature length: ${decryptedBytes.size}")
        println("Decrypted hex: ${decryptedBytes.joinToString("") { "%02X".format(it) }}")

        // ===== BƯỚC 3: PARSE PKCS#1 V1.5 SIGNATURE FORMAT =====
        // Cấu trúc PKCS#1 v1.5: 0x00 0x01 [0xFF padding] 0x00 [DigestInfo]
        // HOẶC:                  0x01 [0xFF padding] 0x00 [DigestInfo] (nếu BigInteger bỏ leading 0x00)

        var idx = 0

        // Bỏ qua byte 0x00 đầu tiên nếu có (do BigInteger thêm vào cho số dương)
        if (idx < decryptedBytes.size && decryptedBytes[idx] == 0x00.toByte()) {
            idx++
        }

        // Kiểm tra Block Type = 0x01 (dùng cho chữ ký)
        if (idx >= decryptedBytes.size || decryptedBytes[idx] != 0x01.toByte()) {
            println("ERROR: Invalid PKCS#1 block type. Expected 0x01, got 0x${String.format("%02X", decryptedBytes[idx])}")
            return false
        }
        idx++

        // Bỏ qua các byte padding 0xFF (tối thiểu 8 bytes theo chuẩn PKCS#1)
        var paddingCount = 0
        while (idx < decryptedBytes.size && decryptedBytes[idx] == 0xFF.toByte()) {
            idx++
            paddingCount++
        }
        println("Padding 0xFF count: $paddingCount")

        // Kiểm tra byte phân cách 0x00 sau padding
        if (idx >= decryptedBytes.size || decryptedBytes[idx] != 0x00.toByte()) {
            println("ERROR: Invalid PKCS#1 separator. Expected 0x00, got 0x${String.format("%02X", decryptedBytes[idx])}")
            return false
        }
        idx++

        // ===== BƯỚC 4: TRÍCH XUẤT DIGESTINFO =====
        // DigestInfo chứa: [Algorithm Identifier][Hash Value]
        val digestInfo = decryptedBytes.copyOfRange(idx, decryptedBytes.size)
        println("DigestInfo length: ${digestInfo.size}")
        println("DigestInfo hex: ${digestInfo.joinToString("") { "%02X".format(it) }}")

        // Trích xuất hash từ DigestInfo (loại bỏ phần Algorithm Identifier)
        val hashBytes = extractHashFromDigestInfo(digestInfo)
        if (hashBytes == null) {
            println("ERROR: Failed to extract hash from DigestInfo")
            return false
        }

        println("Extracted hash (${hashBytes.size} bytes): ${hashBytes.joinToString("") { "%02X".format(it) }}")

        // ===== BƯỚC 5: TÍNH HASH CỦA MESSAGE VÀ SO SÁNH =====
        // Tính SHA-1 hash của message gốc
        val messageDigest = java.security.MessageDigest.getInstance("SHA-1")
        val expectedHash = messageDigest.digest(message)

        println("Expected hash (${expectedHash.size} bytes): ${expectedHash.joinToString("") { "%02X".format(it) }}")

        // So sánh hash từ chữ ký với hash tính được từ message
        // Nếu trùng khớp => Chữ ký hợp lệ
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

/**
 * Trích xuất hash value từ DigestInfo (cấu trúc ASN.1/DER)
 *
 * DigestInfo là cấu trúc chuẩn trong PKCS#1 chứa:
 * - Algorithm Identifier (thuật toán hash đã dùng, VD: SHA-1)
 * - Hash Value (kết quả hash của message)
 *
 * @param digestInfo: Byte array chứa DigestInfo đã parse từ chữ ký
 * @return Hash value (20 bytes cho SHA-1) hoặc null nếu parse lỗi
 */
private fun extractHashFromDigestInfo(digestInfo: ByteArray): ByteArray? {
    return try {
        // ===== CẤU TRÚC DIGESTINFO CHO SHA-1 (ASN.1/DER FORMAT) =====
        //
        // 30 21                         <- SEQUENCE tag (0x30), length 33 bytes (0x21)
        //   30 09                       <- SEQUENCE tag, length 9 bytes (AlgorithmIdentifier)
        //     06 05 2B 0E 03 02 1A      <- OID tag (0x06), SHA-1 OID = 1.3.14.3.2.26
        //     05 00                     <- NULL tag (0x05), length 0
        //   04 14                       <- OCTET STRING tag (0x04), length 20 bytes (0x14)
        //     [20 bytes of SHA-1 hash]  <- Hash value cần lấy
        //
        // Mục tiêu: Bỏ qua phần AlgorithmIdentifier, chỉ lấy 20 bytes hash cuối cùng

        println("Parsing DigestInfo...")
        var idx = 0

        // ===== BƯỚC 1: KIỂM TRA SEQUENCE TAG NGOÀI CÙNG =====
        // Kiểm tra byte đầu tiên phải là 0x30 (SEQUENCE tag trong ASN.1)
        if (idx >= digestInfo.size || digestInfo[idx] != 0x30.toByte()) {
            println("ERROR: Expected SEQUENCE tag (0x30), got 0x${String.format("%02X", digestInfo[idx])}")
            return null
        }
        idx++

        // ===== BƯỚC 2: ĐỌC LENGTH CỦA SEQUENCE =====
        // Đọc độ dài của toàn bộ DigestInfo (33 bytes cho SHA-1)
        if (idx >= digestInfo.size) return null
        var len = digestInfo[idx].toInt() and 0xFF
        idx++

        // Xử lý trường hợp long form length (nếu bit 7 = 1)
        // VD: 0x81 0x21 nghĩa là độ dài được mã hóa trong 1 byte tiếp theo
        if (len > 0x80) {
            val numLenBytes = len - 0x80
            idx += numLenBytes // Bỏ qua các byte length
        }

        // ===== BƯỚC 3: BỎ QUA ALGORITHMIDENTIFIER SEQUENCE =====
        // AlgorithmIdentifier chứa OID của SHA-1 và NULL parameter
        // Ta không cần phần này, chỉ cần hash value ở sau

        // Kiểm tra SEQUENCE tag của AlgorithmIdentifier
        if (idx >= digestInfo.size || digestInfo[idx] != 0x30.toByte()) {
            println("ERROR: Expected AlgorithmIdentifier SEQUENCE")
            return null
        }
        idx++

        // Đọc length của AlgorithmIdentifier (9 bytes cho SHA-1)
        if (idx >= digestInfo.size) return null
        len = digestInfo[idx].toInt() and 0xFF
        idx++

        // Bỏ qua toàn bộ nội dung AlgorithmIdentifier
        // (Bao gồm OID và NULL parameter)
        idx += len

        // ===== BƯỚC 4: TRÍCH XUẤT HASH VALUE =====
        // Bây giờ con trỏ đang ở OCTET STRING chứa hash value

        // Kiểm tra OCTET STRING tag (0x04)
        if (idx >= digestInfo.size || digestInfo[idx] != 0x04.toByte()) {
            println("ERROR: Expected OCTET STRING tag (0x04), got 0x${String.format("%02X", digestInfo[idx])}")
            return null
        }
        idx++

        // Đọc độ dài hash (20 bytes cho SHA-1, 32 cho SHA-256, ...)
        if (idx >= digestInfo.size) return null
        val hashLen = digestInfo[idx].toInt() and 0xFF
        idx++

        println("Hash length in DigestInfo: $hashLen")

        // Kiểm tra còn đủ byte để đọc hash không
        if (idx + hashLen > digestInfo.size) {
            println("ERROR: Not enough bytes for hash")
            return null
        }

        // ===== BƯỚC 5: TRẢ VỀ HASH VALUE =====
        // Copy 20 bytes hash từ vị trí hiện tại
        digestInfo.copyOfRange(idx, idx + hashLen)

    } catch (e: Exception) {
        println("Error extracting hash: ${e.message}")
        e.printStackTrace()
        null
    }
}
//--------------
@Serializable
data class VerifyUpdatePinRequest(
    val cardId: String,
    val newPin: String,
    val message: String,
    val signature: String
)

@Serializable
data class VerifyUpdatePinResponse(
    val success: Boolean,
    val message: String,
    //val backendSignature: String
)
fun Route.updatePinRoutes() {
    route("/verify-update-pin") {
        post {
            try {
                val req = call.receive<VerifyUpdatePinRequest>()

                println("=== VERIFY UPDATE PIN REQUEST (USER MODE) ===")
                println("Card ID: ${req.cardId}")
                println("New PIN: ${req.newPin}")

                val cardIdInt = req.cardId.toIntOrNull()
                if (cardIdInt == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid card ID"))
                    return@post
                }

                val card = CardDAO.findById(cardIdInt)
                if (card == null) {
                    // SỬA
                    call.respond(HttpStatusCode.NotFound, VerifyUpdatePinResponse(false, "Card not found"))
                    return@post
                }

                if (card.publicKey == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Card not initialized"))
                    return@post
                }

                val messageBytes = Base64.getDecoder().decode(req.message)
                val signatureBytes = Base64.getDecoder().decode(req.signature)

                println("Message: ${String(messageBytes, Charsets.UTF_8)}")

                // Verify signature với public key từ database
                val isValid = verifyCardSignature(card.publicKey!!, messageBytes, signatureBytes)
                if (!isValid) {
                    // SỬA
                    call.respond(HttpStatusCode.Unauthorized, VerifyUpdatePinResponse(false, "Invalid signature"))
                    return@post
                }

                println("✓ Signature verified")

                // Parse và validate message
                val messageStr = String(messageBytes, Charsets.UTF_8)
                val parts = messageStr.split(":")
                if (parts.size < 4 || parts[0] != "update_pin") {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid message format"))
                    return@post
                }

                if (parts[1] != req.cardId || parts[2] != req.newPin) {
                    // SỬA
                    call.respond(HttpStatusCode.BadRequest, VerifyUpdatePinResponse(false, "Message mismatch"))
                    return@post
                }

                println("=== PIN UPDATE AUTHORIZED (USER MODE) ===")
                println("New PIN: ${req.newPin}")

                // Trả về success - KHÔNG CẦN backend signature
                call.respond(VerifyUpdatePinResponse(
                    success = true,
                    message = "PIN update verified successfully"
                ))

            } catch (e: Exception) {
                println("Update PIN error: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, VerifyUpdatePinResponse(false, e.message ?: "Unknown error"))
            }
        }
    }
}