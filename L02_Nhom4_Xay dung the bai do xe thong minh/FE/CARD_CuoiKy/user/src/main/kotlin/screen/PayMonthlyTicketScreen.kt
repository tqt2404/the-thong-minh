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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.repository.CardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.formatVnAmount
import utils.CardManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64

data class TicketPackage(
    val name: String,
    val months: Int,
    val price: Long,
    val displayPrice: String
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PayMonthlyTicketScreen(navigator: Navigator, repository: CardRepository) {
    val ticketPackages = listOf(
        TicketPackage("1 tháng", 1, 100_000, "100,000 VNĐ"),
        TicketPackage("3 tháng", 3, 270_000, "270,000 VNĐ"),
        TicketPackage("6 tháng", 6, 540_000, "540,000 VNĐ"),
        TicketPackage("1 năm", 12, 1_000_000, "1,000,000 VNĐ")
    )

    var selectedPackage by remember { mutableStateOf(ticketPackages[0]) }
    var expanded by remember { mutableStateOf(false) }
    var currentBalance by remember { mutableStateOf<Long?>(null) }
    var currentExpiry by remember { mutableStateOf<String?>(null) }
    var isExpired by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Load current balance and expiry on mount
    LaunchedEffect(Unit) {
        isLoading = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    currentBalance = CardManager.getMoneyFromCard()

                    val cardInfo = CardManager.getCardInfo()
                    // Lấy chuỗi raw
                    val rawExpiry = cardInfo?.get("expired")

                    // --- ĐOẠN SỬA ---
                    // 1. Loại bỏ ký tự NULL (\u0000)
                    // 2. Chỉ giữ lại 10 ký tự đầu tiên (yyyy-MM-dd là 10 ký tự) để chắc chắn
                    val expiryStr = rawExpiry?.replace("\u0000", "")?.trim()?.take(10)
                    // ----------------

                    if (!expiryStr.isNullOrEmpty() && expiryStr != "0") {
                        currentExpiry = expiryStr

                        try {
                            // Bây giờ expiryStr đã sạch ("2026-02-02"), parse sẽ thành công
                            val expiryDate = LocalDate.parse(expiryStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            val today = LocalDate.now()

                            // Logic so sánh
                            isExpired = today.isAfter(expiryDate)
                        } catch (e: Exception) {
                            println("Lỗi parse ngày: ${e.message}") // Log ra để debug
                            isExpired = true
                        }
                    } else {
                        isExpired = true
                    }
                }
            } catch (e: Exception) {
                errorText = "Lỗi đọc thông tin thẻ: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    val doPayment: () -> Unit = {
        scope.launch {
            isLoading = true
            errorText = null
            successText = null
            try {
                val paymentAmount = selectedPackage.price
                val balance = currentBalance ?: 0L

                if (balance < paymentAmount) {
                    errorText = "Số dư không đủ! Hiện tại: ${balance} VNĐ"
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    // 1. Get DATABASE ID
                    val dbId = CardManager.getDatabaseId()
                    println("Database ID: $dbId")

                    // 2. Calculate new balance
                    val newBalance = balance - paymentAmount

                    // 3. Create transaction message
                    val timestamp = System.currentTimeMillis()
                    val txMessage = "pay:$dbId:$paymentAmount:$timestamp"
                    val txMessageBytes = txMessage.toByteArray(Charsets.UTF_8)

                    println("Transaction message: $txMessage")
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

                    // 5. Send to backend for verification
                    val response = repository.verifyPayment(
                        cardId = dbId,
                        amount = paymentAmount,
                        newBalance = newBalance,
                        message = Base64.getEncoder().encodeToString(txMessageBytes),
                        signature = Base64.getEncoder().encodeToString(cardSignature)
                    )

                    if (!response.success) {
                        throw Exception("Backend verification failed")
                    }

                    println("Backend verification success")

                    // 6. CẬP NHẬT TIỀN TRỰC TIẾP (không cần backend signature)
                    CardManager.updateMoneyOnCardDirect(newBalance.toString())

                    // 7. Calculate new expiry date
                    val currentDate = LocalDate.now()
                    val newExpiryDate = currentDate.plusMonths(selectedPackage.months.toLong())

                    // Format for card (yyyy-MM-dd)
                    val cardDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    val expiryDateForCard = newExpiryDate.format(cardDateFormatter)

                    // Format for display (dd/MM/yyyy)
                    val displayDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    val expiryDateDisplay = newExpiryDate.format(displayDateFormatter)

                    println("Updating expiry date to: $expiryDateForCard")

                    // 8. Update expiry date on card (NO AUTHORIZATION REQUIRED)
                    CardManager.updateExpiredOnCard(expiryDateForCard)

                    // Update UI
                    currentBalance = newBalance
                    currentExpiry = expiryDateForCard
                    isExpired = false
                    successText = "Thanh toán thành công gói ${selectedPackage.name}!\n" +
                            "Số tiền: ${selectedPackage.displayPrice}\n" +
                            "Số dư mới: ${newBalance} VNĐ\n" +
                            "Hạn mới: ${expiryDateDisplay}"
                }

            } catch (e: Exception) {
                errorText = "Lỗi thanh toán: ${e.message}"
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    FeatureScreenTemplate(
        title = "Thanh toán vé tháng",
        description = "Thanh toán và gia hạn vé tháng công viên",
        onBack = { navigator.navigateTo(Screen.FeatureMenu) },
        gradientColor = Color(0xFFEC4899),
        content = {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Loading state
                if (isLoading && currentExpiry == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color(0xFFEC4899)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Đang kiểm tra thẻ...", color = Color.White)
                        }
                    }
                    return@FeatureScreenTemplate
                }

                // Check if still valid (NOT expired)
                if (!isExpired && currentExpiry != null) {
                    // Show "Still Valid" message
                    Card(
                        modifier = Modifier.width(500.dp),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0xFF1E293B),
                        elevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("✅", fontSize = 64.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Thẻ của bạn vẫn còn hạn",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Format expiry date for display
                            val expiryDisplay = try {
                                val expiryDate = LocalDate.parse(currentExpiry, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                expiryDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            } catch (e: Exception) {
                                currentExpiry
                            }

                            Text(
                                "Hạn sử dụng đến ngày:",
                                fontSize = 14.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (expiryDisplay != null) {
                                Text(
                                    expiryDisplay,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { navigator.navigateTo(Screen.FeatureMenu) },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF475569)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "Quay lại",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    // Show payment form (EXPIRED or NO EXPIRY)
                    // Card hiển thị số dư
                    Card(
                        modifier = Modifier.width(500.dp).padding(bottom = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0xFF1E293B),
                        elevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("💰", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Số dư hiện tại",
                                fontSize = 14.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (isLoading && currentBalance == null) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            } else {
                                Text(
                                    "${formatVnAmount(currentBalance.toString()) ?: 0} VNĐ",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }

                    // Card chọn gói vé
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
                                "Chọn gói vé tháng",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Dropdown chọn gói
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    value = "${selectedPackage.name} - ${selectedPackage.displayPrice}",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Gói vé", color = Color(0xFF94A3B8)) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        textColor = Color.White,
                                        cursorColor = Color(0xFFEC4899),
                                        focusedBorderColor = Color(0xFFEC4899),
                                        unfocusedBorderColor = Color(0xFF475569),
                                        backgroundColor = Color(0xFF1E293B)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    ticketPackages.forEach { pkg ->
                                        DropdownMenuItem(
                                            onClick = {
                                                selectedPackage = pkg
                                                expanded = false
                                            }
                                        ) {
                                            Column {
                                                Text(
                                                    pkg.name,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    pkg.displayPrice,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF10B981)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Thông tin gói đã chọn
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = Color(0xFF334155),
                                elevation = 0.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Thời hạn:", color = Color(0xFF94A3B8))
                                        Text(
                                            selectedPackage.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Số tiền:", color = Color(0xFF94A3B8))
                                        Text(
                                            selectedPackage.displayPrice,
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

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
                                onClick = doPayment,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFEC4899)),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        "Thanh toán ${selectedPackage.displayPrice}",
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
        }
    )
}