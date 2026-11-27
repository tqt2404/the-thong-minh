package component

import Navigator
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.CardManager

@Composable
fun LoginTab(navigator: Navigator) {
    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var triesRemaining by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Đăng nhập thẻ",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = ui.ParkColors.TextPrimary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Text(
            "Nhập mã PIN của bạn",
            fontSize = 14.sp,
            color = ui.ParkColors.TextSecondary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.all { c -> c.isDigit() } && it.length <= 6) {
                    pin = it
                    if (errorMessage != null) {
                        errorMessage = null
                        triesRemaining = null
                    }
                }
            },
            label = { Text("Mã PIN") },
            placeholder = { Text("Nhập 4-6 số") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = ui.lightTextFieldColors(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = Color(0xFFF87171).copy(alpha = 0.1f),
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        errorMessage!!,
                        color = Color(0xFFF87171),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (triesRemaining != null) {
                        Text(
                            "Số lần thử còn lại: $triesRemaining",
                            color = Color(0xFFF87171),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    triesRemaining = null
                    try {
                        val (success, tries) = withContext(Dispatchers.IO) {
                            CardManager.verifyPIN(pin)
                        }

                        if (success) {
                            navigator.navigateTo(Screen.FeatureMenu)
                        } else {
                            triesRemaining = tries
                            errorMessage = if (tries == 0) {
                                "PIN đã bị khóa! Liên hệ quản trị viên."
                            } else {
                                "Mã PIN không chính xác!"
                            }
                            pin = ""
                        }
                    } catch (e: Exception) {
                        errorMessage = "Lỗi: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF6366F1)),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading && pin.length in 4..6
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text(
                    "Đăng nhập",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                CardManager.disconnect()
                navigator.navigateTo(Screen.ConnectCard)
            }
        ) {
            Text(
                "Ngắt kết nối thẻ",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp
            )
        }
    }
}