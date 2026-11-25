package screen

import Navigator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
fun UpdatePinScreen(navigator: Navigator, repository: CardRepository) {
    var newPin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var noInfoOnCard by remember { mutableStateOf<Boolean?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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

    val doUpdatePin: () -> Unit = {
        scope.launch {
            isLoading = true
            errorText = null
            successText = null
            try {
                if (newPin.length !in 4..6) {
                    errorText = "PIN phải từ 4-6 số"
                    return@launch
                }

                val token = AuthPrefs.getToken()
                val dataJson = """{"newPin":"$newPin"}"""
                val response = repository.signSensitiveOperation(
                    token,
                    SignSensitiveOperationRequest(
                        operation = "update_pin",
                        data = dataJson
                    )
                )

                val messageBytes = Base64.getDecoder().decode(response.message)
                val sigBytes = Base64.getDecoder().decode(response.signature)

                CardManager.updatePinOnCard(messageBytes, sigBytes, newPin)

                successText = "Cập nhật PIN thành công!"
                newPin = ""
            } catch (e: Exception) {
                errorText = "Lỗi: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        FeatureScreenTemplate(
            title = "Cấp lại mã PIN",
            description = "Cấp lại mã PIN người dùng",
            onBack = { navigator.navigateTo(Screen.FeatureMenu) },
            gradientColor = Color(0xFF06B6D4),
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
                        else -> Card(
                            modifier = Modifier.width(400.dp).padding(vertical = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = Color(0xFF1E293B),
                            elevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Nhập PIN mới", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = newPin,
                                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 6) newPin = it },
                                    label = { Text("PIN (4-6 số)", color = Color(0xFF94A3B8)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        textColor = Color.White,
                                        cursorColor = Color(0xFF06B6D4),
                                        focusedBorderColor = Color(0xFF06B6D4),
                                        unfocusedBorderColor = Color(0xFF475569)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    visualTransformation = PasswordVisualTransformation(),

                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                if (errorText != null) {
                                    Text(errorText!!, color = Color.Red, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                if (successText != null) {
                                    Text(successText!!, color = Color.Green, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                Button(
                                    onClick = doUpdatePin,
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF06B6D4)),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = newPin.length in 4..6
                                ) {
                                    Text("Cập nhật PIN", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

        )
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99111B1E)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF06B6D4),
                        strokeWidth = 5.dp,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Đang xử lý...", color = Color.White)
                }
            }
        }
    }
}