package utils

import java.io.File
import java.util.*
import javax.smartcardio.Card
import javax.smartcardio.CardChannel
import javax.smartcardio.CardTerminal
import javax.smartcardio.CommandAPDU
import javax.smartcardio.TerminalFactory
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object CardManager {
    var activeCard: Card? = null
    var activeChannel: CardChannel? = null

    private val AID = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x00)

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

    fun getAvailableTerminals(): List<CardTerminal> {
        val factory = TerminalFactory.getDefault()
        val terminals = factory.terminals().list()
        return terminals.toList()
    }

    fun connect(): Boolean {
        return try {
            val terminals = getAvailableTerminals()
            if (terminals.isEmpty()) return false

            val validTerminals = terminals.filter { !it.name.contains("Windows Hello", ignoreCase = true) }
            if (validTerminals.isEmpty()) return false
            val terminal = validTerminals.firstOrNull { it.isCardPresent } ?: return false
            activeCard = terminal.connect("*")
            activeChannel = activeCard?.basicChannel

            // Select applet with AID
            val selectCmd = CommandAPDU(0x00, 0xA4, 0x04, 0x00, AID)
            val response = activeChannel?.transmit(selectCmd)

            response?.sw == 0x9000
        } catch (e: Exception) {
            false
        }
    }

    fun disconnect() {
        activeCard?.disconnect(false)
        activeCard = null
        activeChannel = null
    }

    fun isConnected(): Boolean = activeCard != null

    //------------------------------------------------------------------------------------------------------------------
    // THÊM: Kiểm tra thẻ trắng (chưa có dữ liệu)
    //------------------------------------------------------------------------------------------------------------------
    fun isCardEmpty(): Boolean {
        val ch = activeChannel ?: throw Exception("No card channel!")

        // Gửi lệnh INS_CHECK_EMPTY (0x27)
        val cmd = CommandAPDU(0x00, 0x27, 0x00, 0x00)
        val resp = ch.transmit(cmd)

        if (resp.sw == 0x9000) {
            // Byte đầu tiên: 0x01 = Empty, 0x00 = Not Empty
            return resp.data[0] == 0x01.toByte()
        }

        // Nếu lỗi thì mặc định coi như không empty để an toàn
        return false
    }

    //------------------------------------------------------------------------------------------------------------------
    // THÊM: Xác thực System Secret (giống Admin)
    //------------------------------------------------------------------------------------------------------------------
    fun verifySystemSecret(): Boolean {
        val ch = activeChannel ?: throw Exception("No card channel!")

        // Lấy System Secret string
        val secretBytes = SYSTEM_SECRET_STRING.toByteArray(Charsets.UTF_8)

        // Mã hóa secret trước khi gửi
        val encryptedSecret = encryptTransportBlock(secretBytes)

        // Gửi lệnh INS_VERIFY_SECRET (0x0A)
        val cmd = CommandAPDU(0x00, 0x0A, 0x00, 0x00, encryptedSecret)
        val resp = ch.transmit(cmd)

        // 0x9000 = Secret đúng → Đã unlock MasterKey
        return resp.sw == 0x9000
    }

    //------------------------------------------------------------------------------------------------------------------
    /**
     * Verify PIN with card
     * DA SUA: Ma hoa PIN truoc khi gui xuong the
     * @return Pair<Boolean, Int?> - (success, triesRemaining)
     */
    fun verifyPIN(pin: String): Pair<Boolean, Int?> {
        val ch = activeChannel ?: throw Exception("No card channel!")

        val pinBytes = pin.toByteArray(Charsets.UTF_8)

        // MA HOA PIN
        val encryptedPin = encryptTransportBlock(pinBytes)

        // Gui 16 bytes da ma hoa
        val cmd = CommandAPDU(0x00, 0x00, 0x00, 0x00, encryptedPin)
        val resp = ch.transmit(cmd)

        return when (resp.sw) {
            0x9000 -> {
                Pair(true, null)
            }

            0x6A80 -> {
                val triesRemaining = if (resp.data != null && resp.data.isNotEmpty()) {
                    resp.data[0].toInt() and 0xFF  // 0x04 -> 4
                } else {
                    null
                }
                Pair(false, triesRemaining)
            }

            0x6983 -> {
                Pair(false, 0)
            }

            else -> {
                throw Exception("Verify PIN failed. SW=${String.format("%04X", resp.sw)}")
            }
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    // ThÃªm vÃ o CardManager náº¿u muá»'n tÃ¡i sá»­ dá»¥ng
    fun getCardInfo(): Map<String, String>? {
        val ch = activeChannel ?: return null

        try {
            val cmd = CommandAPDU(0x00, 0x05, 0x00, 0x00) // INS_GET_INFO
            val resp = ch.transmit(cmd)

            if (resp.sw != 0x9000 || resp.data.all { it == 0.toByte() }) {
                return null
            }

            val fields = mutableListOf<String>()
            var pos = 0
            while (pos < resp.data.size) {
                val len = resp.data[pos].toInt() and 0xFF
                if (len == 0 || pos + 1 + len > resp.data.size) break
                val value = resp.data.copyOfRange(pos + 1, pos + 1 + len)
                    .toString(Charsets.UTF_8)
                    .trim()
                fields.add(value)
                pos += 1 + len
            }

            val keys = listOf("idCard", "fullName", "dob", "address", "phone", "balance", "expired")
            return keys.zip(fields).toMap()
        } catch (e: Exception) {
            return null
        }
    }
    //------------------
    fun getCardId(): String {
        val ch = activeChannel ?: throw Exception("No card channel!")
        val cmd = CommandAPDU(0x00, 0x17, 0x00, 0x00)
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Get card ID failed.")
        }

        val rawString = String(resp.data, Charsets.UTF_8)
        return rawString.replace(Regex("[^a-zA-Z0-9]"), "")
    }

    fun getDatabaseId(): String {
        val ch = activeChannel ?: throw Exception("No card channel!")
        val cmd = CommandAPDU(0x00, 0x18, 0x00, 0x00) // INS_GET_DB_ID
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Get database ID failed. SW=${String.format("%04X", resp.sw)}")
        }

        val rawString = String(resp.data, Charsets.UTF_8)
        return rawString.replace(Regex("[^a-zA-Z0-9]"), "")
    }

    // NEW: Get money from card
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

    // NEW: Sign data with card private key
    fun signDataWithCard(data: ByteArray): ByteArray {
        val ch = activeChannel ?: throw Exception("No card channel!")

        println("=== CARD SIGN REQUEST ===")
        println("Data to card: '${String(data, Charsets.UTF_8)}'")
        val testHash = java.security.MessageDigest.getInstance("SHA-1")
        val expectedHash = testHash.digest(data)
        println("Expected SHA-1: ${expectedHash.joinToString("") { "%02X".format(it) }}")

        val cmd = CommandAPDU(0x00, 0x12, 0x00, 0x00, data) // INS_CREATE_SIGNATURE
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Sign failed. SW=${String.format("%04X", resp.sw)}")
        }

        return resp.data // 128 bytes RSA-1024 signature
    }

    // NEW: Update money on card (with backend signature)
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

    // Cáº­p nháº­t tiá»n trá»±c tiáº¿p cho user (khÃ´ng cáº§n backend signature)
    fun updateMoneyOnCardDirect(newMoney: String): Boolean {
        val ch = activeChannel ?: throw Exception("No card channel!")

        // Tien nong thi gui ro truc tiep luon cung duoc, vi da duoc bao ve boi viec Verify PIN thanh cong o buoc truoc
        // (Chi khi verify PIN dung thi MasterKey moi mo)
        val moneyBytes = newMoney.toByteArray(Charsets.UTF_8)

        val cmd = CommandAPDU(0x00, 0x26, 0x00, 0x00, moneyBytes) // INS_UPDATE_MONEY_DIRECT
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Update money failed. SW=${String.format("%04X", resp.sw)}")
        }
        return true
    }

    //------------------------------------------------------------------------------------------------------------------
    /**
     * Update PIN directly (User changing their own PIN)
     * DA SUA: Ma hoa PIN moi truoc khi gui
     */
    fun updatePinOnCardDirect(newPin: String): Boolean {
        val ch = activeChannel ?: throw Exception("No card channel!")

        println("=== UPDATE PIN DIRECT (USER MODE) ===")
        println("New PIN: $newPin")

        val pinBytes = newPin.toByteArray(Charsets.UTF_8)

        // MA HOA PIN
        val encryptedPin = encryptTransportBlock(pinBytes)

        // Sá»­ dá»¥ng INS_UPDATE_PIN_DIRECT (0x25)
        // Gui 16 bytes da ma hoa
        val cmd = CommandAPDU(0x00, 0x25, 0x00, 0x00, encryptedPin)
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            // Check loi PIN giong cu
            if (resp.sw == 0x6A80) {
                throw Exception("PIN má»›i khÃ´ng Ä'Æ°á»£c trÃ¹ng vá»›i PIN cÅ©!")
            }
            throw Exception("Update PIN direct failed. SW=${String.format("%04X", resp.sw)}")
        }

        println("✓ PIN updated successfully (direct)")
        return true
    }

    fun updateExpiredOnCard(expiryDate: String) {
        val ch = activeChannel ?: throw Exception("No card channel!")

        try {
            val expiryBytes = expiryDate.toByteArray(Charsets.UTF_8)
            val apdu = CommandAPDU(0x00, 0x08, 0x00, 0x00, expiryBytes)
            val response = ch.transmit(apdu)

            if (response.sw != 0x9000) {
                throw Exception("Update expired failed with SW: ${String.format("%04X", response.sw)}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    fun getEntryState(): Pair<Boolean, Long> {
        val ch = activeChannel ?: throw Exception("No card channel!")

        val cmd = CommandAPDU(0x00, 0x22, 0x00, 0x00) // INS_GET_ENTRY_STATE
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Get entry state failed. SW=${String.format("%04X", resp.sw)}")
        }

        val isInside = resp.data[0] == 0x01.toByte()

        // Parse entryTime (8 bytes, big-endian long)
        var entryTime = 0L
        for (i in 0 until 8) {
            entryTime = (entryTime shl 8) or (resp.data[i + 1].toLong() and 0xFF)
        }

        return Pair(isInside, entryTime)
    }

    //-------------------------------------------------------------------------------------------------------------------
    fun entryGate(timestamp: Long): Boolean {
        val ch = activeChannel ?: throw Exception("No card channel!")

        // Convert timestamp to 8 bytes (big-endian)
        val timeBytes = ByteArray(8)
        for (i in 0 until 8) {
            timeBytes[7 - i] = ((timestamp shr (i * 8)) and 0xFF).toByte()
        }

        val cmd = CommandAPDU(0x00, 0x20, 0x00, 0x00, timeBytes) // INS_ENTRY_GATE
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Entry gate failed. SW=${String.format("%04X", resp.sw)}")
        }

        // Response: 0x01 = success, 0x00 = already inside
        return resp.data[0] == 0x01.toByte()
    }

    //------------------------------------------------------------------------------------------------------------------
    fun exitGate(timestamp: Long): Pair<Int, Long> {
        val ch = activeChannel ?: throw Exception("No card channel!")

        val cmd = CommandAPDU(0x00, 0x21, 0x00, 0x00) // INS_EXIT_GATE (khÃ´ng cáº§n gá»­i timestamp ná»¯a)
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Exit gate failed. SW=${String.format("%04X", resp.sw)}")
        }

        val status = resp.data[0].toInt() and 0xFF

        if (status == 0x01) {
            // Success - parse entry timestamp from response
            var entryTimestamp = 0L
            for (i in 0 until 8) {
                entryTimestamp = (entryTimestamp shl 8) or (resp.data[i + 1].toLong() and 0xFF)
            }
            return Pair(status, entryTimestamp)
        } else {
            // Failed
            return Pair(status, 0L)
        }
    }
    //------------------

    fun getPinTriesRemaining(): Int {
        val ch = activeChannel ?: throw Exception("No card channel!")

        val cmd = CommandAPDU(0x00, 0x24, 0x00, 0x00) // INS_GET_PIN_TRIES
        val resp = ch.transmit(cmd)

        if (resp.sw != 0x9000) {
            throw Exception("Get PIN tries failed. SW=${String.format("%04X", resp.sw)}")
        }

        return resp.data[0].toInt() and 0xFF
    }

    fun isCardBlocked(): Boolean {
        return try {
            getPinTriesRemaining() == 0
        } catch (e: Exception) {
            println("Error checking card block status: ${e.message}")
            false
        }
    }
    //---------------------
    fun getCardExpiryDate(): String? {
        val ch = activeChannel ?: return null

        try {
            val cmd = CommandAPDU(0x00, 0x30, 0x00, 0x00)
            val resp = ch.transmit(cmd)

            if (resp.sw != 0x9000) {
                return null
            }

            val expiryDate = String(resp.data, Charsets.UTF_8).trim()
            return expiryDate.replace("\u0000", "")

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}