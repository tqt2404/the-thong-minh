package screen

import Navigator
import Screen
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.api.RetrofitInstance
import data.model.CardInfo
import data.repository.CardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import viewmodel.CreateCardViewModel
import utils.CardManager
import utils.encodeAvatarToByteArray
import viewmodel.CreateCardState
import javax.smartcardio.CommandAPDU
import java.util.regex.Pattern
import java.text.SimpleDateFormat
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import component.DatePickerDialog

fun isValidCCCD(input: String): Boolean = input.length == 13 && input.all { it.isDigit() }
fun isValidPhoneNumber(input: String): Boolean =
    input.length == 10 && input.all { it.isDigit() } && input.startsWith("0")
fun isValidPin(input: String): Boolean = input.length in 4..6 && input.all { it.isDigit() }
fun isValidDate(input: String): Boolean {
    if (!Pattern.matches("""\d{2}/\d{2}/\d{4}""", input)) return false
    return try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sdf.isLenient = false
        sdf.parse(input)
        true
    } catch (_: Exception) {
        false
    }
}

@Composable
fun CreateCardScreen(navigator: Navigator) {
    val cardRepository = remember { CardRepository(RetrofitInstance.cardApi) }
    val viewModel = remember { CreateCardViewModel(cardRepository) }

    var idCard by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var avatarImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var avatarPath by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }
    var hasCardInfo by remember { mutableStateOf(false) }

    // Quản lý hiện/ẩn date picker dialog
    var showDatePicker by remember { mutableStateOf(false) }

    // Đúng: Check xem thẻ đã thật sự có dữ liệu hay chưa, tương tự như màn xem info
    LaunchedEffect(Unit) {
        try {
            val infoResp = withContext(Dispatchers.IO) {
                // Gửi luôn lệnh lấy info
                val cmd = CommandAPDU(0x00, 0x05, 0x00, 0x00)
                val card = CardManager.activeCard
                card?.basicChannel?.transmit(cmd)
            }
            hasCardInfo = infoResp?.let { it.sw == 0x9000 && it.data.any { b -> b != 0.toByte() } } ?: false
        } catch (_: Exception) {
            hasCardInfo = false
        }
    }

    // Theo dõi trạng thái tạo thẻ
    LaunchedEffect(viewModel.state) {
        if (viewModel.state is CreateCardState.Success) {
            dialogMessage = "Tạo thẻ thành công!"
            showDialog = true
        } else if (viewModel.state is CreateCardState.Error) {
            dialogMessage = (viewModel.state as CreateCardState.Error).msg
            showDialog = true
        }
    }

    // Chỉ khi các trường hợp lệ, nút tạo mới enabled
    val isFormValid by derivedStateOf {
        isValidCCCD(idCard) &&
                isValidPhoneNumber(phoneNumber) &&
                isValidPin(pin) &&
                isValidDate(dateOfBirth) &&
                !hasCardInfo
    }

    // Dialog thông báo trạng thái (sau khi tạo thẻ)
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Thông báo") },
            text = { Text(dialogMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        navigator.navigateTo(Screen.FeatureMenu)
                        showDialog = false
                    }
                ) { Text("OK") }
            }
        )
    }

    // Hiện DatePicker dialog nếu true
    if (showDatePicker) {
        DatePickerDialog(
            initialDate = runCatching { LocalDate.parse(dateOfBirth, DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(LocalDate.now()),
            onDateSelected = { selectedDate ->
                dateOfBirth = selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            },
            onDismiss = { showDatePicker = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ui.ParkColors.Background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            ui.SectionHeader(
                title = "Cấp thẻ mới",
                subtitle = "Nhập đầy đủ thông tin chủ thẻ"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (hasCardInfo) {
                    ui.StatusBanner(
                        message = "Thẻ đã có thông tin. Bạn không thể đăng ký tạo thẻ mới trên thẻ này.",
                        isWarning = true
                    )
                } else {
                    // Nếu chưa có thông tin, render form như cũ
                    Row(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left column - Avatar
                        Card(
                            modifier = Modifier
                                .width(320.dp)
                                .height(550.dp),
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = Color(0xFF1E293B),
                            elevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "📸 Ảnh đại diện",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF1F5F9)
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Box(
                                    modifier = Modifier
                                        .size(220.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF334155),
                                                    Color(0xFF475569)
                                                )
                                            )
                                        )
                                        .border(
                                            width = 3.dp,
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF6366F1),
                                                    Color(0xFF8B5CF6)
                                                )
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatarImage != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = avatarImage!!,
                                            contentDescription = "Avatar",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(20.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "👤",
                                                fontSize = 90.sp
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Chưa có ảnh",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        val fileDialog = java.awt.FileDialog(
                                            null as java.awt.Frame?,
                                            "Chọn ảnh đại diện",
                                            java.awt.FileDialog.LOAD
                                        )
                                        fileDialog.file = "*.jpg;*.jpeg;*.png;*.bmp"
                                        fileDialog.isVisible = true

                                        fileDialog.file?.let { fileName ->
                                            val file = java.io.File(fileDialog.directory + fileName)
                                            avatarPath = file.absolutePath
                                            try {
                                                val bytes = file.readBytes()
                                                val skiaImage = Image.makeFromEncoded(bytes)
                                                avatarImage = org.jetbrains.skia.Bitmap.makeFromImage(skiaImage).asComposeImageBitmap()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = Color(0xFF6366F1)
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    elevation = ButtonDefaults.elevation(
                                        defaultElevation = 4.dp,
                                        pressedElevation = 8.dp
                                    )
                                ) {
                                    Text(
                                        "📁 Chọn ảnh",
                                        fontSize = 16.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
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
                                            text = "✓ Đã chọn ảnh",
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

                        // Right column - Form
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(550.dp),
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = Color(0xFF1E293B),
                            elevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(28.dp)
                            ) {
                                Text(
                                    text = "📋 Thông tin cá nhân",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF1F5F9)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            FormTextField(
                                                value = idCard,
                                                onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 13) idCard = it },
                                                label = "🪪 Số CCCD"
                                            )
                                            if (idCard.isNotEmpty() && !isValidCCCD(idCard)) {
                                                Text(
                                                    "CCCD phải đủ 13 số",
                                                    color = Color.Red,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(18.dp))

                                            FormTextField(
                                                value = fullName,
                                                onValueChange = { fullName = it },
                                                label = "👤 Họ và tên"
                                            )
                                            Spacer(modifier = Modifier.height(18.dp))

                                            // Form Date Picker Field (Compose)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showDatePicker = true }
                                            ) {
                                                OutlinedTextField(
                                                    value = dateOfBirth,
                                                    onValueChange = {},
                                                    label = {
                                                        Text("🎂 Ngày sinh (DD/MM/YYYY)")
                                                    },
                                                    readOnly = true,
                                                    enabled = false, // 👈 QUAN TRỌNG
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                                        disabledTextColor = Color(0xFFF1F5F9),
                                                        disabledBorderColor = Color(0xFF475569),
                                                        disabledLabelColor = Color(0xFF94A3B8),
                                                        backgroundColor = Color(0xFF0F172A)
                                                    ),
                                                    shape = RoundedCornerShape(12.dp),
                                                    singleLine = true
                                                )
                                            }

                                            if (dateOfBirth.isNotEmpty() && !isValidDate(dateOfBirth)) {
                                                Text(
                                                    "Ngày sinh không hợp lệ, định dạng dd/MM/yyyy",
                                                    color = Color.Red,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(18.dp))

                                            FormTextField(
                                                value = phoneNumber,
                                                onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 10) phoneNumber = it },
                                                label = "📱 Số điện thoại"
                                            )
                                            if (phoneNumber.isNotEmpty() && !isValidPhoneNumber(phoneNumber)) {
                                                val msg = if (!phoneNumber.startsWith("0"))
                                                    "Số điện thoại phải bắt đầu bằng số 0"
                                                else "Số điện thoại phải đủ 10 số"
                                                Text(
                                                    msg,
                                                    color = Color.Red,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(18.dp))
                                        }

                                        Spacer(modifier = Modifier.width(20.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            FormTextField(
                                                value = address,
                                                onValueChange = { address = it },
                                                label = "🏠 Địa chỉ"
                                            )
                                            Spacer(modifier = Modifier.height(18.dp))

                                            FormTextField(
                                                value = pin,
                                                onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 6) pin = it },
                                                label = "🔐 Mã PIN (4-6 số)",
                                                isPassword = true
                                            )
                                            if (pin.isNotEmpty() && !isValidPin(pin)) {
                                                Text(
                                                    "PIN chỉ từ 4 đến 6 số",
                                                    color = Color.Red,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Action buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { navigator.navigateTo(Screen.FeatureMenu) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFF87171)
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(2.dp, Color(0xFFF87171))
                                    ) {
                                        Text(
                                            "Hủy bỏ",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            try {
                                                val avatarBytes = if (avatarPath.isNotEmpty())
                                                    encodeAvatarToByteArray(avatarPath) else ByteArray(0)
                                                val cardInfo = CardInfo(
                                                    idCard = idCard,
                                                    fullName = fullName,
                                                    dateOfBirth = dateOfBirth,
                                                    address = address,
                                                    phoneNumber = phoneNumber,
                                                    pin = pin,
                                                    avatarBytes = avatarBytes
                                                )
                                                viewModel.createCard(cardInfo)
                                            } catch (e: Exception) {
                                                dialogMessage = "Lỗi: ${e.message}"
                                                showDialog = true
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = if (isFormValid) Color(0xFF10B981) else Color(0xFF4B5563)
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = ButtonDefaults.elevation(
                                            defaultElevation = 4.dp,
                                            pressedElevation = 8.dp
                                        ),
                                        enabled = isFormValid
                                    ) {
                                        Text(
                                            text = "✨ Tạo thẻ",
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
        }
        // Check nếu đang loading → hiện vòng xoay lên toàn màn hình
        if (viewModel.state is CreateCardState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99111B1E)), // Màu nền mờ overlay lên toàn màn
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF6366F1),
                        strokeWidth = 5.dp,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Đang tạo thẻ, vui lòng chờ...", color = Color.White)
                }
            }
        }
    }
}

//@Composable
//fun FormTextField(
//    value: String,
//    onValueChange: (String) -> Unit,
//    label: String,
//    modifier: Modifier = Modifier,
//    isPassword: Boolean = false
//) {
//    OutlinedTextField(
//        value = value,
//        onValueChange = onValueChange,
//        label = {
//            Text(
//                label,
//                color = Color(0xFF94A3B8),
//                fontSize = 14.sp
//            )
//        },
//        modifier = modifier.fillMaxWidth(),
//        colors = TextFieldDefaults.outlinedTextFieldColors(
//            textColor = Color(0xFFF1F5F9),
//            cursorColor = Color(0xFF8B5CF6),
//            focusedBorderColor = Color(0xFF8B5CF6),
//            unfocusedBorderColor = Color(0xFF475569),
//            backgroundColor = Color(0xFF0F172A)
//        ),
//        shape = RoundedCornerShape(12.dp),
//        singleLine = true,
//        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
//    )
//}