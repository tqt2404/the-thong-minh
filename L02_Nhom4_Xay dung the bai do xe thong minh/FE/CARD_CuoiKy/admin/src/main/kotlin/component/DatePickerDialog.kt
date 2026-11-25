package component

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalDate
import java.time.YearMonth
import java.util.*

private val MONTHS_VI = listOf(
    "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
    "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
)
private val DAYS_HEADER_VI = listOf("CN","T2","T3","T4","T5","T6","T7")

@Composable
fun DatePickerDialog(
    initialDate: LocalDate = LocalDate.now().coerceAtMost(LocalDate.now()),
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.of(initialDate.year, initialDate.month)) }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var showYearPicker by remember { mutableStateOf(false) }

    Dialog(onCloseRequest = { onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = Color.White,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .width(IntrinsicSize.Min),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Thanh điều hướng tháng năm
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = {
                        currentMonth = currentMonth.minusMonths(1)
                        if (selectedDate.year != currentMonth.year || selectedDate.monthValue != currentMonth.monthValue) {
                            selectedDate = currentMonth.atDay(1)
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Tháng trước")
                    }
                    Box(
                        Modifier
                            .clickable { showYearPicker = true }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${MONTHS_VI[currentMonth.monthValue - 1]} ${currentMonth.year}",
                            fontSize = MaterialTheme.typography.h6.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )
                    }
                    IconButton(onClick = {
                        currentMonth = currentMonth.plusMonths(1)
                        if (selectedDate.year != currentMonth.year || selectedDate.monthValue != currentMonth.monthValue) {
                            selectedDate = currentMonth.atDay(1)
                        }
                    }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Tháng sau")
                    }
                }
                if (showYearPicker) {
                    YearPickerDialog(
                        initialYear = currentMonth.year,
                        onDismiss = { showYearPicker = false },
                        onYearSelected = { year ->
                            currentMonth = YearMonth.of(year, currentMonth.monthValue)
                            selectedDate = currentMonth.atDay(1)
                            showYearPicker = false
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Header thứ trong tuần
                Row(Modifier.fillMaxWidth()) {
                    DAYS_HEADER_VI.forEach {
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Lưới chọn ngày trong tháng (CUỘN/KHÔNG BỊ MẤT DÒNG)
                val firstDayOfMonth = currentMonth.atDay(1)
                val daysInMonth = currentMonth.lengthOfMonth()
                val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // Chủ nhật = 0
                val weeks = ((firstDayOfWeek + daysInMonth - 1) / 7) + 1

                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 440.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column {
                        var dayCounter = 1 - firstDayOfWeek
                        repeat(weeks) {
                            Row(Modifier.fillMaxWidth()) {
                                repeat(7) {
                                    if (dayCounter in 1..daysInMonth) {
                                        val thisDate = currentMonth.atDay(dayCounter)
                                        Button(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(1.dp)
                                                .then(
                                                    if (thisDate == selectedDate) Modifier.border(
                                                        BorderStroke(2.dp, Color(0xFF6366F1)), CircleShape
                                                    ) else Modifier
                                                ),
                                            onClick = {
                                                selectedDate = thisDate
                                                onDateSelected(thisDate)
                                                onDismiss()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = if (thisDate == selectedDate) Color(0xFF6366F1) else Color.White
                                            ),
                                            shape = CircleShape,
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                text = dayCounter.toString(),
                                                color = if (thisDate == selectedDate) Color.White else Color.Black,
                                                modifier = Modifier.align(Alignment.CenterVertically)
                                            )
                                        }
                                    } else {
                                        Spacer(
                                            Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                        )
                                    }
                                    dayCounter++
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Đóng")
                }
            }
        }
    }
}

@Composable
fun YearPickerDialog(
    initialYear: Int,
    onDismiss: () -> Unit,
    onYearSelected: (Int) -> Unit
) {
    val currentYear = LocalDate.now().year
    val years = (1900..currentYear).toList().reversed() // 2025,...,1900

    Dialog(onCloseRequest = onDismiss) {
        Surface(
            modifier = Modifier.padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(16.dp).width(220.dp)) {
                Text("Chọn năm", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.h6.fontSize)
                Spacer(Modifier.height(8.dp))

                // 👇 LazyColumn ở đây!
                LazyColumn(
                    modifier = Modifier.height(360.dp) // scrolls thực sự
                ) {
                    items(years) { year ->
                        OutlinedButton(
                            onClick = { onYearSelected(year) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = if (year == initialYear) Color(0xFF6366F1) else Color.White
                            ),
                            border = if (year == initialYear)
                                BorderStroke(2.dp, Color(0xFF6366F1))
                            else null,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("$year", color = if (year == initialYear) Color.White else Color.Black)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Đóng")
                }
            }
        }
    }
}