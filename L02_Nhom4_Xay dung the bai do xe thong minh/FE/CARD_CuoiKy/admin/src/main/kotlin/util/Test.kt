package util

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Text
import androidx.compose.material.OutlinedTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import component.DatePickerDialog // Đảm bảo đúng path import của bạn!
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun main() = singleWindowApplication(title = "Test DatePickerDialog") {
    var showPicker by remember { mutableStateOf(true) }
    var date by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().padding(48.dp)) {
        OutlinedTextField(
            value = date,
            onValueChange = {},
            label = { Text("Ngày sinh (dd/MM/yyyy)") },
            modifier = Modifier
                .width(220.dp)
                .clickable { showPicker = true },
            readOnly = true
        )

        if (showPicker) {
            DatePickerDialog(
                onDateSelected = {
                    date = it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    showPicker = false
                },
                onDismiss = { showPicker = false }
            )
        }
    }
}