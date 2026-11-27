package screen

import Navigator
import Screen
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.CardManager
import utils.AuthPrefs
import data.repository.CardRepository
import data.model.SignSensitiveOperationRequest
import java.util.Base64
import javax.smartcardio.CommandAPDU

@Composable
fun UnlockCardScreen(navigator: Navigator, repository: CardRepository) {
    var isLoading by remember { mutableStateOf(false) }
    var noInfoOnCard by remember { mutableStateOf<Boolean?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Check card data on mount (giống UpdateExpiredScreen)
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
                    val apdu = CommandAPDU(0x00, 0x05, 0x00, 0x00) // INS_GET_CCCD
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

    val doUnlock: () -> Unit = {
        scope.launch {
            isLoading = true
            errorText = null
            successText = null
            try {
                // Get signature from backend (giống UpdatePinScreen)
                val token = AuthPrefs.getToken()
                val dataJson = """{"action":"reset_pin"}"""
                val response = repository.signSensitiveOperation(
                    token,
                    SignSensitiveOperationRequest(
                        operation = "reset_pin",
                        data = dataJson
                    )
                )

                val messageBytes = Base64.getDecoder().decode(response.message)
                val sigBytes = Base64.getDecoder().decode(response.signature)

                // Reset PIN on card
                CardManager.resetPinOnCard(messageBytes, sigBytes)

                successText = "✅ Mở khóa thẻ thành công!\nThẻ đã được reset về 5 lần thử."
            } catch (e: Exception) {
                errorText = "❌ Lỗi mở khóa: ${e.message}"
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    FeatureScreenTemplate(
        title = "Mở khóa thẻ",
        description = "Reset số lần nhập PIN khi thẻ bị khóa",
        onBack = { navigator.navigateTo(Screen.FeatureMenu) },
        gradientColor = Color(0xFFF59E0B),
        content = {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isLoading -> CircularProgressIndicator()
                    errorText != null && noInfoOnCard == null -> Text(errorText!!, color = Color.Red)
                    noInfoOnCard == true -> Card(
                        modifier = Modifier.width(400.dp).padding(vertical = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0xFFF87171).copy(alpha = 0.1f),
                        elevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⚠️",
                                fontSize = 48.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Text(
                                "Thẻ chưa có dữ liệu",
                                color = Color(0xFFF87171),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ui.AppCard(modifier = Modifier.width(460.dp)) {
                        Text(
                            text = "Mở khóa thẻ bị chặn",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = ui.ParkColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Khi thẻ bị khóa do nhập sai PIN 5 lần, sử dụng chức năng này để đặt lại số lần thử.",
                            fontSize = 14.sp,
                            color = ui.ParkColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ui.StatusBanner(errorText, isError = true)
                        ui.StatusBanner(successText)
                        if (errorText != null || successText != null) Spacer(modifier = Modifier.height(12.dp))
                        ui.PrimaryButton(
                            text = "Mở khóa thẻ",
                            onClick = doUnlock,
                            enabled = !isLoading,
                            isLoading = isLoading,
                            backgroundColor = ui.ParkColors.Warning,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                        // Note
                        Card(
                            modifier = Modifier.width(400.dp).padding(top = 24.dp),
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = Color(0xFF3B82F6).copy(alpha = 0.1f),
                            elevation = 0.dp
                        ) {
                            Row(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ℹ️",
                                    fontSize = 20.sp,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column {
                                    Text(
                                        text = "Lưu ý",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF3B82F6),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(
                                        text = "• Chức năng này chỉ reset số lần thử\n• PIN cũ vẫn được giữ nguyên\n• Sau khi mở khóa, user dùng PIN cũ để đăng nhập",
                                        fontSize = 12.sp,
                                        color = ui.ParkColors.TextSecondary,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}