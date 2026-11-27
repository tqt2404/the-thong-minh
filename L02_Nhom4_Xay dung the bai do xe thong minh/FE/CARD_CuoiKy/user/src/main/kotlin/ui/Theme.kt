package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ParkColors {
    val Background = Color(0xFF0F172A)
    val Surface = Color(0xFF1E293B)
    val SurfaceMuted = Color(0xFF283548)
    val Border = Color(0xFF334155)
    val BorderStrong = Color(0xFF475569)
    val Primary = Color(0xFF14B8A6)
    val PrimaryDark = Color(0xFF0D9488)
    val PrimarySoft = Color(0xFF134E4A).copy(alpha = 0.45f)
    val PrimaryBorder = Color(0xFF14B8A6).copy(alpha = 0.4f)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
    val Success = Color(0xFF22C55E)
    val SuccessBg = Color(0xFF14532D).copy(alpha = 0.35f)
    val SuccessBorder = Color(0xFF22C55E).copy(alpha = 0.35f)
    val Warning = Color(0xFFF59E0B)
    val WarningBg = Color(0xFF78350F).copy(alpha = 0.35f)
    val WarningBorder = Color(0xFFF59E0B).copy(alpha = 0.35f)
    val Error = Color(0xFFF87171)
    val ErrorBg = Color(0xFF7F1D1D).copy(alpha = 0.35f)
    val ErrorBorder = Color(0xFFF87171).copy(alpha = 0.35f)
}

@Composable
fun ParkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = darkColors(
            primary = ParkColors.Primary,
            primaryVariant = ParkColors.PrimaryDark,
            secondary = ParkColors.PrimaryDark,
            background = ParkColors.Background,
            surface = ParkColors.Surface,
            error = ParkColors.Error,
            onPrimary = Color.White,
            onBackground = ParkColors.TextPrimary,
            onSurface = ParkColors.TextPrimary
        ),
        content = content
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = 24.dp,
    backgroundColor: Color = ParkColors.Surface,
    borderColor: Color = ParkColors.Border,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
    backgroundColor: Color = ParkColors.Primary
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (!enabled) ParkColors.BorderStrong else if (hovered) ParkColors.PrimaryDark else backgroundColor,
        tween(180)
    )
    val scale by animateFloatAsState(
        if (pressed && enabled && !isLoading) 0.97f else if (hovered && enabled) 1.01f else 1f,
        spring(stiffness = Spring.StiffnessMediumLow)
    )

    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        interactionSource = interaction,
        modifier = modifier.height(46.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(10.dp),
        elevation = ButtonDefaults.elevation(2.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = bg, contentColor = Color.White, disabledBackgroundColor = ParkColors.BorderStrong, disabledContentColor = ParkColors.TextSecondary)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    textColor: Color = ParkColors.TextPrimary,
    borderColor: Color = ParkColors.BorderStrong
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (hovered) ParkColors.SurfaceMuted else ParkColors.Surface, tween(160))
    val scale by animateFloatAsState(if (pressed && enabled) 0.98f else 1f, spring(stiffness = Spring.StiffnessMediumLow))

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.height(46.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(backgroundColor = bg, contentColor = textColor)
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SidebarNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        when {
            selected -> ParkColors.PrimarySoft
            hovered -> ParkColors.SurfaceMuted
            else -> Color.Transparent
        },
        tween(160)
    )
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(stiffness = Spring.StiffnessMediumLow))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) ParkColors.Primary else ParkColors.TextSecondary, modifier = Modifier.size(19.dp))
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) ParkColors.TextPrimary else ParkColors.TextSecondary)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ParkColors.TextPrimary)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, fontSize = 14.sp, color = ParkColors.TextSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Divider(color = ParkColors.Border)
    }
}

@Composable
fun StatusBanner(message: String?, isError: Boolean = false, isWarning: Boolean = false) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 3 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 3 }
    ) {
        val (bg, border, fg, icon) = when {
            isError -> listOf(ParkColors.ErrorBg, ParkColors.ErrorBorder, ParkColors.Error, Icons.Rounded.Error)
            isWarning -> listOf(ParkColors.WarningBg, ParkColors.WarningBorder, ParkColors.Warning, Icons.Rounded.Warning)
            else -> listOf(ParkColors.SuccessBg, ParkColors.SuccessBorder, ParkColors.Success, Icons.Rounded.CheckCircle)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = bg as Color,
            border = BorderStroke(1.dp, border as Color),
            elevation = 0.dp
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon as ImageVector, contentDescription = null, tint = fg as Color, modifier = Modifier.size(18.dp))
                Text(message.orEmpty(), fontSize = 13.sp, color = fg, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = ParkColors.TextPrimary, showDivider: Boolean = true) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = ParkColors.TextSecondary)
            Text(value.ifBlank { "Chưa có dữ liệu" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
        }
        if (showDivider) Divider(color = ParkColors.Border)
    }
}

@Composable
fun lightTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    textColor = ParkColors.TextPrimary,
    disabledTextColor = ParkColors.TextSecondary,
    backgroundColor = ParkColors.Background,
    cursorColor = ParkColors.Primary,
    focusedBorderColor = ParkColors.Primary,
    unfocusedBorderColor = ParkColors.BorderStrong,
    focusedLabelColor = ParkColors.Primary,
    unfocusedLabelColor = ParkColors.TextSecondary,
    placeholderColor = ParkColors.TextMuted
)

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    errorText: String? = null
) {
    var visible by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ParkColors.TextPrimary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (placeholder.isNotEmpty()) {{ Text(placeholder, color = ParkColors.TextMuted) }} else null,
            leadingIcon = leadingIcon?.let {{ Icon(it, null, tint = ParkColors.TextSecondary, modifier = Modifier.size(18.dp)) }},
            trailingIcon = if (isPassword) {{
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = ParkColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }} else null,
            visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            singleLine = true,
            enabled = enabled,
            readOnly = readOnly,
            isError = errorText != null,
            shape = RoundedCornerShape(10.dp),
            colors = lightTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        if (!errorText.isNullOrBlank()) {
            Text(errorText, fontSize = 12.sp, color = ParkColors.Error)
        }
    }
}
