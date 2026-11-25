import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import data.api.RetrofitInstance
import data.repository.LoginRepository
import screen.*
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

    MaterialTheme(
        colors = darkColors(
            primary = Color(0xFF6366F1),
            primaryVariant = Color(0xFF4F46E5),
            secondary = Color(0xFF8B5CF6),
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B),
        )
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize()
        ) {
            // Nội dung màn hình
            when (navigator.currentScreen) {
                Screen.Login -> LoginScreen(loginScreenModel, navigator)
                Screen.ConnectCard -> ConnectCardScreen(navigator)
                Screen.FeatureMenu -> FeatureMenuScreen(navigator)
                Screen.Dashboard -> UnlockCardScreen(navigator, cardRepository)
                Screen.CreateCard -> CreateCardScreen(navigator)
                Screen.ManageCard -> ManageCardScreen(navigator, cardRepository)
                Screen.DepositMoney -> DepositMoneyScreen(navigator, cardRepository)
                Screen.UpdatePin -> UpdatePinScreen(navigator, cardRepository)
                Screen.UpdateExpired -> UpdateExpiredScreen(navigator,cardRepository)
                Screen.ViewCardInfo -> ViewCardInfoScreen(navigator)
            }

            // Hiệu ứng tuyết rơi nằm trên cùng
//            SnowfallEffect(
//
//            )
//            LightSnowfallEffect()
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = {
            CardManager.disconnect()
            exitApplication()
        },
        title = "Park Card Admin",
        state = rememberWindowState(width = 1400.dp, height = 900.dp)
    ) {
        App()
    }
}