package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = CobaltOnPrimary,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = CobaltOnContainer,
    secondary = SlateSecondary,
    onSecondary = SlateOnSecondary,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = SlateOnContainer,
    tertiary = EmeraldVerified,
    onTertiary = SurfaceWhite,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldOnContainer,
    error = ErrorRed,
    onError = SurfaceWhite,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = BackgroundIce,
    onBackground = TextInk,
    surface = SurfaceWhite,
    onSurface = TextInk,
    surfaceVariant = SurfaceVariantIce,
    onSurfaceVariant = TextMuted,
    outline = OutlineSubtle,
    outlineVariant = OutlineVariantSubtle,
    surfaceContainerLowest = SurfaceWhite,
    surfaceContainerLow = ColorSurfaceLow,
    surfaceContainer = SurfaceWhite,
    surfaceContainerHigh = SurfaceVariantIce,
    surfaceContainerHighest = CobaltContainer
)

private val ColorSurfaceLow get() = androidx.compose.ui.graphics.Color(0xFFFAFBFD)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkOnSurfaceVariant,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    tertiary = androidx.compose.ui.graphics.Color(0xFF34D399),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF064E3B),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF065F46),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFD1FAE5),
    error = androidx.compose.ui.graphics.Color(0xFFF87171),
    onError = androidx.compose.ui.graphics.Color(0xFF7F1D1D),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF7F1D1D),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFEE2E2),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkSurfaceVariant
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun HitungGajianProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
