package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkWorkbenchColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00241D),
    primaryContainer = DarkTealCard,
    onPrimaryContainer = CyberCyan,
    secondary = BlueAccent,
    onSecondary = Color(0xFF001E36),
    secondaryContainer = Color(0xFF162536),
    onSecondaryContainer = Color(0xFFD0E8FF),
    tertiary = AmberSignal,
    onTertiary = Color(0xFF2A1B00),
    tertiaryContainer = Color(0xFF2B2110),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = DeepDarkBg,
    onBackground = TextPrimary,
    surface = CardDarkBg,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1B2430),
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    outlineVariant = Color(0xFF2D3A4B),
    error = CoralRed,
    onError = Color.White,
    errorContainer = Color(0xFF331518),
    onErrorContainer = Color(0xFFFFB4AB)
)

private val LightWorkbenchColorScheme = lightColorScheme(
    primary = MeshtasticGreenLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = MeshCoreCyanLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = LabLightBg,
    onBackground = Color(0xFF0F172A),
    surface = LabLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LabLightCard,
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFCBD5E1),
    error = CoralError,
    onError = Color.White
)

val WorkbenchShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkWorkbenchColorScheme else LightWorkbenchColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = WorkbenchShapes,
        content = content
    )
}
