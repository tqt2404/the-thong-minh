package screen

import Navigator
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import component.DatePickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import data.repository.CardRepository
import utils.CardManager
import utils.AuthPrefs
import data.model.SignSensitiveOperationRequest
import org.jetbrains.skia.Image
import utils.encodeAvatarToByteArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter



@Composable
fun ManageCardScreen(navigator: Navigator, cardRepository: CardRepository) {
    var showDatePicker by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var noInfoOnCard by remember { mutableStateOf<Boolean?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    var idCard by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var avatarImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var avatarPath by remember { mutableStateOf("") }
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }

    val scope = rememberCoroutineScope()

    // Cờ enable cho cập nhật thông tin
    val isUpdateInfoEnabled by derivedStateOf {
        isValidPhoneNumber(phoneNumber)
                && fullName.isNotBlank()
                && dateOfBirth.isNotBlank()
                && address.isNotBlank()
    }

    if (showDatePicker) {
        DatePickerDialog(
            initialDate = runCatching {
                LocalDate.parse(dateOfBirth, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            }.getOrDefault(LocalDate.now()),
            onDateSelected = { selectedDate ->
                dateOfBirth = selectedDate.format(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy")
                )
            },
            onDismiss = { showDatePicker = false }
        )
    }

    suspend fun readCardInfo() {
        isLoading = true
        errorText = null
        updateStatus = null
        try {
            val card = CardManager.activeCard ?: run {
                errorText = "Không kết nối được tới thẻ"
                return
            }
            val apdu = javax.smartcardio.CommandAPDU(0x00, 0x05, 0x00, 0x00)
            val resp = withContext(Dispatchers.IO) { card.basicChannel.transmit(apdu) }
            if (resp.sw != 0x9000 || resp.data.isEmpty() || resp.data.all { it == 0.toByte() }) {
                noInfoOnCard = true
                return
            }
            noInfoOnCard = false
            val buf = resp.data
            var offset = 0
            fun getField(): String {
                val len = buf[offset].toInt()
                val v = buf.copyOfRange(offset + 1, offset + 1 + len).toString(Charsets.UTF_8)
                offset += 1 + len
                return v
            }
            idCard = getField()
            fullName = getField()
            dateOfBirth = getField()
            address = getField()
            phoneNumber = getField()

            val apduAvatar = javax.smartcardio.CommandAPDU(0x00, 0x11, 0x00, 0x00)
            val respAvatar = withContext(Dispatchers.IO) { card.basicChannel.transmit(apduAvatar) }
            avatarImage = try {
                if (respAvatar.sw == 0x9000 && respAvatar.data.isNotEmpty()) {
                    val skiaImg = Image.makeFromEncoded(respAvatar.data)
                    org.jetbrains.skia.Bitmap.makeFromImage(skiaImg).asComposeImageBitmap()
                } else null
            } catch (e: Exception) { null }
        } catch (e: Exception) {
            errorText = "Lỗi đọc thẻ: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    fun doUpdateInfo() {
        scope.launch {
            isLoading = true
            errorText = null
            updateStatus = null
            try {
                val token = AuthPrefs.getToken()
                val dataJson = """{"fullName":"$fullName","dateOfBirth":"$dateOfBirth","address":"$address","phoneNumber":"$phoneNumber"}"""

                val req = SignSensitiveOperationRequest(
                    operation = "update_info",
                    data = dataJson
                )

                val sigRes = cardRepository.signSensitiveOperation(token, req)

                val messageBytes = java.util.Base64.getDecoder().decode(sigRes.message)
                val sigBytes = java.util.Base64.getDecoder().decode(sigRes.signature)

                val buffer = CardManager.buildUpdateInfoBuffer(
                    msgBytes = messageBytes,
                    sigBytes = sigBytes,
                    fullName = fullName,
                    dateOfBirth = dateOfBirth,
                    address = address,
                    phoneNumber = phoneNumber
                )
                CardManager.updateCardInfoToCard(buffer)
                updateStatus = "Cập nhật thông tin thành công!"
            } catch (e: Exception) {
                errorText = "Lỗi cập nhật thông tin: ${e.message}"
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun doUpdateAvatar() {
        scope.launch {
            isLoading = true
            errorText = null
            updateStatus = null
            try {
                val avatarData = avatarBytes
                if (avatarData == null || avatarData.isEmpty()) {
                    throw Exception("Chưa chọn ảnh.")
                }
                if (avatarData.size > 8192) {
                    throw Exception("Kích thước ảnh vượt quá 8192 bytes!")
                }
                CardManager.updateAvatarOnCard(avatarData)
                updateStatus = "Cập nhật ảnh đại diện thành công!"
            } catch (e: Exception) {
                errorText = "Lỗi cập nhật ảnh: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch { readCardInfo() }
    }

    FeatureScreenTemplate(
        title = "Cập nhật thẻ",
        description = "Sửa và cập nhật thông tin chủ thẻ",
        onBack = { navigator.navigateTo(Screen.FeatureMenu) },
        gradientColor = Color(0xFF10B981),
        content = {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                when {
                    isLoading -> CircularProgressIndicator()
                    errorText != null -> Text(errorText!!, color = Color.Red)
                    noInfoOnCard == true -> {
                        Card(
                            backgroundColor = Color(0xFFF87171).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                "Thẻ chưa có dữ liệu",
                                color = Color(0xFFF87171),
                                modifier = Modifier.padding(36.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Card(
                                modifier = Modifier.width(300.dp).height(520.dp),
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = ui.ParkColors.Surface,
                                border = BorderStroke(1.dp, ui.ParkColors.Border),
                                elevation = 0.dp
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Ảnh đại diện",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ui.ParkColors.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Box(
                                        modifier = Modifier.size(200.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(ui.ParkColors.SurfaceMuted)
                                            .border(1.dp, ui.ParkColors.Border, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (avatarImage != null) {
                                            Image(
                                                bitmap = avatarImage!!,
                                                contentDescription = "Avatar",
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                "Chưa có ảnh",
                                                fontSize = 14.sp,
                                                color = ui.ParkColors.TextSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = {
                                            val fileDialog = java.awt.FileDialog(null as? java.awt.Frame, "Chọn ảnh", java.awt.FileDialog.LOAD)
                                            fileDialog.file = "*.jpg;*.jpeg;*.png;*.bmp"
                                            fileDialog.isVisible = true
                                            fileDialog.file?.let { fileName ->
                                                val file = java.io.File(fileDialog.directory + fileName)
                                                avatarPath = file.absolutePath
                                                try {
                                                    val bytes: ByteArray? = try {
                                                        encodeAvatarToByteArray(avatarPath, maxSize = 8192)
                                                    } catch (ex: Exception) {
                                                        errorText = "Ảnh vượt quá 8192 bytes!"
                                                        null
                                                    }
                                                    if (bytes != null) {
                                                        avatarBytes = bytes
                                                        val skiaImage = Image.makeFromEncoded(bytes)
                                                        avatarImage = org.jetbrains.skia.Bitmap.makeFromImage(skiaImage).asComposeImageBitmap()
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    errorText = "Lỗi: ${e.message}"
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF6366F1)),
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = ButtonDefaults.elevation(4.dp, 8.dp)
                                    ) {
                                        Text(
                                            "📁 Chọn ảnh",
                                            fontSize = 16.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = { doUpdateAvatar() },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF10B981)),
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = ButtonDefaults.elevation(4.dp, 8.dp)
                                    ) {
                                        Text(
                                            "🖼️ Cập nhật ảnh",
                                            fontSize = 16.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (avatarPath.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Card(
                                            backgroundColor = Color(0xFF10B981).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "✓ Đã chọn ảnh",
                                                fontSize = 14.sp,
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(24.dp))
                            Card(
                                modifier = Modifier.weight(1f).height(550.dp),
                                shape = RoundedCornerShape(20.dp),
                                backgroundColor = Color(0xFF1E293B),
                                elevation = 8.dp
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(28.dp)
                                ) {
                                    Text(
                                        "📋 Thông tin cá nhân",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF1F5F9)
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Column(
                                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                FormTextField(value = idCard, onValueChange = {}, label = "🪪 Số CCCD")
                                                Spacer(modifier = Modifier.height(18.dp))
                                                FormTextField(value = fullName, onValueChange = { fullName = it }, label = "👤 Họ và tên")
                                                Spacer(modifier = Modifier.height(18.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { showDatePicker = true }
                                                ) {
                                                    OutlinedTextField(
                                                        value = dateOfBirth,
                                                        onValueChange = {},
                                                        label = {
                                                            Text(
                                                                "🎂 Ngày sinh (DD/MM/YYYY)",
                                                                fontSize = 14.sp
                                                            )
                                                        },
                                                        readOnly = true,
                                                        enabled = false,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                                            disabledTextColor = Color(0xFFF1F5F9),
                                                            disabledBorderColor = Color(0xFF475569),
                                                            disabledLabelColor = Color(0xFF94A3B8),
                                                            backgroundColor = Color(0xFF334155)
                                                        ),
                                                        shape = RoundedCornerShape(12.dp),
                                                        singleLine = true
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(18.dp))
                                                FormTextField(
                                                    value = phoneNumber,
                                                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 10) phoneNumber = it },
                                                    label = "📱 Số điện thoại"
                                                )
                                                if (phoneNumber.isNotEmpty() && !isValidPhoneNumber(phoneNumber)) {
                                                    val msg = when {
                                                        !phoneNumber.startsWith("0") -> "Số điện thoại phải bắt đầu bằng số 0"
                                                        phoneNumber.length != 10 -> "Số điện thoại phải đủ 10 số"
                                                        else -> "Số điện thoại không hợp lệ"
                                                    }
                                                    Text(
                                                        msg,
                                                        color = Color.Red,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(20.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                FormTextField(value = address, onValueChange = { address = it }, label = "🏠 Địa chỉ")
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
                                        OutlinedButton(
                                            onClick = { scope.launch { readCardInfo() } },
                                            modifier = Modifier.weight(1f).height(54.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(2.dp, Color(0xFFF87171))
                                        ) {
                                            Text(
                                                "Đọc lại",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Button(
                                            onClick = { doUpdateInfo() },
                                            enabled = isUpdateInfoEnabled,
                                            modifier = Modifier.weight(1f).height(54.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = if (isUpdateInfoEnabled) Color(0xFF10B981) else Color(0xFF4B5563)
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            elevation = ButtonDefaults.elevation(4.dp, 8.dp)
                                        ) {
                                            Text(
                                                "✏️ Cập nhật",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    updateStatus?.let {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(it, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                    }
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
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        colors = ui.lightTextFieldColors(),
        shape = RoundedCornerShape(10.dp),
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
    )
}