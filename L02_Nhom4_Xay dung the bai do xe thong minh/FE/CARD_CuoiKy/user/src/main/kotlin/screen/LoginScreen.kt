package screen

import Navigator
import Screen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import component.EntryExitTab
import component.LoginTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.CardManager
import data.repository.CardRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import java.util.Base64

@Composable
fun LoginScreen(navigator: Navigator, repository: CardRepository) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Vào/Ra khu vui chơi", "Đăng nhập")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ui.ParkColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(680.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ui.AppCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Cổng Công Viên", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ui.ParkColors.TextPrimary)
                        Text("Thẻ đã kết nối", fontSize = 13.sp, color = ui.ParkColors.Success, fontWeight = FontWeight.Medium)
                    }
                    ui.SecondaryButton(
                        text = "Rút thẻ",
                        onClick = {
                            CardManager.disconnect()
                            navigator.navigateTo(Screen.ConnectCard)
                        }
                    )
                }
            }

            ui.AppCard(modifier = Modifier.fillMaxWidth(), padding = 24.dp) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    backgroundColor = ui.ParkColors.SurfaceMuted,
                    contentColor = ui.ParkColors.PrimaryDark,
                    modifier = Modifier.fillMaxWidth(),
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = ui.ParkColors.Primary,
                            height = 3.dp
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontSize = 15.sp,
                                    color = if (selectedTab == index) ui.ParkColors.PrimaryDark else ui.ParkColors.TextSecondary,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                when (selectedTab) {
                    0 -> EntryExitTab(repository)
                    1 -> LoginTab(navigator)
                }
            }
        }
    }
}





