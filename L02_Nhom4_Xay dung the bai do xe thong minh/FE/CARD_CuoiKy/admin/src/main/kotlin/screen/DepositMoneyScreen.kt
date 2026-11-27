package screen

import Navigator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import utils.CardManager
import utils.AuthPrefs
import data.repository.CardRepository
import data.model.SignSensitiveOperationRequest
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import util.formatVnAmount
import java.util.Base64
import javax.smartcardio.CommandAPDU

@Composable
fun DepositMoneyScreen(navigator: Navigator, repository: CardRepository) {
    var amount by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    var noInfoOnCard by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    // Topbar
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ui.ParkColors.Background)
    ) {
        ui.SectionHeader(title = "Nạp tiền tại quầy", subtitle = "Nạp tiền mặt vào số dư thẻ")

        // Check thẻ và cảnh báo chung
        LaunchedEffect(Unit) {
            isLoading = true
            noInfoOnCard = null
            errorText = null
            scope.launch {
                try {
                    val card = CardManager.activeCard
                    if (card == null) {
                        errorText = "Không kết nối được tới thẻ"
                    } else {
                        val apdu = CommandAPDU(0x00, 0x05, 0x00, 0x00)
                        val resp = withContext(Dispatchers.IO) {
                            card.basicChannel.transmit(apdu)
                        }
                        noInfoOnCard = resp.sw != 0x9000 || resp.data.all { it == 0.toByte() }
                    }
                } catch (e: Exception) {
                    errorText = "Lỗi đọc thẻ: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            if (isLoading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
            }
            if (errorText != null) {
                Text(errorText!!, color = Color.Red)
                Spacer(Modifier.height(16.dp))
            }
            if (noInfoOnCard == true) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0xFFF87171).copy(alpha = 0.1f),
                    elevation = 8.dp
                ) {
                    Text(
                        "Thẻ chưa có dữ liệu",
                        color = Color(0xFFF87171),
                        modifier = Modifier.padding(24.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DepositMoneyByCashCard(
                amount = amount,
                onAmountChange = { amount = it },
                isLoading = isLoading,
                errorText = errorText,
                successText = successText,
                scope = scope,
                repository = repository,
                setState = { isLoading = it.first; errorText = it.second; successText = it.third }
            )
        }
    }
}

@Composable
fun DepositMoneyByCashCard(
    amount: String,
    onAmountChange: (String) -> Unit,
    isLoading: Boolean,
    errorText: String?,
    successText: String?,
    scope: CoroutineScope,
    repository: CardRepository,
    setState: ((Triple<Boolean, String?, String?>) -> Unit)
) {
    ui.AppCard(
        modifier = Modifier.width(440.dp).padding(vertical = 16.dp)
    ) {
        Text("Nhập số tiền", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ui.ParkColors.TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { if (it.all { c -> c.isDigit() }) onAmountChange(it) },
            label = { Text("Số tiền (VNĐ)") },
            modifier = Modifier.fillMaxWidth(),
            colors = ui.lightTextFieldColors(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        ui.StatusBanner(errorText, isError = true)
        ui.StatusBanner(successText)
        if (errorText != null || successText != null) Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                    scope.launch {
                        setState(Triple(true, null, null))
                        try {
                            val amountValue = amount.toLongOrNull()
                            if (amountValue == null || amountValue <= 0) {
                                setState(Triple(false, "Số tiền không hợp lệ", null))
                                return@launch
                            }

                            // Get current money from card
                            val currentMoney = CardManager.getMoneyFromCard()
                            val newMoney = currentMoney + amountValue

                            // Get signature from backend
                            val token = AuthPrefs.getToken()
                            val dataJson = """{"newMoney":"${formatVnAmount(newMoney.toString())}"}"""
                            val response = repository.signSensitiveOperation(
                                token,
                                SignSensitiveOperationRequest(
                                    operation = "update_money",
                                    data = dataJson
                                )
                            )

                            val messageBytes = Base64.getDecoder().decode(response.message)
                            val sigBytes = Base64.getDecoder().decode(response.signature)

                            // Update money on card
                            CardManager.updateMoneyOnCard(messageBytes, sigBytes, newMoney.toString())

                            setState(Triple(false, null, "Nạp tiền thành công! Số dư: ${formatVnAmount(newMoney.toString())} VNĐ"))
                            onAmountChange("")
                        } catch (e: Exception) {
                            setState(Triple(false, "Lỗi: ${e.message}", null))
                        }
                    }
                },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = ui.ParkColors.Primary),
            shape = RoundedCornerShape(10.dp),
            enabled = amount.isNotEmpty()
        ) {
            Text("Nạp tiền", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}
