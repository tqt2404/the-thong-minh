package viewmodel

import androidx.compose.runtime.*
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import data.repository.CardRepository
import data.model.CardInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import utils.CardManager

sealed class CreateCardState {
    object Idle : CreateCardState()
    object Loading : CreateCardState()
    data class Success(val cardId: Int) : CreateCardState()
    data class Error(val msg: String) : CreateCardState()
}

class CreateCardViewModel(
    private val cardRepository: CardRepository
) : ScreenModel {

    var state by mutableStateOf<CreateCardState>(CreateCardState.Idle)
        private set

    fun createCard(cardInfo: CardInfo) {
        state = CreateCardState.Loading
        screenModelScope.launch(Dispatchers.IO) {
            try {
                // ===== BƯỚC 1: Lấy Backend Public Key =====
                println("DEBUG: Step 1 - Getting backend public key")
                val backendPubKey = cardRepository.getBackendPublicKey().getOrThrow()
                println("DEBUG: Backend public key received: $backendPubKey")

                // ===== BƯỚC 2: Tạo card trên backend, nhận ID =====
                println("DEBUG: Step 2 - Creating card on backend")
                val cardId = cardRepository.createCard().getOrThrow()
                println("DEBUG: Created card with ID: $cardId")

                // ===== BƯỚC 3: Tạo PIN trên thẻ =====
                println("DEBUG: Step 3 - Creating PIN on card")
                createPinOnCard(cardInfo.pin)
                println("DEBUG: PIN created successfully")

                // ===== BƯỚC 4: Verify PIN =====
                println("DEBUG: Step 4 - Verifying PIN")
                verifyPinOnCard(cardInfo.pin)
                println("DEBUG: PIN verified successfully")

                // ===== BƯỚC 5: Gom dữ liệu thành buffer cho APDU create =====
                println("DEBUG: Step 5 - Building card data buffer")
                val cardDataBuffer = buildCreateCardBuffer(cardId, cardInfo)
                println("DEBUG: Card buffer length: ${cardDataBuffer.size}")
                println("DEBUG: Card buffer hex: ${cardDataBuffer.joinToString(" ") { "%02X".format(it) }}")

                // ===== BƯỚC 6: Tạo card data trên thẻ, nhận Card Public Key =====
                // Lưu ý: createCard sẽ tạo CardModel mới trong thẻ
                println("DEBUG: Step 6 - Creating card on device")
                val cardPublicKey = createCardOnDevice(cardDataBuffer)
                println("DEBUG: Card public key received (length: ${cardPublicKey.length})")

                // ===== BƯỚC 7: Gửi Backend Public Key xuống thẻ =====
                // Bây giờ CardModel đã tồn tại, có thể set backend public key
                println("DEBUG: Step 7 - Setting backend public key on card")
                setBackendPublicKeyOnCard(backendPubKey)
                println("DEBUG: Backend public key set on card successfully")

                // ===== BƯỚC 8: Update Card Public Key lên backend =====
                println("DEBUG: Step 8 - Updating card public key on backend")
                cardRepository.updatePublicKey(cardId, cardPublicKey).getOrThrow()
                println("DEBUG: Card public key updated on backend successfully")

                // ============ THÊM PHẦN GỬI AVATAR LÊN THẺ ============
                if (cardInfo.avatarBytes.isNotEmpty()) {
                    println("DEBUG: Step 9 - Updating avatar on card (length: ${cardInfo.avatarBytes.size})")
                    CardManager.updateAvatarOnCard(cardInfo.avatarBytes)
                    println("DEBUG: Avatar updated on card successfully")
                }
                // ============ HẾT PHẦN AVATAR ============

                state = CreateCardState.Success(cardId)
                println("DEBUG: Card creation completed successfully!")
            } catch (e: Exception) {
                state = CreateCardState.Error(e.message ?: "Unknown error")
                println("ERROR: Card creation failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun createPinOnCard(pin: String) {
        val pinBytes = pin.toByteArray(Charsets.UTF_8)
        CardManager.createPinOnCard(pinBytes)
    }

    private fun verifyPinOnCard(pin: String) {
        val pinBytes = pin.toByteArray(Charsets.UTF_8)
        CardManager.verifyPinOnCard(pinBytes)
    }

    private fun createCardOnDevice(cardDataBuffer: ByteArray): String {
        return CardManager.sendCreateCardCommand(cardDataBuffer)
    }

    private fun setBackendPublicKeyOnCard(backendPubKey: String) {
        CardManager.setBackendPublicKeyOnCard(backendPubKey)
    }

    private fun buildCreateCardBuffer(cardId: Int, info: CardInfo): ByteArray {
        // Format: [length][data][length][data]...
        val fields = listOf(
            cardId.toString().toByteArray(Charsets.UTF_8),
            info.idCard.toByteArray(Charsets.UTF_8),
            info.fullName.toByteArray(Charsets.UTF_8),
            info.dateOfBirth.toByteArray(Charsets.UTF_8),
            info.address.toByteArray(Charsets.UTF_8),
            info.phoneNumber.toByteArray(Charsets.UTF_8)
            //info.expired.toByteArray(Charsets.UTF_8)
        )

        println("DEBUG: Field values:")
        println("  Card ID: ${cardId.toString()} (${fields[0].size} bytes)")
        println("  ID Card: ${info.idCard} (${fields[1].size} bytes)")
        println("  Full Name: ${info.fullName} (${fields[2].size} bytes)")
        println("  Date of Birth: ${info.dateOfBirth} (${fields[3].size} bytes)")
        println("  Address: ${info.address} (${fields[4].size} bytes)")
        println("  Phone: ${info.phoneNumber} (${fields[5].size} bytes)")
       // println("  Expired: '${info.expired}' (${fields[7].size} bytes)")

        val totalLen = fields.sumOf { 1 + it.size }
        val buffer = ByteArray(totalLen)
        var pos = 0

        for ((idx, field) in fields.withIndex()) {
            if (field.size > 255) {
                throw IllegalArgumentException("Field $idx too long: ${field.size} bytes (max 255)")
            }
            buffer[pos++] = field.size.toByte()
            System.arraycopy(field, 0, buffer, pos, field.size)
            pos += field.size
        }

        return buffer
    }
}