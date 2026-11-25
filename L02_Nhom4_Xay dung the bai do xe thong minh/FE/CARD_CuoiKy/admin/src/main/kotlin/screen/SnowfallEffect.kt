package screen

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.isActive
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState

data class Snowflake(
    var x: Float,
    var y: Float,
    var size: Float,
    var speed: Float,
    var swingAmplitude: Float,
    var swingFrequency: Float,
    var swingOffset: Float,
    var opacity: Float
)

@Composable
fun SnowfallEffect(
    modifier: Modifier = Modifier,
    numberOfSnowflakes: Int = 80,
    snowColor: Color = Color.White
) {
    var screenWidth by remember { mutableStateOf(1080f) }
    var screenHeight by remember { mutableStateOf(720f) }
    val snowflakes = remember {
        List(numberOfSnowflakes) {
            Snowflake(
                x = Random.nextFloat() * screenWidth,
                y = Random.nextFloat() * screenHeight,
                size = Random.nextFloat() * 4f + 2f,
                speed = Random.nextFloat() * 70f + 30f,
                swingAmplitude = Random.nextFloat() * 34f + 14f,
                swingFrequency = Random.nextFloat() * 1.1f + 0.7f,
                swingOffset = Random.nextFloat() * 100f,
                opacity = Random.nextFloat() * 0.5f + 0.3f
            )
        }
    }
    // State ảo để ép vẽ lại UI
    var ticker by remember { mutableStateOf(0L) }

    // Random lại mỗi khi giao diện đổi size
    LaunchedEffect(screenWidth, screenHeight) {
        snowflakes.forEach { snow ->
            snow.x = Random.nextFloat() * screenWidth
            snow.y = Random.nextFloat() * screenHeight
        }
    }

    // Animation
    LaunchedEffect(screenWidth, screenHeight) {
        if (screenWidth == 0f || screenHeight == 0f) return@LaunchedEffect
        var lastTimeNanos = System.nanoTime()
        while (true) {
            val nowNanos = System.nanoTime()
            val delta = (nowNanos - lastTimeNanos) / 1_000_000_000f
            lastTimeNanos = nowNanos
            snowflakes.forEach { snow ->
                snow.y += snow.speed * delta
                snow.swingOffset += snow.swingFrequency * delta
                snow.x += (sin(snow.swingOffset) * snow.swingAmplitude * 0.04f)
                if (snow.y > screenHeight + 20f) {
                    snow.x = Random.nextFloat() * screenWidth
                    snow.y = -20f
                    snow.size = Random.nextFloat() * 4f + 2f
                    snow.speed = Random.nextFloat() * 70f + 30f
                    snow.swingAmplitude = Random.nextFloat() * 34f + 14f
                    snow.swingFrequency = Random.nextFloat() * 1.1f + 0.7f
                    snow.swingOffset = Random.nextFloat() * 100f
                    snow.opacity = Random.nextFloat() * 0.5f + 0.3f
                }
                if (snow.x < -20f) snow.x = screenWidth + 20f
                else if (snow.x > screenWidth + 20f) snow.x = -20f
            }
            ticker = nowNanos // BẮT BUỘC dòng này!!!
            kotlinx.coroutines.delay(8)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        screenWidth = size.width
        screenHeight = size.height
        ticker // Đặt tên biến vào đây là đủ để Compose Catch depend state này
        snowflakes.forEach { snow ->
            drawCircle(
                color = snowColor.copy(alpha = snow.opacity),
                radius = snow.size,
                center = Offset(snow.x, snow.y)
            )
        }
    }
}