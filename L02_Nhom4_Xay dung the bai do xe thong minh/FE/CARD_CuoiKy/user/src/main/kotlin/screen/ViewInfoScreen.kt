package screen

import Navigator
import Screen
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import utils.CardManager
import utils.formatVnAmount
import javax.smartcardio.CommandAPDU

@Composable
fun ViewInfoScreen(navigator: Navigator) {
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var noInfoOnCard by remember { mutableStateOf<Boolean?>(null) }

    // Các trường thông tin
    var idCard by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var expired by remember { mutableStateOf("") }
    var avatarImage by remember { mutableStateOf<ImageBitmap?>(null) }

    val scope = rememberCoroutineScope()

    fun parseCardInfo(data: ByteArray): Map<String, String> {
        val fields = mutableListOf<String>()
        var pos = 0
        while (pos < data.size) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0 || pos + 1 + len > data.size) break

            // --- ĐOẠN ĐÃ SỬA ---
            val value = data.copyOfRange(pos + 1, pos + 1 + len)
                .toString(Charsets.UTF_8)
                .replace("\u0000", "") // Quan trọng: Xóa ký tự Null byte
                .trim()                // Xóa khoảng trắng thừa
            // -------------------

            fields.add(value)
            pos += 1 + len
        }

        val keys = listOf("idCard", "fullName", "dob", "address", "phone", "balance", "expired")

        // Xử lý trường hợp số lượng field đọc được ít hơn key (để tránh lỗi crash nếu thẻ chưa đủ thông tin)
        val safeFields = if (fields.size >= keys.size) fields else fields + List(keys.size - fields.size) { "" }

        return keys.zip(safeFields).toMap()
    }
    suspend fun loadAvatar(): ImageBitmap? {
        val card = CardManager.activeCard ?: return null
        // Gửi lệnh GET_AVATAR (INS=0x11)
        val apdu = CommandAPDU(0x00, 0x11, 0x00, 0x00)
        val resp = withContext(Dispatchers.IO) {
            card.basicChannel.transmit(apdu)
        }
        if (resp.sw != 0x9000 || resp.data.isEmpty()) return null
        // Giả định ảnh trả về là JPEG/PNG bytes (bạn lưu sao thì đọc vậy)
        return try {
            val skiaImg = Image.makeFromEncoded(resp.data)
            Bitmap.makeFromImage(skiaImg).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        errorText = null
        noInfoOnCard = null
        scope.launch {
            try {
                val card = CardManager.activeCard
                if (card == null) {
                    errorText = "Không kết nối được tới thẻ"
                } else {
                    // Gửi lệnh GET_INFO (INS=0x05)
                    val apdu = CommandAPDU(0x00, 0x05, 0x00, 0x00)
                    val resp = withContext(Dispatchers.IO) {
                        card.basicChannel.transmit(apdu)
                    }

                    if (resp.sw != 0x9000 || resp.data.all { it == 0.toByte() }) {
                        noInfoOnCard = true
                    } else {
                        noInfoOnCard = false
                        val cardInfo = parseCardInfo(resp.data)
                        idCard = cardInfo["idCard"] ?: ""
                        fullName = cardInfo["fullName"] ?: ""
                        dob = cardInfo["dob"] ?: ""
                        address = cardInfo["address"] ?: ""
                        phone = cardInfo["phone"] ?: ""
                        balance = cardInfo["balance"] ?: "0"
                        expired = cardInfo["expired"] ?: ""
                        avatarImage = loadAvatar()
                    }
                }
            } catch (e: Exception) {
                errorText = "Lỗi đọc thẻ: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    FeatureScreenTemplate(
        title = "Thông tin cá nhân",
        description = "Xem thông tin đầy đủ trên thẻ",
        onBack = { navigator.navigateTo(Screen.FeatureMenu) },
        gradientColor = Color(0xFF6366F1),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isLoading -> CircularProgressIndicator()
                    errorText != null -> Text(errorText!!, color = Color.Red)
                    noInfoOnCard == true -> Card(
                        modifier = Modifier
                            .width(600.dp)
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0xFFF87171).copy(alpha = 0.1f),
                        elevation = 8.dp
                    ) {
                        Text(
                            "Thẻ chưa có dữ liệu",
                            color = Color(0xFFF87171),
                            modifier = Modifier.padding(24.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    else -> {
                        ui.AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (avatarImage != null) {
                                        Image(
                                            bitmap = avatarImage!!,
                                            contentDescription = "Ảnh đại diện",
                                            modifier = Modifier
                                                .size(96.dp)
                                                .clip(RoundedCornerShape(48.dp))
                                        )
                                        Spacer(Modifier.width(16.dp))
                                    }
                                    Column {
                                        Text(
                                            fullName.ifEmpty { "Chưa có thông tin" },
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ui.ParkColors.TextPrimary
                                        )
                                        Text(
                                            "CCCD: $idCard",
                                            fontSize = 14.sp,
                                            color = ui.ParkColors.TextSecondary
                                        )
                                    }
                                }

                                Divider(color = ui.ParkColors.Border, modifier = Modifier.padding(bottom = 12.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ui.InfoRow("Số CCCD", idCard.ifEmpty { "Chưa có dữ liệu" })
                                    ui.InfoRow("Họ và tên", fullName.ifEmpty { "Chưa có dữ liệu" })
                                    ui.InfoRow("Ngày sinh", dob.ifEmpty { "Chưa có dữ liệu" })
                                    ui.InfoRow("Địa chỉ", address.ifEmpty { "Chưa có dữ liệu" })
                                    ui.InfoRow("Số điện thoại", phone.ifEmpty { "Chưa có dữ liệu" })
                                    ui.InfoRow(
                                        "Số dư",
                                        "${formatVnAmount(balance).ifEmpty { "0" }} VNĐ",
                                        valueColor = ui.ParkColors.PrimaryDark
                                    )
                                    ui.InfoRow("Hạn sử dụng", expired.ifEmpty { "Chưa có dữ liệu" }, showDivider = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = Color(0xFF94A3B8),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}