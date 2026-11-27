import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import data.api.RetrofitInstance
import data.repository.CardRepository
import screen.*
import ui.*
import utils.CardManager

@Composable
fun App() {
    val navigator = remember { Navigator() }
    val cardRepository = remember { CardRepository(RetrofitInstance.cardApi) }

    ParkTheme {
        Crossfade(
            targetState = navigator.currentScreen,
            animationSpec = tween(200),
            modifier = Modifier.fillMaxSize().background(ParkColors.Background)
        ) { screen ->
            when (screen) {
                Screen.ConnectCard -> ConnectCardScreen(navigator)
                Screen.Login -> LoginScreen(navigator, cardRepository)
                Screen.FeatureMenu,
                Screen.ViewInfo,
                Screen.PayMonthlyTicket,
                Screen.UserUpdatePin -> UserWorkspaceShell(
                    currentScreen = if (screen == Screen.FeatureMenu) Screen.ViewInfo else screen,
                    navigator = navigator,
                    cardRepository = cardRepository
                )
            }
        }
    }
}

@Composable
private fun UserWorkspaceShell(
    currentScreen: Screen,
    navigator: Navigator,
    cardRepository: CardRepository
) {
    Row(modifier = Modifier.fillMaxSize().background(ParkColors.Background)) {
        Column(
            modifier = Modifier.width(256.dp).fillMaxHeight().background(ParkColors.Surface)
                .border(1.dp, ParkColors.Border).padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(ParkColors.Primary),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.ConfirmationNumber, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Column {
                        Text("Thẻ Thành Viên", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ParkColors.TextPrimary)
                        Text("Công viên giải trí", fontSize = 12.sp, color = ParkColors.TextSecondary)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(ParkColors.SuccessBg).border(1.dp, ParkColors.SuccessBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.VerifiedUser, null, tint = ParkColors.Success, modifier = Modifier.size(18.dp))
                    Text("Thẻ đã xác thực", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ParkColors.Success)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(ParkColors.Border))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SidebarNavItem("Thẻ thành viên", Icons.Rounded.Badge, currentScreen == Screen.ViewInfo) { navigator.navigateTo(Screen.ViewInfo) }
                    SidebarNavItem("Vé tháng", Icons.Rounded.EventAvailable, currentScreen == Screen.PayMonthlyTicket) { navigator.navigateTo(Screen.PayMonthlyTicket) }
                    SidebarNavItem("Đổi mã PIN", Icons.Rounded.Password, currentScreen == Screen.UserUpdatePin) { navigator.navigateTo(Screen.UserUpdatePin) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Cổng vào / ra", { navigator.navigateTo(Screen.Login) }, Modifier.fillMaxWidth(), leadingIcon = Icons.Rounded.ArrowBack)
                SecondaryButton(
                    text = "Rút thẻ",
                    onClick = {
                        CardManager.disconnect()
                        navigator.navigateTo(Screen.ConnectCard)
                    },
                    leadingIcon = Icons.Rounded.Logout,
                    textColor = ParkColors.Error,
                    borderColor = ParkColors.ErrorBorder,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(28.dp)) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    (fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(180, easing = FastOutSlowInEasing)) { 18 })
                        .togetherWith(fadeOut(tween(100)))
                }
            ) { active ->
                when (active) {
                    Screen.PayMonthlyTicket -> PayMonthlyTicketScreen(navigator, cardRepository)
                    Screen.UserUpdatePin -> UserUpdatePinScreen(navigator, cardRepository)
                    else -> ViewInfoScreen(navigator)
                }
            }
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Thẻ Công Viên — Khách hàng",
        state = rememberWindowState(width = 1200.dp, height = 780.dp)
    ) {
        App()
    }
}