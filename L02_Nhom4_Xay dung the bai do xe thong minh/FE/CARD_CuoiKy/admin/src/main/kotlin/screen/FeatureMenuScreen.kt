package screen

import Navigator
import Screen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
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
                Text(
                    "Quản lý thẻ",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Button(
                    onClick = {
                        CardManager.disconnect()
                        navigator.navigateTo(Screen.ConnectCard)
                              },
                    modifier = Modifier.height(44.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF475569)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Ngắt kết nối", color = Color.White, fontSize = 14.sp)
                }
            }

            // Các chức năng (admin features)
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    AdminFeatureButton(
                        title = "Tạo thẻ mới",
                        description = "Tạo thẻ cho người dùng mới",
                        color = Color(0xFF6366F1),
                        modifier = Modifier.weight(1f),
                        onClick = { navigator.navigateTo(Screen.CreateCard) }
                    )
                    AdminFeatureButton(
                        title = "Cập nhật thông tin",
                        description = "Sửa thông tin thẻ người dùng",
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier.weight(1f),
                        onClick = { navigator.navigateTo(Screen.ManageCard) }
                    )
                    AdminFeatureButton(
                        title = "Nạp tiền",
                        description = "Nạp tiền vào tài khoản",
                        color = Color(0xFFEC4899),
                        modifier = Modifier.weight(1f),
                        onClick = { navigator.navigateTo(Screen.DepositMoney) }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    AdminFeatureButton(
                        title = "Cấp lại mã PIN",
                        description = "Đặt lại mã PIN người dùng",
                        color = Color(0xFF06B6D4),
                        modifier = Modifier.weight(1f),
                        onClick = { navigator.navigateTo(Screen.UpdatePin) }
                    )
//                    AdminFeatureButton(
//                        title = "Cập nhật hạn",
//                        description = "Gia hạn hoặc xóa hạn thẻ",
//                        color = Color(0xFF10B981),
//                        modifier = Modifier.weight(1f),
//                        onClick = { navigator.navigateTo(Screen.UpdateExpired) }
//                    )
                    AdminFeatureButton(
                        title = "Mở khóa thẻ",
                        description = "Mở khóa thẻ sau 5 lần nhập sai",
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier.weight(1f),
                        onClick = { navigator.navigateTo(Screen.Dashboard) }
                    )
                    AdminFeatureButton(
                        title = "Xem chi tiết",
                        description = "Xem thông tin chi tiết thẻ",
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f),
                        onClick = { navigator.navigateTo(Screen.ViewCardInfo) }
                    )
                }
//                Spacer(modifier = Modifier.height(20.dp))
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(150.dp),
//                    horizontalArrangement = Arrangement.spacedBy(20.dp)
//                ) {
//                    AdminFeatureButton(
//                        title = "Mở khóa thẻ",
//                        description = "Mở khóa thẻ sau 5 lần nhập sai",
//                        color = Color(0xFF8B5CF6),
//                        modifier = Modifier.weight(1f),
//                        onClick = { navigator.navigateTo(Screen.Dashboard) }
//                    )
//                    Spacer(modifier = Modifier.weight(1f))
//                    Spacer(modifier = Modifier.weight(1f))
//                }
            }
        }
    }
}

// Button này giữ nguyên code của bạn hoặc tách riêng file cũng được
@Composable
fun AdminFeatureButton(
    title: String,
    description: String,
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
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(
                    text = "•",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(0.95f)
                )
            }
        }
    }
}