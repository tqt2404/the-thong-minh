package screen

import Navigator
import Screen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.CardManager
import javax.smartcardio.*

data class SmartCardInfo(
    val atr: String,
    val readerName: String
)

fun connectSmartCardHold(): Triple<SmartCardInfo, Card, CardTerminal> {
    val factory = TerminalFactory.getDefault()
    val terminals = factory.terminals().list()
    require(terminals.isNotEmpty()) { "Không tìm thấy đầu đọc thẻ!" }
    val validTerminals = terminals.filter { !it.name.contains("Windows Hello", ignoreCase = true) }
    require(validTerminals.isNotEmpty()) { "Không tìm thấy đầu đọc thẻ!" }
    val terminal = validTerminals.firstOrNull { it.isCardPresent }
        ?: validTerminals.firstOrNull { it.name.contains("Oracle", ignoreCase = true) }
        ?: validTerminals.first()
    terminal.waitForCardPresent(5000)
    val card = terminal.connect("*")
    val atr = card.atr.bytes.joinToString(" ") { "%02X".format(it) }
    return Triple(SmartCardInfo(atr, terminal.name), card, terminal)
}

fun selectFixedAid(card: Card, aidHex: String): Boolean {
    val aidBytes = aidHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val apdu = CommandAPDU(0x00, 0xA4, 0x04, 0x00, aidBytes)
    val resp = card.basicChannel.transmit(apdu)
    return resp.sw == 0x9000
}

@Composable
fun ConnectCardScreen(navigator: Navigator) {
    var isConnecting by remember { mutableStateOf(false) }
    var atr by remember { mutableStateOf<String?>(null) }
    var readerName by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val AID = "112233445500"

    // Đăng ký callback: khi thẻ bị rút → trở về ConnectCard
    LaunchedEffect(Unit) {
        CardManager.onCardRemoved = {
            atr = null
            readerName = null
            selectStatus = null
            errorMessage = "Thẻ bị rút ra! Vui lòng kết nối lại."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B),
                        Color(0xFF334155)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CARD",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Kết nối thẻ thông minh",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Đặt thẻ vào đầu đọc để bắt đầu",
                fontSize = 16.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    isConnecting = true
                    errorMessage = null
                    selectStatus = null
                    atr = null
                    readerName = null
                    scope.launch {
                        try {
                            // 1. Kết nối vật lý
                            val (info, card, terminal) = withContext(Dispatchers.IO) {
                                connectSmartCardHold()
                            }

                            // 2. Chọn Applet (Select AID)
                            val selectSuccess = withContext(Dispatchers.IO) {
                                selectFixedAid(card, AID)
                            }

                            if (selectSuccess) {
                                // Cấu hình CardManager ngay khi Select thành công
                                CardManager.activeCard = card
                                CardManager.activeChannel = card.basicChannel
                                CardManager.startCardWatcher(scope, terminal)

                                // --- BẮT ĐẦU LOGIC KIỂM TRA QUYỀN ---

                                // Bước 3: Thử đăng nhập bằng Admin Key (cho thẻ đã có dữ liệu)
                                val isAdminVerified = withContext(Dispatchers.IO) {
                                    try {
                                        CardManager.verifySystemSecret()
                                    } catch (e: Exception) {
                                        false // Nếu lỗi (sai key hoặc chưa set key) -> trả về false
                                    }
                                }

                                if (isAdminVerified) {
                                    // ==> CASE 1: Thẻ cũ, Key đúng -> Cho vào
                                    atr = info.atr
                                    readerName = info.readerName
                                    selectStatus = "Đăng nhập Admin thành công!"
                                    isConnecting = false
                                    navigator.navigateTo(Screen.FeatureMenu)
                                } else {
                                    // Bước 4: Nếu Admin Key sai, kiểm tra xem có phải thẻ trắng không?
                                    // (Lưu ý: Bạn phải đã thêm hàm isCardEmpty vào CardManager như hướng dẫn trước)
                                    val isCardEmpty = withContext(Dispatchers.IO) {
                                        try {
                                            CardManager.isCardEmpty()
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }

                                    if (isCardEmpty) {
                                        // ==> CASE 2: Thẻ trắng tinh (Chưa có Key) -> Cho vào để tạo mới
                                        atr = info.atr
                                        readerName = info.readerName
                                        selectStatus = "Thẻ mới (Chưa định danh)"
                                        isConnecting = false
                                        navigator.navigateTo(Screen.FeatureMenu)
                                    } else {
                                        // ==> CASE 3: Thẻ đã có dữ liệu nhưng Key Admin sai -> CHẶN
                                        withContext(Dispatchers.Main) {
                                            CardManager.disconnect() // Ngắt kết nối ngay
                                        }
                                        selectStatus = null
                                        errorMessage = "Lỗi: Không thể xác thực quyền Admin với thẻ này!"
                                        isConnecting = false
                                    }
                                }
                                // --- KẾT THÚC LOGIC KIỂM TRA QUYỀN ---

                            } else {
                                card.disconnect(false)
                                selectStatus = null
                                errorMessage = "Select AID thất bại, thẻ không chứa applet với AID $AID"
                                isConnecting = false
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            errorMessage = e.message ?: "Kết nối thẻ thất bại"
                            isConnecting = false
                        }
                    }
                },
                modifier = Modifier
                    .width(300.dp)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF6366F1)
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Đang kết nối...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "Kết nối thẻ",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            if (atr != null && readerName != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Card(
                    backgroundColor = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Kết nối thành công", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Đầu đọc: $readerName", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        Text("ATR: $atr", color = Color(0xFF38BDF8), fontSize = 12.sp)
                        if (selectStatus != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(selectStatus!!, color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Card(
                    backgroundColor = Color(0xFFF87171).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text(
                        errorMessage!!,
                        color = Color(0xFFF87171),
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (CardManager.activeCard != null) {
                TextButton(
                    onClick = {
                        CardManager.disconnect()
                        atr = null
                        readerName = null
                        selectStatus = null
                        errorMessage = null
                    }
                ) {
                    Text(
                        text = "Ngắt kết nối thẻ",
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            TextButton(
                onClick = {
                    CardManager.disconnect()
                    navigator.navigateTo(Screen.Login)
                }
            ) {
                Text(
                    text = "Quay lại",
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}