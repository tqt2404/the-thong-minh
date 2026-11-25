package component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.repository.CardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.CardManager
import utils.calculateParkFee
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun EntryExitTab(repository: CardRepository) {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isInside by remember { mutableStateOf(false) }
    var hasMonthlyPass by remember { mutableStateOf(false) }
    var entryTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var pendingFee by remember { mutableStateOf<Long?>(null) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var triesRemaining by remember { mutableStateOf<Int?>(null) }
    var isCardBlocked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Auto-check card state on mount
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 1. Kiểm tra khóa thẻ (giữ nguyên)
                    isCardBlocked = CardManager.isCardBlocked()

                    // 2. SỬA ĐOẠN NÀY: Thay vì getCardInfo(), gọi getCardExpiryDate()
                    // Vì getCardInfo bị chặn bởi PIN, còn hàm này thì không.
                    val expiryStr = CardManager.getCardExpiryDate()

                    println("Expiry date from card: $expiryStr") // Log để debug

                    if (!expiryStr.isNullOrEmpty() && expiryStr != "0") {
                        // Check monthly pass logic (Giữ nguyên)
                        try {
                            // Đảm bảo định dạng yyyy-MM-dd
                            val expiryDate = LocalDate.parse(expiryStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            val today = LocalDate.now()
                            hasMonthlyPass = !today.isAfter(expiryDate)
                        } catch (e: Exception) {
                            println("Date parse error: ${e.message}")
                            hasMonthlyPass = false
                        }
                    } else {
                        hasMonthlyPass = false
                    }

                    // 3. Get entry state (Giữ nguyên - hàm này cũng không cần PIN)
                    val (inside, entryTimestamp) = CardManager.getEntryState()
                    isInside = inside
                    if (inside && entryTimestamp > 0) {
                        entryTime = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(entryTimestamp),
                            ZoneId.systemDefault()
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val handleEntry: () -> Unit = {
        scope.launch {
            isLoading = true
            errorMessage = null
            successMessage = null
            try {
                // ✅ KIỂM TRA THẺ BỊ KHÓA TRƯỚC KHI VÀO KHU VUI CHƠI
                val blocked = withContext(Dispatchers.IO) {
                    CardManager.isCardBlocked()
                }

                if (blocked) {
                    isCardBlocked = true
                    errorMessage = "🔒 Thẻ đã bị khóa do nhập sai PIN quá số lần cho phép!\n" +
                            "Vui lòng liên hệ quản trị viên để mở khóa thẻ."
                    isLoading = false
                    return@launch
                }

                // Kiểm tra hạn vé tháng
                var isPassValid = false
                try {
                    val expiryStr = withContext(Dispatchers.IO) { CardManager.getCardExpiryDate() }
                    if (!expiryStr.isNullOrEmpty() && expiryStr != "0" && expiryStr.trim() != "-") {
                        val expiryDate = LocalDate.parse(expiryStr.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        isPassValid = !LocalDate.now().isAfter(expiryDate)
                    }
                } catch (_: Exception) {
                    isPassValid = false
                }
                hasMonthlyPass = isPassValid

                if (isPassValid) {
                    val timestamp = System.currentTimeMillis()
                    val success = withContext(Dispatchers.IO) {
                        CardManager.entryGate(timestamp)
                    }
                    if (success) {
                        isInside = true
                        entryTime = LocalDateTime.now()
                        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                        successMessage = "✅ Vào khu vui chơi thành công lúc ${entryTime!!.format(formatter)}\n🎫 Vé tháng - Miễn phí"
                    } else {
                        errorMessage = "❌ Thẻ đã ở trong khu vui chơi!"
                    }
                } else {
                    // Không có vé tháng -> tính phí theo thời gian hiện tại và hiện pop-up thanh toán
                    pendingFee = calculateParkFee(LocalDateTime.now())
                    showPaymentDialog = true
                }
            } catch (e: Exception) {
                errorMessage = "Lỗi vào khu vui chơi: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    val handleExitRequest: () -> Unit = {
        scope.launch {
            isLoading = true
            errorMessage = null
            successMessage = null
            try {
                withContext(Dispatchers.IO) {
                    val (inside, _) = CardManager.getEntryState()
                    if (!inside) {
                        errorMessage = "❌ Thẻ chưa vào khu vui chơi!"
                        return@withContext
                    }

                    val timestamp = System.currentTimeMillis()
                    val (status, _) = CardManager.exitGate(timestamp)

                    if (status == 0x01) {
                        isInside = false
                        entryTime = null
                        val now = LocalDateTime.now()
                        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                        successMessage = "✅ Ra khỏi khu vui chơi thành công lúc ${now.format(formatter)}"
                    } else {
                        errorMessage = "❌ Lỗi ra cổng (Thẻ từ chối lệnh Exit)"
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Lỗi hệ thống: ${e.message}"
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }


    val handlePaymentWithPin: () -> Unit = {
        scope.launch {
            isLoading = true
            pinError = null
            try {
                // 1. Verify PIN
                val (pinSuccess, tries) = withContext(Dispatchers.IO) {
                    CardManager.verifyPIN(pin)
                }

                if (!pinSuccess) {
                    triesRemaining = tries
                    if (tries == 0) {
                        isCardBlocked = true
                        pinError = "🔒 PIN đã bị khóa! Liên hệ quản trị viên để mở khóa."
                    } else {
                        pinError = "❌ Mã PIN không chính xác!\nSố lần thử còn lại: $tries"
                    }
                    pin = ""
                    return@launch
                }

                // 2. PIN verified - proceed with payment
                val currentBalance = withContext(Dispatchers.IO) {
                    CardManager.getMoneyFromCard()
                }

                if (currentBalance < pendingFee!!) {
                    pinError = "❌ Số dư không đủ để thanh toán vé vào công viên!\nSố dư: $currentBalance VNĐ\nVé: ${pendingFee} VNĐ"
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    // 3. Get DATABASE ID
                    val dbId = CardManager.getDatabaseId()
                    println("Database ID: $dbId")

                    // 4. Calculate new balance
                    val newBalance = currentBalance - pendingFee!!

                    // 5. Create transaction message
                    val timestamp = System.currentTimeMillis()
                    val txMessage = "pay:$dbId:${pendingFee}:$timestamp"
                    val txMessageBytes = txMessage.toByteArray(Charsets.UTF_8)

                    println("Transaction message: $txMessage")

                    // 6. Sign with card
                    val cardSignature = CardManager.signDataWithCard(txMessageBytes)
                    println("Card signature length: ${cardSignature.size}")

                    // 7. Send to backend for verification
//                    val response = repository.verifyPayment(
//                        cardId = dbId,
//                        amount = pendingFee!!,
//                        newBalance = newBalance,
//                        message = Base64.getEncoder().encodeToString(txMessageBytes),
//                        signature = Base64.getEncoder().encodeToString(cardSignature)
//                    )
//
//                    if (!response.success) {
//                        throw Exception("Backend verification failed")
//                    }
//
//                    println("Backend verification success")
//                    / 8. Update money on card with backend signature
////                    val backendMessageBytes = Base64.getDecoder().decode(response.message)
////                    val backendSigBytes = Base64.getDecoder().decode(response.backendSignature)
////
////                    CardManager.updateMoneyOnCard(
////                        backendMessageBytes,
////                        backendSigBytes,
////                        newBalance.toString()
////                    )
//                    /

                    // 7. Send to backend for verification
                    val response = repository.verifyPayment(
                        cardId = dbId,
                        amount = pendingFee!!,
                        newBalance = newBalance,
                        message = Base64.getEncoder().encodeToString(txMessageBytes),
                        signature = Base64.getEncoder().encodeToString(cardSignature)
                    )

                    if (!response.success) {
                        throw Exception("Backend verification failed")
                    }

                    println("Backend verification success")

                    // 8. CẬP NHẬT TIỀN TRỰC TIẾP (không cần backend signature)
                    CardManager.updateMoneyOnCardDirect(newBalance.toString())

                    // 9. PAYMENT SUCCESS → NOW call entryGate
                    val entryTimestamp = System.currentTimeMillis()
                    val entered = CardManager.entryGate(entryTimestamp)

                    if (!entered) {
                        throw Exception("Entry gate failed after payment")
                    }

                    // 10. Success - update UI
                    isInside = true
                    val now = LocalDateTime.now()
                    entryTime = now
                    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                    successMessage = "✅ Vào khu vui chơi thành công lúc ${now.format(formatter)}\n🎟️ Vé vào cổng: ${pendingFee} VNĐ"
                    showPinDialog = false
                    showPaymentDialog = false
                    pendingFee = null
                    pin = ""
                }
            } catch (e: Exception) {
                pinError = "Lỗi thanh toán: ${e.message}"
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "🎡",
            fontSize = 64.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            if (isInside) "Ra cửa khu vui chơi" else "Vào cửa khu vui chơi",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            if (isInside) "Nhấn nút để ra khỏi khu vui chơi" else "Nhấn nút để vào khu vui chơi",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Status card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xFF334155),
            elevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Trạng thái:",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        if (isInside) "🎡 Đang trong khu vui chơi" else "✅ Đã ra ngoài",
                        fontSize = 14.sp,
                        color = if (isInside) Color(0xFFFBBF24) else Color(0xFF10B981),
                        fontWeight = FontWeight.Medium
                    )
                }

                // ✅ HIỂN THỊ CẢNH BÁO THẺ BỊ KHÓA
                if (isCardBlocked) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Thẻ:",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            "🔒 Đã bị khóa",
                            fontSize = 14.sp,
                            color = Color(0xFFF87171),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (hasMonthlyPass) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Vé tháng:",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            "✅ Còn hạn",
                            fontSize = 14.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (isInside && entryTime != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Giờ vào: ${entryTime!!.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }

        if (successMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = Color(0xFF10B981).copy(alpha = 0.1f),
                elevation = 0.dp
            ) {
                Text(
                    successMessage!!,
                    color = Color(0xFF10B981),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = Color(0xFFF87171).copy(alpha = 0.1f),
                elevation = 0.dp
            ) {
                Text(
                    errorMessage!!,
                    color = Color(0xFFF87171),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Button(
            onClick = if (isInside) handleExitRequest else handleEntry,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isInside) Color(0xFFF59E0B) else Color(0xFF10B981)
            ),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading && !isCardBlocked // ✅ VÔ HIỆU HÓA NÚT NẾU THẺ BỊ KHÓA
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text(
                    if (isCardBlocked) "🔒 Thẻ bị khóa"
                    else if (isInside) "🚪 Ra cửa"
                    else "🎡 Vào cửa",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ✅ THÔNG BÁO LIÊN HỆ ADMIN NẾU BỊ KHÓA
        if (isCardBlocked) {
            Text(
                "⚠️ Liên hệ quản trị viên để mở khóa thẻ",
                fontSize = 12.sp,
                color = Color(0xFFF87171),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    // Payment Dialog
    if (showPaymentDialog && pendingFee != null && !showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPaymentDialog = false
                pendingFee = null
            },
            title = {
                Text(
                    "🎟️ Thanh toán vé vào công viên",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Phí khu vui chơi của bạn:",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${pendingFee} VNĐ",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6366F1)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Nhấn tiếp tục để nhập mã PIN",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPinDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF6366F1))
                ) {
                    Text("Tiếp tục", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPaymentDialog = false
                        pendingFee = null
                    }
                ) {
                    Text("Hủy")
                }
            }
        )
    }

    // PIN Dialog
    if (showPinDialog && pendingFee != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isLoading) {
                    showPinDialog = false
                    pin = ""
                    pinError = null
                    triesRemaining = null
                }
            },
            title = {
                Text(
                    "🔐 Xác thực thanh toán",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Số tiền thanh toán: ${pendingFee} VNĐ",
                        fontSize = 14.sp,
                        color = Color(0xFF6366F1),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Nhập mã PIN để xác nhận:",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.all { c -> c.isDigit() } && it.length <= 6) {
                                pin = it
                                if (pinError != null) {
                                    pinError = null
                                    triesRemaining = null
                                }
                            }
                        },
                        label = { Text("Mã PIN") },
                        placeholder = { Text("Nhập 4-6 số") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading
                    )

                    if (pinError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            pinError!!,
                            color = Color(0xFFF87171),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = handlePaymentWithPin,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF6366F1)),
                    enabled = !isLoading && pin.length in 4..6
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    } else {
                        Text("Thanh toán", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                        pin = ""
                        pinError = null
                        triesRemaining = null
                    },
                    enabled = !isLoading
                ) {
                    Text("Hủy")
                }
            }
        )
    }
}