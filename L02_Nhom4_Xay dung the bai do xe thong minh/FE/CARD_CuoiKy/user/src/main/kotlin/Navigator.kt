import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen {
    ConnectCard,
    Login,
    FeatureMenu,
    ViewInfo,
    PayMonthlyTicket,
    UserUpdatePin
}

class Navigator {
    var currentScreen by mutableStateOf(Screen.ConnectCard)
        private set

    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }
}