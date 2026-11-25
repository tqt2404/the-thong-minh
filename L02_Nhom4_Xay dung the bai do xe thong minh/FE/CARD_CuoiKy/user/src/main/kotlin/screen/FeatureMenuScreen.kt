package screen

import Navigator
import Screen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import utils.CardManager

@Composable
fun FeatureMenuScreen(navigator: Navigator) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Thẻ của tôi",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Quản lý thông tin cá nhân",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Button(
                    onClick = {
                        CardManager.disconnect()
                        navigator.navigateTo(Screen.ConnectCard)
                    },
                    modifier = Modifier.height(44.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF475569)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Đăng xuất", color = Color.White, fontSize = 14.sp)
                }
            }

            // Các chức năng user - 3 chức năng
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Hàng 1: Xem thông tin + Thanh toán
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    UserFeatureButton(
                        title = "Xem thông tin cá nhân",
                        description = "Xem thông tin đầy đủ trên thẻ của bạn",
                        emoji = "👤",
                        color = Color(0xFF6366F1),
                        modifier = Modifier.weight(1f),
                        onClick = { navigator.navigateTo(Screen.ViewInfo) }
                    )

                    UserFeatureButton(
                        title = "Thanh toán vé tháng",
                        description = "Thanh toán và gia hạn vé tháng khu vui chơi",
                        emoji = "💳",
                        color = Color(0xFFEC4899),
                        modifier = Modifier.weight(1f),
                        onClick = { navigator.navigateTo(Screen.PayMonthlyTicket) }
                    )
                }

                // Hàng 2: Đổi PIN
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    UserFeatureButton(
                        title = "Đổi mã PIN",
                        description = "Thay đổi mã PIN bảo mật của thẻ",
                        emoji = "🔐",
                        color = Color(0xFF06B6D4),
                        modifier = Modifier.weight(1f),
                        onClick = { navigator.navigateTo(Screen.UserUpdatePin) }
                    )

                    // Placeholder để giữ layout cân đối
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun UserFeatureButton(
    title: String,
    description: String,
    emoji: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxSize(),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.elevation(0.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(color.copy(alpha = 0.15f), color.copy(alpha = 0.05f))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(
                    text = emoji,
                    fontSize = 48.sp
                )
                Text(
                    title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    description,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}