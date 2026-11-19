package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CockpitDarkColorScheme = darkColorScheme(
    primary = RsCrimson,
    onPrimary = Color.White,
    primaryContainer = RsCrimsonContainer,
    onPrimaryContainer = RsCrimsonOnContainer,
    secondary = TachAmber,
    onSecondary = Color(0xFF1A1000),
    secondaryContainer = TachAmberContainer,
    onSecondaryContainer = TachAmberOnContainer,
    tertiary = DiagnosticTeal,
    onTertiary = Color(0xFF00201E),
    tertiaryContainer = DiagnosticTealContainer,
    onTertiaryContainer = DiagnosticTealOnContainer,
    background = ObsidianBg,
    onBackground = InkPrimary,
    surface = CarbonSurface,
    onSurface = InkPrimary,
    surfaceVariant = CarbonSurfaceElevated,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = CarbonSurfaceElevated,
    surfaceContainerHigh = CarbonSurfaceHigh,
    outline = TitaniumOutline,
    outlineVariant = TitaniumOutlineSubtle,
    error = FaultRed,
    onError = Color.White
)

private val CockpitLightColorScheme = lightColorScheme(
    primary = RsCrimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADE),
    onPrimaryContainer = Color(0xFF410008),
    secondary = Color(0xFFB36B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1B3),
    onSecondaryContainer = Color(0xFF2E1900),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF042F2E),
    background = LightBg,
    onBackground = LightInkPrimary,
    surface = LightSurface,
    onSurface = LightInkPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightInkSecondary,
    outline = Color(0xFFC5CCD8),
    outlineVariant = Color(0xFFDCE1EA),
    error = Color(0xFFDC2626),
    onError = Color.White
)

val CockpitShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MotoLinkTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // Motorcycle instrument cockpit defaults to high-contrast dark mode for glare reduction
    val colorScheme = if (darkTheme) CockpitDarkColorScheme else CockpitLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CockpitShapes,
        content = content
    )
}
