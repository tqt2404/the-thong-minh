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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.repository.CardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.CardManager
import java.util.Base64

@Composable
fun UserUpdatePinScreen(navigator: Navigator, repository: CardRepository) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val doUpdatePin: () -> Unit = {
        scope.launch {
            isLoading = true
            errorText = null
            successText = null
            try {
                // Validate input
                if (currentPin.length !in 4..6) {
                    errorText = "PIN hiện tại phải từ 4-6 số"
                    return@launch
                }
                if (newPin.length !in 4..6) {
                    errorText = "PIN mới phải từ 4-6 số"
                    return@launch
                }
                if (newPin != confirmPin) {
                    errorText = "PIN mới không khớp!"
                    return@launch
                }
                if (currentPin == newPin) {
                    errorText = "PIN mới phải khác PIN hiện tại!"
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    // 1. Verify current PIN first
                    val verifyResult = CardManager.verifyPIN(currentPin)
                    if (!verifyResult.first) {
                        val tries = verifyResult.second
                        errorText = if (tries == 0) {
                            "Thẻ đã bị khóa!"
                        } else {
                            "PIN hiện tại sai! Còn $tries lần thử"
                        }
                        return@withContext
                    }

                    println("✓ Current PIN verified")

                    // 2. Get DATABASE ID
                    val dbId = CardManager.getDatabaseId()
                    println("Database ID: $dbId")

                    // 3. Create update PIN message
                    val timestamp = System.currentTimeMillis()
                    val txMessage = "update_pin:$dbId:$newPin:$timestamp"
                    val txMessageBytes = txMessage.toByteArray(Charsets.UTF_8)

                    println("Transaction message: $txMessage")

                    // Debug hash
                    println("=== CLIENT SIGN DEBUG ===")
                    println("Message bytes: ${txMessageBytes.joinToString(" ") { "%02X".format(it) }}")
                    println("Message length: ${txMessageBytes.size}")
                    val testHash = java.security.MessageDigest.getInstance("SHA-1")
                    val clientHash = testHash.digest(txMessageBytes)
                    println("Client computed SHA-1: ${clientHash.joinToString("") { "%02X".format(it) }}")
                    println("========================")

                    // 4. Sign with card
                    val cardSignature = CardManager.signDataWithCard(txMessageBytes)
                    println("Card signature length: ${cardSignature.size}")

                    // 5. Send to backend for verification (CHỈ XÁC THỰC, KHÔNG LẤY SIGNATURE)
                    val response = repository.verifyUpdatePin(
                        cardId = dbId,
                        newPin = newPin,
                        message = Base64.getEncoder().encodeToString(txMessageBytes),
                        signature = Base64.getEncoder().encodeToString(cardSignature)
                    )

                    if (!response.success) {
                        throw Exception("Backend verification failed")
                    }

                    println("✓ Backend verification success (USER MODE)")

                    // 6. Update PIN on card TRỰC TIẾP (KHÔNG CẦN BACKEND SIGNATURE)
                    CardManager.updatePinOnCardDirect(newPin)

                    // Update UI
                    successText = "Đổi PIN thành công!\nVui lòng sử dụng PIN mới từ lần sau"
                    currentPin = ""
                    newPin = ""
                    confirmPin = ""
                }

            } catch (e: Exception) {
                errorText = "Lỗi đổi PIN: ${e.message}"
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    FeatureScreenTemplate(
        title = "Đổi PIN",
        description = "Thay đổi mã PIN của thẻ",
        onBack = { navigator.navigateTo(Screen.FeatureMenu) },
        gradientColor = Color(0xFF06B6D4),
        content = {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.width(500.dp),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0xFF1E293B),
                    elevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "🔐",
                            fontSize = 48.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            "Cập nhật mã PIN",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // Current PIN
                        OutlinedTextField(
                            value = currentPin,
                            onValueChange = {
                                if (it.all { c -> c.isDigit() } && it.length <= 6) currentPin = it
                            },
                            label = { Text("PIN hiện tại", color = Color(0xFF94A3B8)) },
                            placeholder = { Text("Nhập 4-6 số", color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = Color.White,
                                cursorColor = Color(0xFF06B6D4),
                                focusedBorderColor = Color(0xFF06B6D4),
                                unfocusedBorderColor = Color(0xFF475569)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // New PIN
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = {
                                if (it.all { c -> c.isDigit() } && it.length <= 6) newPin = it
                            },
                            label = { Text("PIN mới", color = Color(0xFF94A3B8)) },
                            placeholder = { Text("Nhập 4-6 số", color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = Color.White,
                                cursorColor = Color(0xFF06B6D4),
                                focusedBorderColor = Color(0xFF06B6D4),
                                unfocusedBorderColor = Color(0xFF475569)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Confirm PIN
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = {
                                if (it.all { c -> c.isDigit() } && it.length <= 6) confirmPin = it
                            },
                            label = { Text("Xác nhận PIN mới", color = Color(0xFF94A3B8)) },
                            placeholder = { Text("Nhập lại PIN mới", color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = Color.White,
                                cursorColor = Color(0xFF06B6D4),
                                focusedBorderColor = Color(0xFF06B6D4),
                                unfocusedBorderColor = Color(0xFF475569)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (errorText != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = Color(0xFFF87171).copy(alpha = 0.1f),
                                elevation = 0.dp
                            ) {
                                Text(
                                    errorText!!,
                                    color = Color(0xFFF87171),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        if (successText != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = Color(0xFF10B981).copy(alpha = 0.1f),
                                elevation = 0.dp
                            ) {
                                Text(
                                    successText!!,
                                    color = Color(0xFF10B981),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = doUpdatePin,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF06B6D4)),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading &&
                                    currentPin.length in 4..6 &&
                                    newPin.length in 4..6 &&
                                    confirmPin.length in 4..6
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    "Đổi PIN",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}