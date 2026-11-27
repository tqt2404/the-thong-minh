package screen

import Navigator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import utils.CardManager
import utils.AuthPrefs
import data.repository.CardRepository
import data.model.SignSensitiveOperationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64
import javax.smartcardio.CommandAPDU

@Composable
fun UpdateExpiredScreen(navigator: Navigator, repository: CardRepository) {
    var expiredDate by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var noInfoOnCard by remember { mutableStateOf<Boolean?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Check card data on mount
    LaunchedEffect(Unit) {
        isLoading = true
        noInfoOnCard = null
        errorText = null
        scope.launch {
            try {
                val card = CardManager.activeCard
                if (card == null) {
                    errorText = "Không kết nối được tới thẻ"
                } else {
                    val apdu = CommandAPDU(0x00, 0x05, 0x00, 0x00)
                    val resp = withContext(Dispatchers.IO) {
                        card.basicChannel.transmit(apdu)
                    }
                    noInfoOnCard = resp.sw != 0x9000 || resp.data.all { it == 0.toByte() }
                }
            } catch (e: Exception) {
                errorText = "Lỗi đọc thẻ: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    val doUpdateExpired: () -> Unit = {
        scope.launch {
            isLoading = true
            errorText = null
            successText = null
            try {
                // Validate date format DD/MM/YYYY
                val dateRegex = Regex("""^\d{2}/\d{2}/\d{4}$""")
                if (!dateRegex.matches(expiredDate)) {
                    errorText = "Định dạng ngày không hợp lệ (DD/MM/YYYY)"
                    return@launch
                }

                // Get signature from backend
                val token = AuthPrefs.getToken()
                val dataJson = """{"newExpired":"$expiredDate"}"""
                val response = repository.signSensitiveOperation(
                    token,
                    SignSensitiveOperationRequest(
                        operation = "update_expired",
                        data = dataJson
                    )
                )

                val messageBytes = Base64.getDecoder().decode(response.message)
                val sigBytes = Base64.getDecoder().decode(response.signature)

                // Update expired on card
                CardManager.updateExpiredOnCard(messageBytes, sigBytes, expiredDate)

                successText = "Cập nhật hạn thẻ thành công!"
                expiredDate = ""
            } catch (e: Exception) {
                errorText = "Lỗi: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    FeatureScreenTemplate(
        title = "Cập nhật hạn",
        description = "Gia hạn hoặc xóa hạn thẻ",
        onBack = { navigator.navigateTo(Screen.FeatureMenu) },
        gradientColor = Color(0xFF10B981),
        content = {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isLoading -> CircularProgressIndicator()
                    errorText != null -> Text(errorText!!, color = Color.Red)
                    noInfoOnCard == true -> Card(
                        modifier = Modifier.width(400.dp).padding(vertical = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0xFFF87171).copy(alpha = 0.1f),
                        elevation = 8.dp
                    ) {
                        Text(
                            "Thẻ chưa có dữ liệu",
                            color = Color(0xFFF87171),
                            modifier = Modifier.padding(24.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    else -> ui.AppCard(modifier = Modifier.width(440.dp)) {
                        Text("Ngày hết hạn", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ui.ParkColors.TextPrimary)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = expiredDate,
                            onValueChange = {
                                if (it.length <= 10 && it.all { c -> c.isDigit() || c == '/' }) {
                                    expiredDate = it
                                }
                            },
                            label = { Text("DD/MM/YYYY") },
                            placeholder = { Text("VD: 31/12/2025") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.lightTextFieldColors(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        ui.StatusBanner(errorText, isError = true)
                        ui.StatusBanner(successText)
                        if (errorText != null || successText != null) Spacer(modifier = Modifier.height(12.dp))

                        ui.PrimaryButton(
                            text = "Cập nhật hạn",
                            onClick = doUpdateExpired,
                            enabled = expiredDate.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    )
}