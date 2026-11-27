package screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.ParkColors
import ui.SectionHeader

@Composable
fun FeatureScreenTemplate(
    title: String,
    description: String,
    onBack: () -> Unit,
    gradientColor: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ParkColors.Background)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SectionHeader(title = title, subtitle = description)
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}
