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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import data.repository.LoginRepository
import screen.*
import ui.*
import utils.CardManager
import viewmodel.LoginScreenModel
import viewmodel.ManageCardViewModel

sealed class Screen {
    object Login : Screen()
    object ConnectCard : Screen()
    object FeatureMenu : Screen()
    object Dashboard : Screen()
    object CreateCard : Screen()
    object ManageCard : Screen()
    object DepositMoney : Screen()
    object UpdatePin : Screen()
    object UpdateExpired : Screen()
    object ViewCardInfo : Screen()
}

class Navigator {
    var currentScreen by mutableStateOf<Screen>(
        if (utils.AuthPrefs.isLoggedIn()) Screen.ConnectCard else Screen.Login
    )

    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }
}

@Composable
fun App() {
    val navigator = remember { Navigator() }
    val loginRepository = remember { LoginRepository(RetrofitInstance.loginApi) }
    val loginScreenModel = remember { LoginScreenModel(loginRepository) }
    val cardRepository = remember { data.repository.CardRepository(RetrofitInstance.cardApi) }
    val manageCardViewModel = remember { ManageCardViewModel(cardRepository) }

    ParkTheme {
        Crossfade(
            targetState = navigator.currentScreen,
            animationSpec = tween(200),
            modifier = Modifier.fillMaxSize().background(ParkColors.Background)
        ) { screen ->
            when (screen) {
                Screen.Login -> LoginScreen(loginScreenModel, navigator)
                Screen.ConnectCard -> ConnectCardScreen(navigator)
                Screen.FeatureMenu,
                Screen.ViewCardInfo,
                Screen.CreateCard,
                Screen.ManageCard,
                Screen.DepositMoney,
                Screen.UpdateExpired,
                Screen.UpdatePin,
                Screen.Dashboard -> AdminWorkspaceShell(
                    currentScreen = if (screen == Screen.FeatureMenu) Screen.ViewCardInfo else screen,
                    navigator = navigator,
                    cardRepository = cardRepository
                )
            }
        }
    }
}

@Composable
private fun AdminWorkspaceShell(
    currentScreen: Screen,
    navigator: Navigator,
    cardRepository: data.repository.CardRepository
) {
    Row(modifier = Modifier.fillMaxSize().background(ParkColors.Background)) {
        Column(
            modifier = Modifier.width(260.dp).fillMaxHeight().background(ParkColors.Surface)
                .border(1.dp, ParkColors.Border).padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(ParkColors.Primary),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.CreditCard, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Column {
                        Text("Thẻ Công Viên", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ParkColors.TextPrimary)
                        Text("Quản trị viên", fontSize = 12.sp, color = ParkColors.TextSecondary)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(ParkColors.SuccessBg).border(1.dp, ParkColors.SuccessBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = ParkColors.Success, modifier = Modifier.size(18.dp))
                    Text("Thẻ đã kết nối", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ParkColors.Success)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(ParkColors.Border))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SidebarNavItem("Thông tin thẻ", Icons.Rounded.Badge, currentScreen == Screen.ViewCardInfo) { navigator.navigateTo(Screen.ViewCardInfo) }
                    SidebarNavItem("Cấp thẻ mới", Icons.Rounded.PersonAdd, currentScreen == Screen.CreateCard) { navigator.navigateTo(Screen.CreateCard) }
                    SidebarNavItem("Cập nhật thông tin", Icons.Rounded.EditNote, currentScreen == Screen.ManageCard) { navigator.navigateTo(Screen.ManageCard) }
                    SidebarNavItem("Nạp tiền tại quầy", Icons.Rounded.AccountBalanceWallet, currentScreen == Screen.DepositMoney) { navigator.navigateTo(Screen.DepositMoney) }
                    SidebarNavItem("Gia hạn vé tháng", Icons.Rounded.EventAvailable, currentScreen == Screen.UpdateExpired) { navigator.navigateTo(Screen.UpdateExpired) }
                    SidebarNavItem("Đổi PIN (Quản trị)", Icons.Rounded.Password, currentScreen == Screen.UpdatePin) { navigator.navigateTo(Screen.UpdatePin) }
                    SidebarNavItem("Mở khóa thẻ", Icons.Rounded.LockOpen, currentScreen == Screen.Dashboard) { navigator.navigateTo(Screen.Dashboard) }
                }
            }
            SecondaryButton(
                text = "Ngắt kết nối thẻ",
                onClick = {
                    CardManager.disconnect()
                    navigator.navigateTo(Screen.ConnectCard)
                },
                leadingIcon = Icons.Rounded.LinkOff,
                textColor = ParkColors.Error,
                borderColor = ParkColors.ErrorBorder,
                modifier = Modifier.fillMaxWidth()
            )
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
                    Screen.Dashboard -> UnlockCardScreen(navigator, cardRepository)
                    Screen.CreateCard -> CreateCardScreen(navigator)
                    Screen.ManageCard -> ManageCardScreen(navigator, cardRepository)
                    Screen.DepositMoney -> DepositMoneyScreen(navigator, cardRepository)
                    Screen.UpdatePin -> UpdatePinScreen(navigator, cardRepository)
                    Screen.UpdateExpired -> UpdateExpiredScreen(navigator, cardRepository)
                    else -> ViewCardInfoScreen(navigator)
                }
            }
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = {
            CardManager.disconnect()
            exitApplication()
        },
        title = "Thẻ Công Viên — Quản trị viên",
        state = rememberWindowState(width = 1200.dp, height = 780.dp)
    ) {
        App()
    }
}