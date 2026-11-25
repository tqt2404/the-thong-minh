package utils

import data.model.CardInfo
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import javax.smartcardio.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object CardManager {
    private val envProperties = Properties().apply {
        try {
            val envFile = File(".env")
            if (envFile.exists()) {
                envFile.inputStream().use { load(it) }
            } else {
                println("Warning: .env file not found, using default values")
            }
        } catch (e: Exception) {
            println("Error loading .env file: ${e.message}")
        }
    }

    // System Secret (giống Admin)
    private val SYSTEM_SECRET_STRING: String = envProperties.getProperty("SYSTEM_SECRET_STRING", "")

    // Khoa bao mat Pin (Giong het ben JavaCard) - Load from .env
    private val TRANSPORT_KEY_STRING: String = envProperties.getProperty("TRANSPORT_KEY_STRING", "")

    var activeCard: Card? = null
    var activeChannel: CardChannel? = null
    var watcherJob: Job? = null
    var onCardRemoved: (() -> Unit)? = null

    // Ham ho tro ma hoa 1 block du lieu thanh 16 bytes de gui xuong the
    // Cau truc block: [Byte dau tien: Do dai that][Data...][Padding 0...]
    private fun encryptTransportBlock(data: ByteArray): ByteArray {
        if (data.size > 15) throw IllegalArgumentException("Data too long for transport block")

        // Tao block 16 bytes
        val block = ByteArray(16)
        block[0] = data.size.toByte() // Byte dau luu do dai
        System.arraycopy(data, 0, block, 1, data.size) // Copy data vao sau do

        // Ma hoa AES-128 ECB NoPadding
        val keySpec = SecretKeySpec(TRANSPORT_KEY_STRING.toByteArray(Charsets.UTF_8), "AES")
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)

        return cipher.doFinal(block)
    }

    fun disconnect() {
        watcherJob?.cancel()
        watcherJob = null

        try {
            activeCard?.disconnect(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        activeCard = null
        activeChannel = null
        onCardRemoved?.invoke()
    }

    fun startCardWatcher(scope: CoroutineScope, terminal: CardTerminal) {
        watcherJob?.cancel()
        watcherJob = scope.launch(Dispatchers.IO) {
            while (true) {
                if (!terminal.isCardPresent) {
                    withContext(Dispatchers.Main) {
                        disconnect()
                    }
                    break
                }
                delay(500)
            }
        }
    }

    fun sendApdu(cmd: CommandAPDU): ResponseAPDU {
        val ch = activeChannel ?: throw IllegalStateException("No card connected!")
        return ch.transmit(cmd)
    }

    fun sendCommand(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray): ResponseAPDU {
        val cmd = CommandAPDU(cla, ins, p1, p2, data)
        return sendApdu(cmd)
    }

    private fun interpretSW(sw: Int): String {
        return when (sw) {
            0x9000 -> "Success"
            0x6982 -> "Security condition not met / Access denied"
            0x6984 -> "Invalid data (length/format) or security error"
            0x6A82 -> "File not found"
            0x63C0 -> "Pin retry counter exhausted"
            else -> "Unknown error"
        }
    }

    fun sendCreateCardCommand(cardDataBuffer: ByteArray): String {
        val cmd = CommandAPDU(0x00, 0x02, 0x00, 0x00, cardDataBuffer)
        val resp = sendApdu(cmd)

        if (resp.sw != 0x9000) {
            val swHex = String.format("%04X", resp.sw)
            val swMsg = interpretSW(resp.sw)
            throw Exception("Card returned error SW=$swHex ($swMsg)")
        }

        return resp.data.joinToString("") { "%02X".format(it) }
    }
    fun isCardEmpty(): Boolean {
        // Gửi lệnh INS_CHECK_EMPTY (0x27)
        val cmd = CommandAPDU(0x00, 0x27, 0x00, 0x00)
        val resp = sendApdu(cmd)

        if (resp.sw == 0x9000) {
            // Byte đầu tiên: 1 là Empty, 0 là Not Empty
            return resp.data[0] == 0x01.toByte()
        }
        return false // Mặc định coi như không empty để an toàn
    }
    fun verifySystemSecret(): Boolean {
        // Lấy chuỗi bí mật đã load từ .env
        val secretBytes = SYSTEM_SECRET_STRING.toByteArray(Charsets.UTF_8)

        //  Ma hoa secret truoc khi gui
        val encryptedSecret = encryptTransportBlock(secretBytes)

        // Giả sử bạn quy định INS = 0x0A là lệnh Verify Secret (Bạn phải khớp mã này với Applet)
        // Cấu trúc lệnh: [CLA, INS, P1, P2, DATA]
        val cmd = CommandAPDU(0x00, 0x0A, 0x00, 0x00, encryptedSecret)

        val resp = sendApdu(cmd)

        // Nếu thẻ trả về 0x9000 nghĩa là Secret đúng -> Đã đăng nhập quyền hệ thống
        return resp.sw == 0x9000
    }
    //----------------------------------------------------------------------------------------------------------------------
    fun createPinOnCard(pinBytes: ByteArray): Boolean {
        // Lấy bytes của Secret
        val secretBytes = SYSTEM_SECRET_STRING.toByteArray(Charsets.UTF_8)

        //  Ma hoa PIN va Secret truoc khi gui
        val encryptedPin = encryptTransportBlock(pinBytes)       // 16 bytes
        val encryptedSecret = encryptTransportBlock(secretBytes) // 16 bytes

        // Build APDU Data: [EncryptedPIN (16)] [EncryptedSecret (16)]
        val buffer = ByteArray(encryptedPin.size + encryptedSecret.size)
        System.arraycopy(encryptedPin, 0, buffer, 0, encryptedPin.size)
        System.arraycopy(encryptedSecret, 0, buffer, encryptedPin.size, encryptedSecret.size)

        // Gửi lệnh 0x01 (INS_CREATE_PIN)
        val cmd = CommandAPDU(0x00, 0x01, 0x00, 0x00, buffer)
        val resp = sendApdu(cmd)

        return if (resp.sw == 0x9000) {
            true
        } else {
            val swHex = String.format("%04X", resp.sw)
            throw Exception("Failed to create PIN. SW=$swHex")
        }
    }
    //------------------------------------------------------------------------------------------------------------------

    fun verifyPinOnCard(pinBytes: ByteArray): Boolean {
        println("DEBUG: Verifying PIN with bytes: ${pinBytes.joinToString(" ") { "%02X".format(it) }}")

        // Ma hoa PIN truoc khi gui
        val encryptedPin = encryptTransportBlock(pinBytes)

        val cmd = CommandAPDU(0x00, 0x00, 0x00, 0x00, encryptedPin)
        val resp = sendApdu(cmd)

        println("DEBUG: Verify response SW: ${String.format("%04X", resp.sw)}, Data: ${resp.data.joinToString(" ") { "%02X".format(it) }}")

        return if (resp.sw == 0x9000) {
            true
        } else {
            val swHex = String.format("%04X", resp.sw)
            val triesRemaining = if (resp.data.isNotEmpty()) resp.data[0].toInt() else -1
            throw Exception("Failed to verify PIN. SW=$swHex, Tries remaining: $triesRemaining")
        }
    }

    //--------------------------------------------------------------
    fun setBackendPublicKeyOnCard(backendPubKeyFormatted: String): Boolean {
        println("DEBUG: Setting backend public key: $backendPubKeyFormatted")

        val parts = backendPubKeyFormatted.split(":")
        if (parts.size != 4) {
            throw IllegalArgumentException("Invalid backend public key format")
        }

        val modLen = parts[0].toInt()
        val modulusHex = parts[1]
        val expLen = parts[2].toInt()
        val exponentHex = parts[3]

        var modulus = modulusHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        var exponent = exponentHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        if (modulus.isNotEmpty() && modulus[0] == 0x00.toByte()) {
            modulus = modulus.copyOfRange(1, modulus.size)
            println("DEBUG: Removed leading zero from modulus, new size: ${modulus.size}")
        }
        if (exponent.isNotEmpty() && exponent[0] == 0x00.toByte()) {
            exponent = exponent.copyOfRange(1, exponent.size)
            println("DEBUG: Removed leading zero from exponent, new size: ${exponent.size}")
        }

        // Format cho thẻ: [modLen_byte][modulus][expLen_byte][exponent]
        val keyBuffer = ByteArray(1 + modulus.size + 1 + exponent.size)
        var pos = 0

        keyBuffer[pos++] = modulus.size.toByte()
        System.arraycopy(modulus, 0, keyBuffer, pos, modulus.size)
        pos += modulus.size

        keyBuffer[pos++] = exponent.size.toByte()
        System.arraycopy(exponent, 0, keyBuffer, pos, exponent.size)

        println("DEBUG: Backend key buffer size: ${keyBuffer.size} bytes")
        println("DEBUG: Buffer hex: ${keyBuffer.joinToString(" ") { "%02X".format(it) }}")

        val cmd = CommandAPDU(0x00, 0x16, 0x00, 0x00, keyBuffer)
        val resp = sendApdu(cmd)

        if (resp.sw != 0x9000) {
            val swHex = String.format("%04X", resp.sw)
            throw Exception("Failed to set backend public key. SW=$swHex")
        }

        println("DEBUG: Backend public key set successfully")
        return true
    }

    fun isCardInfoExists(): Boolean {
        // Sử dụng INS_GET_INFO = 0x05 để kiểm tra xem có info không
        val cmd = CommandAPDU(0x00, 0x05, 0x00, 0x00)
        val resp = sendApdu(cmd)
        return resp.sw == 0x9000 // Thẻ có info/cardModel đã tồn tại
    }

    fun updateAvatarOnCard(avatarBytes: ByteArray): Boolean {
        val MAX_LEN = 240  // <=255 để trừ hao header APDU
        var offset = 0
        while (offset < avatarBytes.size) {
            val chunkSize = minOf(MAX_LEN, avatarBytes.size - offset)
            val chunk = avatarBytes.copyOfRange(offset, offset + chunkSize)
            // Gửi param offset vào P1, P2
            val cmd = javax.smartcardio.CommandAPDU(
                0x00, 0x10,
                (offset shr 8) and 0xFF,    // P1: high byte
                offset and 0xFF,            // P2: low byte
                chunk
            )
            val resp = sendApdu(cmd)
            if (resp.sw != 0x9000) {
                val swHex = String.format("%04X", resp.sw)
                throw Exception("Failed to write avatar at offset $offset. SW=$swHex")
            }
            offset += chunkSize
        }
        return true
    }

    // ======================================================================
    // THÊM CHỨC NĂNG ĐỌC/CẬP NHẬT INFO CHỦ THẺ THEO APPLET PARKCARD
    // ======================================================================

    /**
     * Đọc thông tin chủ thẻ (dạng CardInfo) theo contract applet ParkCard
     */
    fun readCardInfo(): CardInfo {
        val card = activeCard ?: throw Exception("No card connected!")
        val ch = activeChannel ?: throw Exception("No card channel!")
        val apdu = CommandAPDU(0x00, 0x05, 0x00, 0x00)
        val resp = ch.transmit(apdu)
        if (resp.sw != 0x9000) throw Exception("Lỗi đọc thẻ. SW: ${String.format("%04X", resp.sw)}")
        val buf = resp.data
        var offset = 0
        fun field(): String {
            val len = buf[offset].toInt()
            val v = buf.copyOfRange(offset + 1, offset + 1 + len).toString(Charsets.UTF_8)
            offset += 1 + len
            return v
        }
        val idCard = field()
        val fullName = field()
        val dob = field()
        val address = field()
        val phone = field()
        val expired = field()
        val pin = field()
        return CardInfo(idCard, fullName, dob, address, phone, pin, ByteArray(0))
    }

    //------------------------------------------------------------------------------------------------------------------
    fun buildUpdateInfoBuffer(
        msgBytes: ByteArray,
        sigBytes: ByteArray,
        fullName: String,
        dateOfBirth: String,
        address: String,
        phoneNumber: String
    ): ByteArray {
        // Encode strings first
        val fnBytes = fullName.encodeToByteArray()
        val dobBytes = dateOfBirth.encodeToByteArray()
        val addrBytes = address.encodeToByteArray()
        val phoneBytes = phoneNumber.encodeToByteArray()

        val totalSize = 2 + msgBytes.size + 2 + sigBytes.size +
                1 + fnBytes.size + 1 + dobBytes.size +
                1 + addrBytes.size + 1 + phoneBytes.size

        val buf = ByteArray(totalSize)
        var pos = 0

        // msgLen
        buf[pos++] = (msgBytes.size shr 8).toByte()
        buf[pos++] = msgBytes.size.toByte()
        // msg
        msgBytes.copyInto(buf, pos)
        pos += msgBytes.size

        // sigLen
        buf[pos++] = (sigBytes.size shr 8).toByte()
        buf[pos++] = sigBytes.size.toByte()
        // sig
        sigBytes.copyInto(buf, pos)
        pos += sigBytes.size

        // fullName
        buf[pos++] = fnBytes.size.toByte()
        fnBytes.copyInto(buf, pos)
        pos += fnBytes.size

        // dateOfBirth
        buf[pos++] = dobBytes.size.toByte()
        dobBytes.copyInto(buf, pos)
        pos += dobBytes.size

        // address
        buf[pos++] = addrBytes.size.toByte()
        addrBytes.copyInto(buf, pos)
        pos += addrBytes.size

        // phoneNumber
        buf[pos++] = phoneBytes.size.toByte()
        phoneBytes.copyInto(buf, pos)
        pos += phoneBytes.size

        println("=== BUFFER CHECK ===")
        println("Buffer[0-1] msgLen: ${String.format("%02X %02X", buf[0], buf[1])}")
        val sigLenPos = 2 + msgBytes.size
        println("Buffer[$sigLenPos-${sigLenPos+1}] sigLen: ${String.format("%02X %02X", buf[sigLenPos], buf[sigLenPos+1])}")
        val fnLenPos = sigLenPos + 2 + sigBytes.size
        println("Buffer[$fnLenPos] fnLen: ${String.format("%02X", buf[fnLenPos])}")

        return buf
    }

    //----------------------------------------------------------------------------------
    fun updateCardInfoToCard(buffer: ByteArray): Boolean {
        val ch = activeChannel ?: throw Exception("No card channel!")

        // Gửi toàn bộ buffer 1 lần
        val cmd = CommandAPDU(0x00, 0x04, 0x00, 0x00, buffer)
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Update failed. SW=${String.format("%04X", resp.sw)}")
        }
        return true
    }
    //----------------------------------------------------------------------------------
    fun updatePinOnCard(
        messageBytes: ByteArray,
        sigBytes: ByteArray,
        newPin: String
    ): Boolean {
        val ch = activeChannel ?: throw Exception("No card channel!")

        val pinBytes = newPin.toByteArray(Charsets.UTF_8)

        // DA SUA: Ma hoa PIN truoc khi gui
        // encryptedPin la 16 bytes
        val encryptedPin = encryptTransportBlock(pinBytes)

        // Buffer: [msgLen(2)][msg][sigLen(2)][sig][EncryptedPIN (16 bytes)]
        // Luu y: Khong can byte length cho PIN nua vi block encrypted co dinh 16 bytes
        val buffer = ByteArray(2 + messageBytes.size + 2 + sigBytes.size + encryptedPin.size)
        var pos = 0

        buffer[pos++] = (messageBytes.size shr 8).toByte()
        buffer[pos++] = messageBytes.size.toByte()
        messageBytes.copyInto(buffer, pos)
        pos += messageBytes.size

        buffer[pos++] = (sigBytes.size shr 8).toByte()
        buffer[pos++] = sigBytes.size.toByte()
        sigBytes.copyInto(buffer, pos)
        pos += sigBytes.size

        // Copy khoi ma hoa vao buffer
        encryptedPin.copyInto(buffer, pos)

        // DEBUG: In ra để check
        println("=== UPDATE PIN DEBUG ===")
        println("Message size: ${messageBytes.size}")
        println("Sig size: ${sigBytes.size}")
        println("Encrypted Pin size: ${encryptedPin.size}")
        println("Total buffer size: ${buffer.size}")

        val cmd = CommandAPDU(0x00, 0x03, 0x00, 0x00, buffer)
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Update PIN failed. SW=${String.format("%04X", resp.sw)}")
        }
        return true
    }
    //--------------------------------------------------------------------------------------------------
    fun getMoneyFromCard(): Long {
        val ch = activeChannel ?: throw Exception("No card channel!")

        val cmd = CommandAPDU(0x00, 0x07, 0x00, 0x00) // INS_GET_MONEY
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Get money failed. SW=${String.format("%04X", resp.sw)}")
        }

        val moneyStr = String(resp.data, Charsets.UTF_8).trim()
        return moneyStr.toLongOrNull() ?: 0L
    }
    fun updateMoneyOnCard(
        messageBytes: ByteArray,
        sigBytes: ByteArray,
        newMoney: String
    ): Boolean {
        val ch = activeChannel ?: throw Exception("No card channel!")

        val moneyBytes = newMoney.toByteArray(Charsets.UTF_8)

        // Buffer: [msgLen(2)][msg][sigLen(2)][sig][money]
        val buffer = ByteArray(2 + messageBytes.size + 2 + sigBytes.size + moneyBytes.size)
        var pos = 0

        buffer[pos++] = (messageBytes.size shr 8).toByte()
        buffer[pos++] = messageBytes.size.toByte()
        messageBytes.copyInto(buffer, pos)
        pos += messageBytes.size

        buffer[pos++] = (sigBytes.size shr 8).toByte()
        buffer[pos++] = sigBytes.size.toByte()
        sigBytes.copyInto(buffer, pos)
        pos += sigBytes.size

        moneyBytes.copyInto(buffer, pos)

        val cmd = CommandAPDU(0x00, 0x06, 0x00, 0x00, buffer) // INS_UPDATE_MONEY
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Update money failed. SW=${String.format("%04X", resp.sw)}")
        }
        return true
    }
    //------------------------------------------------------------------------------------------------------------------
    fun updateExpiredOnCard(
        messageBytes: ByteArray,
        sigBytes: ByteArray,
        newExpired: String
    ): Boolean {
        val ch = activeChannel ?: throw Exception("No card channel!")

        val expiredBytes = newExpired.toByteArray(Charsets.UTF_8)

        // Buffer: [msgLen(2)][msg][sigLen(2)][sig][expired]
        val buffer = ByteArray(2 + messageBytes.size + 2 + sigBytes.size + expiredBytes.size)
        var pos = 0

        buffer[pos++] = (messageBytes.size shr 8).toByte()
        buffer[pos++] = messageBytes.size.toByte()
        messageBytes.copyInto(buffer, pos)
        pos += messageBytes.size

        buffer[pos++] = (sigBytes.size shr 8).toByte()
        buffer[pos++] = sigBytes.size.toByte()
        sigBytes.copyInto(buffer, pos)
        pos += sigBytes.size

        expiredBytes.copyInto(buffer, pos)

        val cmd = CommandAPDU(0x00, 0x08, 0x00, 0x00, buffer) // INS_UPDATE_EXPIRED
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Update expired failed. SW=${String.format("%04X", resp.sw)}")
        }
        return true
    }
    //------------------------------------------------------------------------------------------------------------------
    fun resetPinOnCard(messageBytes: ByteArray, sigBytes: ByteArray): Boolean {
        val ch = activeChannel ?: throw Exception("No card channel!")

        // Build buffer: [msgLen(2)][message][sigLen(2)][signature]
        val buffer = ByteArray(2 + messageBytes.size + 2 + sigBytes.size)
        var offset = 0

        // Message length (2 bytes)
        buffer[offset++] = ((messageBytes.size shr 8) and 0xFF).toByte()
        buffer[offset++] = (messageBytes.size and 0xFF).toByte()

        // Message
        System.arraycopy(messageBytes, 0, buffer, offset, messageBytes.size)
        offset += messageBytes.size

        // Signature length (2 bytes)
        buffer[offset++] = ((sigBytes.size shr 8) and 0xFF).toByte()
        buffer[offset++] = (sigBytes.size and 0xFF).toByte()

        // Signature
        System.arraycopy(sigBytes, 0, buffer, offset, sigBytes.size)

        val cmd = CommandAPDU(0x00, 0x09, 0x00, 0x00, buffer) // INS_RESET_PIN
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Reset PIN failed. SW=${String.format("%04X", resp.sw)}")
        }
        return true
    }
}