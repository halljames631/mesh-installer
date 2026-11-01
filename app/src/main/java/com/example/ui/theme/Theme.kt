package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkWorkbenchColorScheme = darkColorScheme(
    primary = MeshtasticGreen,
    onPrimary = Color(0xFF002612),
    primaryContainer = MeshtasticGreenDim,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = MeshCoreCyan,
    onSecondary = Color(0xFF002436),
    secondaryContainer = MeshCoreCyanDim,
    onSecondaryContainer = Color(0xFFB3E5FC),
    tertiary = AmberSignal,
    onTertiary = Color(0xFF2A1B00),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    error = CoralError,
    onError = Color.White
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
        content = content
    )
}
