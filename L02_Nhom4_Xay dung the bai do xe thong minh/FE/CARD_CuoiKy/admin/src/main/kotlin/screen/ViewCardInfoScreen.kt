package screen

import Navigator
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import utils.CardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.smartcardio.CommandAPDU
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.Bitmap
import util.formatVnAmount

@Composable
fun ViewCardInfoScreen(navigator: Navigator) {
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var noInfoOnCard by remember { mutableStateOf<Boolean?>(null) }

    // Các trường thông tin
    var idCard by remember { mutableStateOf("Chưa có dữ liệu") }
    var fullName by remember { mutableStateOf("Chưa có dữ liệu") }
    var dob by remember { mutableStateOf("Chưa có dữ liệu") }
    var address by remember { mutableStateOf("Chưa có dữ liệu") }
    var phone by remember { mutableStateOf("Chưa có dữ liệu") }
    var balance by remember { mutableStateOf("0 VNĐ") }
    var expired by remember { mutableStateOf("Chưa có dữ liệu") }
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
        val apdu = CommandAPDU(0x00, 0x11, 0x00, 0x00)
        val resp = withContext(Dispatchers.IO) {
            card.basicChannel.transmit(apdu)
        }

        println("=== AVATAR DEBUG ===")
        println("SW: ${resp.sw.toString(16)}")
        println("Size: ${resp.data.size}")
        println("First 30 bytes: ${resp.data.take(30).joinToString(" ") { "%02X".format(it) }}")
        println("Last 30 bytes: ${resp.data.takeLast(30).joinToString(" ") { "%02X".format(it) }}")

        if (resp.sw != 0x9000 || resp.data.isEmpty()) return null

        // Kiểm tra JPEG end marker (FF D9)
        if (resp.data.size >= 2) {
            val lastTwo = resp.data.takeLast(2)
            if (lastTwo[0] == 0xFF.toByte() && lastTwo[1] == 0xD9.toByte()) {
                println(" JPEG end marker found")
            } else {
                println(" JPEG end marker NOT found, last 2 bytes: ${lastTwo.joinToString(" ") { "%02X".format(it) }}")
            }
        }

        // Kiểm tra header
        if (resp.data[0] == 0xFF.toByte() &&
            resp.data[1] == 0xD8.toByte() &&
            resp.data[2] == 0xFF.toByte()) {
            println(" JPEG header detected")
        }

        return try {
            val skiaImg = SkiaImage.makeFromEncoded(resp.data)
            Bitmap.makeFromImage(skiaImg).asImageBitmap()
        } catch (e: Exception) {
            println(" Decode failed: ${e.message}")
            e.printStackTrace()
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
                        idCard = cardInfo["idCard"] ?: "Chưa có dữ liệu"
                        fullName = cardInfo["fullName"] ?: "Chưa có dữ liệu"
                        dob = cardInfo["dob"] ?: "Chưa có dữ liệu"
                        address = cardInfo["address"] ?: "Chưa có dữ liệu"
                        phone = cardInfo["phone"] ?: "Chưa có dữ liệu"
                        balance = cardInfo["balance"]?.let { "$it VNĐ" } ?: "0 VNĐ"
                        expired = cardInfo["expired"] ?: "Chưa có dữ liệu"

                        // Đọc thêm ảnh đại diện
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
        title = "Xem chi tiết",
        description = "Xem thông tin chi tiết thẻ",
        onBack = { navigator.navigateTo(Screen.FeatureMenu) },
        gradientColor = Color(0xFFF59E0B),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator()
                    }
                    errorText != null -> {
                        Text(errorText!!, color = Color.Red)
                    }
                    noInfoOnCard == true -> {
                        Card(
                            modifier = Modifier
                                .width(700.dp)
                                .padding(vertical = 16.dp)
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = Color(0xFF1E293B),
                            elevation = 8.dp
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    "Thẻ chưa có thông tin",
                                    color = Color(0xFFF87171),
                                    style = MaterialTheme.typography.subtitle1
                                )
                            }
                        }
                    }
                    else -> {
                        Card(
                            modifier = Modifier
                                .width(700.dp)
                                //.padding(vertical = 16.dp)
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = Color(0xFF1E293B),
                            elevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Ảnh đại diện nếu có
                                if (avatarImage != null) {
                                    Image(
                                        bitmap = avatarImage!!,
                                        contentDescription = "Ảnh đại diện",
                                        modifier = Modifier
                                            .size(150.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .padding(bottom = 12.dp)
                                    )
                                } else {
                                    Text(
                                        "👤",
                                        fontSize = 56.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                InfoRow("CCCD", idCard)
                                Divider(color = Color(0xFF475569), modifier = Modifier.padding(vertical = 10.dp))
                                InfoRow("Họ tên", fullName)
                                Divider(color = Color(0xFF475569), modifier = Modifier.padding(vertical = 10.dp))
                                InfoRow("Ngày sinh", dob)
                                Divider(color = Color(0xFF475569), modifier = Modifier.padding(vertical = 10.dp))
                                InfoRow("Địa chỉ", address)
                                Divider(color = Color(0xFF475569), modifier = Modifier.padding(vertical = 10.dp))
                                InfoRow("Số điện thoại", phone)
                                Divider(color = Color(0xFF475569), modifier = Modifier.padding(vertical = 10.dp))
                                InfoRow("Số dư", formatVnAmount(balance))
                                Divider(color = Color(0xFF475569), modifier = Modifier.padding(vertical = 10.dp))
                                InfoRow("Hết hạn", expired)
                            }
                        }
                    }
                }
            }
        }
    )
}

