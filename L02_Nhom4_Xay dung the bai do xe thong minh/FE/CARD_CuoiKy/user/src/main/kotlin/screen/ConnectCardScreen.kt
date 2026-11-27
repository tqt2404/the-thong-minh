package screen

import Navigator
import Screen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.api.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.CardManager
import java.util.Base64

@Composable
fun ConnectCardScreen(navigator: Navigator) {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ui.ParkColors.Background),
        contentAlignment = Alignment.Center
    ) {
        ui.AppCard(
            modifier = Modifier
                .width(440.dp)
                .padding(24.dp),
            padding = 32.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(ui.ParkColors.PrimarySoft, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "NFC",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ui.ParkColors.Primary
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Kết nối thẻ",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = ui.ParkColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    "Đặt thẻ lên đầu đọc và nhấn Kết nối",
                    fontSize = 14.sp,
                    color = ui.ParkColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                ui.StatusBanner(statusMessage)
                ui.StatusBanner(errorMessage, isError = true)
                if (statusMessage != null || errorMessage != null) Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            statusMessage = null

                            try {
                                // Bước 1: Kết nối vật lý với thẻ
                                statusMessage = " Đang kết nối với thẻ..."
                                val connectSuccess = withContext(Dispatchers.IO) {
                                    CardManager.connect()
                                }

                                if (!connectSuccess) {
                                    errorMessage = "Không thể kết nối với thẻ. Vui lòng kiểm tra lại."
                                    isLoading = false
                                    return@launch
                                }

                                // Bước 2: Kiểm tra thẻ trắng (chưa có dữ liệu)
                                statusMessage = " Đang kiểm tra thẻ..."
                                val isCardEmpty = withContext(Dispatchers.IO) {
                                    try {
                                        CardManager.isCardEmpty()
                                    } catch (e: Exception) {
                                        false
                                    }
                                }

                                if (isCardEmpty) {
                                    withContext(Dispatchers.Main) {
                                        CardManager.disconnect()
                                    }
                                    errorMessage = "⚠ Thẻ chưa được kích hoạt!\nVui lòng liên hệ quản trị viên."
                                    isLoading = false
                                    return@launch
                                }

                                // Bước 3: Xác thực System Secret
                                statusMessage = " Đang xác thực System Secret..."
                                val isSecretVerified = withContext(Dispatchers.IO) {
                                    try {
                                        CardManager.verifySystemSecret()
                                    } catch (e: Exception) {
                                        false
                                    }
                                }

                                if (!isSecretVerified) {
                                    withContext(Dispatchers.Main) {
                                        CardManager.disconnect()
                                    }
                                    errorMessage = " Thẻ không hợp lệ!\nKhông thể xác thực thẻ này."
                                    isLoading = false
                                    return@launch
                                }

                                // Bước 4: Lấy Card ID từ thẻ
                                statusMessage = " Đang lấy thông tin thẻ..."
                                val cardId = withContext(Dispatchers.IO) {
                                    try {
                                        CardManager.getDatabaseId()
                                    } catch (e: Exception) {
                                        null
                                    }
                                }

                                if (cardId == null) {
                                    withContext(Dispatchers.Main) {
                                        CardManager.disconnect()
                                    }
                                    errorMessage = " Không thể lấy ID thẻ!"
                                    isLoading = false
                                    return@launch
                                }

                                // Bước 5: Tạo message để ký (format: "auth:cardId:timestamp")
                                statusMessage = "✍ Đang xác thực chữ ký..."
                                val timestamp = System.currentTimeMillis()
                                val message = "auth:$cardId:$timestamp"
                                val messageBytes = message.toByteArray(Charsets.UTF_8)

                                // Bước 6: Ký message bằng Private Key trên thẻ
                                val signatureBytes = withContext(Dispatchers.IO) {
                                    try {
                                        CardManager.signDataWithCard(messageBytes)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }

                                if (signatureBytes == null) {
                                    withContext(Dispatchers.Main) {
                                        CardManager.disconnect()
                                    }
                                    errorMessage = " Không thể tạo chữ ký!"
                                    isLoading = false
                                    return@launch
                                }

                                // Bước 7: Gửi lên Backend để xác thực RSA
                                statusMessage = " Đang xác thực với máy chủ..."
                                val authResponse = withContext(Dispatchers.IO) {
                                    try {
                                        val messageBase64 = Base64.getEncoder().encodeToString(messageBytes)
                                        val signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes)

                                        RetrofitInstance.cardRepository.verifyCardAuth(
                                            cardId = cardId,
                                            message = messageBase64,
                                            signature = signatureBase64
                                        )
                                    } catch (e: Exception) {
                                        println("Backend auth error: ${e.message}")
                                        e.printStackTrace()
                                        null
                                    }
                                }

                                if (authResponse == null || !authResponse.success) {
                                    withContext(Dispatchers.Main) {
                                        CardManager.disconnect()
                                    }
                                    errorMessage = "🚫 THẺ GIẢ!\nChữ ký không hợp lệ."
                                    isLoading = false
                                    return@launch
                                }

                                // ✅ CASE: Thẻ hợp lệ, chữ ký đúng → Cho vào
                                statusMessage = "✅ Xác thực thành công!"
                                kotlinx.coroutines.delay(800)
                                navigator.navigateTo(Screen.Login)

                            } catch (e: Exception) {
                                errorMessage = "Lỗi: ${e.message}"
                                e.printStackTrace()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF6366F1)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Đang xác thực...",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "Kết nối",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}