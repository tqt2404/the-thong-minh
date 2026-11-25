import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import data.api.RetrofitInstance
import data.repository.CardRepository
import screen.*

@Composable
fun App() {
    val navigator = remember { Navigator() }
    val cardRepository = remember { CardRepository(RetrofitInstance.cardApi) }

    MaterialTheme(
        colors = darkColors(
            primary = Color(0xFF6366F1),
            primaryVariant = Color(0xFF4F46E5),
            secondary = Color(0xFF8B5CF6),
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B),
        )
    ) {
        Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize()
        ) {
            when (navigator.currentScreen) {
                Screen.ConnectCard -> ConnectCardScreen(navigator)
                Screen.Login -> LoginScreen(navigator, cardRepository)
                Screen.FeatureMenu -> FeatureMenuScreen(navigator)
                Screen.ViewInfo -> ViewInfoScreen(navigator)
                Screen.PayMonthlyTicket -> PayMonthlyTicketScreen(navigator, cardRepository)
                Screen.UserUpdatePin -> UserUpdatePinScreen(navigator, cardRepository)
            }
//            SnowfallEffect(
//
//            )
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Park Card - User"
    ) {
        App()
    }
}