package app.lumentv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Brand {
    val Violet = Color(0xFF7C5CFF)
    val Cyan = Color(0xFF22D3EE)
    val Live = Color(0xFFFF4D6D)
    val Warn = Color(0xFFFBBF24)
    val Ok = Color(0xFF34D399)
    val gradient = Brush.linearGradient(listOf(Violet, Cyan))
}

private val colors = darkColorScheme(
    primary = Color(0xFFB4A3FF),
    onPrimary = Color(0xFF24005F),
    primaryContainer = Color(0xFF3A2A86),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Brand.Cyan,
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF0B4A55),
    onSecondaryContainer = Color(0xFFB9F2FF),
    tertiary = Color(0xFFFFB0C2),
    background = Color(0xFF0B0D14),
    onBackground = Color(0xFFEEF0F6),
    surface = Color(0xFF0B0D14),
    onSurface = Color(0xFFEEF0F6),
    surfaceVariant = Color(0xFF1E2230),
    onSurfaceVariant = Color(0xFFA4A9BB),
    surfaceContainerLowest = Color(0xFF07080C),
    surfaceContainerLow = Color(0xFF11131C),
    surfaceContainer = Color(0xFF151824),
    surfaceContainerHigh = Color(0xFF1B1F2D),
    surfaceContainerHighest = Color(0xFF232838),
    outline = Color(0xFF3A3F52),
    outlineVariant = Color(0xFF262A39),
    error = Brand.Live,
)

private val base = Typography()
private val typography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
)

val Eyebrow = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
