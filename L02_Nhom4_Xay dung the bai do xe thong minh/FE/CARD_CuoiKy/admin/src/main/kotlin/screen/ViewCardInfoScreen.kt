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
        title = "Thông tin thẻ",
        description = "Thông tin chủ thẻ, số dư và hạn vé tháng",
        onBack = { navigator.navigateTo(Screen.FeatureMenu) },
        gradientColor = ui.ParkColors.Primary,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isLoading -> CircularProgressIndicator(color = ui.ParkColors.Primary)
                    errorText != null -> ui.StatusBanner(errorText, isError = true)
                    noInfoOnCard == true -> ui.StatusBanner("Thẻ chưa có thông tin", isWarning = true)
                    else -> ui.AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (avatarImage != null) {
                                Image(
                                    bitmap = avatarImage!!,
                                    contentDescription = "Ảnh đại diện",
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(55.dp))
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            ui.InfoRow("Số CCCD", idCard)
                            ui.InfoRow("Họ và tên", fullName)
                            ui.InfoRow("Ngày sinh", dob)
                            ui.InfoRow("Địa chỉ", address)
                            ui.InfoRow("Số điện thoại", phone)
                            ui.InfoRow("Số dư", formatVnAmount(balance), valueColor = ui.ParkColors.PrimaryDark)
                            ui.InfoRow("Hạn vé tháng", expired, showDivider = false)
                        }
                    }
                }
            }
        }
    )
}

