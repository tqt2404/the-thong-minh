package viewmodel

import androidx.compose.runtime.*
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import data.model.SignSensitiveOperationRequest
import data.model.CardInfo
import data.repository.CardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import utils.AuthPrefs
import utils.CardManager
import java.util.Base64

sealed class ManageCardState {
    object Idle : ManageCardState()
    object Loading : ManageCardState()
    object Success : ManageCardState()
    data class Error(val msg: String) : ManageCardState()
}

class ManageCardViewModel(
    private val cardRepository: CardRepository
) : ScreenModel {

    // State cho UI binding
    var idCard by mutableStateOf("")
    var fullName by mutableStateOf("")
    var dateOfBirth by mutableStateOf("")
    var address by mutableStateOf("")
    var phoneNumber by mutableStateOf("")
    var expired by mutableStateOf("")
    var pin by mutableStateOf("")
    var avatarBytes: ByteArray? by mutableStateOf(null)

    var uiState by mutableStateOf<ManageCardState>(ManageCardState.Idle)
        private set

    // Đọc thẻ để đổ ra UI
    fun loadCardInfo() {
        uiState = ManageCardState.Loading
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val cardInfo: CardInfo = CardManager.readCardInfo()
                idCard = cardInfo.idCard
                fullName = cardInfo.fullName
                dateOfBirth = cardInfo.dateOfBirth
                address = cardInfo.address
                phoneNumber = cardInfo.phoneNumber
                //expired = cardInfo.expired
                pin = cardInfo.pin
                avatarBytes = cardInfo.avatarBytes
                uiState = ManageCardState.Idle
            } catch (e: Exception) {
                uiState = ManageCardState.Error("Lỗi đọc thẻ: ${e.message}")
            }
        }
    }

    // Cập nhật thông tin lên thẻ + avatar
    fun updateCardInfo(avatarBytesArg: ByteArray? = null) {
        uiState = ManageCardState.Loading
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val token = AuthPrefs.getToken()
                val dataJson = """{
                          "fullName":"$fullName",
                          "dateOfBirth":"$dateOfBirth",
                          "address":"$address",
                          "phoneNumber":"$phoneNumber"
                        }""".trimIndent()

                println("DEBUG dataJson:")
                println(dataJson)
                println("First 20 chars: '${dataJson.take(20)}'")

                // --- Đã bỏ cardId ---
                val req = SignSensitiveOperationRequest(
                    operation = "update_info",
                    data = dataJson
                )
                val sigRes = cardRepository.signSensitiveOperation(token, req)
                val messageBytes = Base64.getDecoder().decode(sigRes.message)
                val sigBytes = Base64.getDecoder().decode(sigRes.signature)

                // Build buffer đúng format applet cần
                val buffer = CardManager.buildUpdateInfoBuffer(
                    msgBytes = messageBytes,
                    sigBytes = sigBytes,
                    fullName = fullName,
                    dateOfBirth = dateOfBirth,
                    address = address,
                    phoneNumber = phoneNumber
                )

                CardManager.updateCardInfoToCard(buffer)

                avatarBytesArg?.let {
                    if (it.isNotEmpty()) CardManager.updateAvatarOnCard(it)
                }

                uiState = ManageCardState.Success
            } catch (e: Exception) {
                uiState = ManageCardState.Error("Lỗi cập nhật: ${e.message}")
            }
        }
    }
}