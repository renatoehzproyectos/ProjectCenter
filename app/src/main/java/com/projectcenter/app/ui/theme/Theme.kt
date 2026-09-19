package com.projectcenter.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalAppThemeOption = staticCompositionLocalOf { AppThemeOption.VERCEL_DARK }

/** Geist-like radii: tight, product UI */
val GeistShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

private fun vercelDarkScheme() = darkColorScheme(
    primary = GeistPrimary,
    onPrimary = GeistOnPrimary,
    secondary = GeistElevated,
    onSecondary = GeistText,
    background = GeistBg,
    onBackground = GeistText,
    surface = GeistSurface,
    onSurface = GeistText,
    surfaceVariant = GeistElevated,
    onSurfaceVariant = GeistTextSecondary,
    outline = GeistBorder,
    outlineVariant = GeistBorderSubtle,
    error = FailedRed,
    onError = Color.White
)

private fun vercelLightScheme() = lightColorScheme(
    primary = GeistLightPrimary,
    onPrimary = GeistLightOnPrimary,
    secondary = Color(0xFFF5F5F5),
    onSecondary = GeistLightText,
    background = GeistLightBg,
    onBackground = GeistLightText,
    surface = GeistLightSurface,
    onSurface = GeistLightText,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = GeistLightTextSecondary,
    outline = GeistLightBorder,
    outlineVariant = Color(0xFFF0F0F0),
    error = FailedRed,
    onError = Color.White
)

private fun midnightScheme() = darkColorScheme(
    primary = MidnightPrimary, onPrimary = MidnightOnPrimary,
    secondary = MidnightSecondary, onSecondary = MidnightOnBg,
    background = MidnightBg, onBackground = MidnightOnBg,
    surface = MidnightSurface, onSurface = MidnightOnSurface,
    surfaceVariant = MidnightSurfaceVariant, onSurfaceVariant = MidnightMuted,
    outline = MidnightBorder, error = FailedRed, onError = Color.White
)

private fun oceanScheme() = darkColorScheme(
    primary = OceanPrimary, onPrimary = OceanOnPrimary,
    secondary = OceanSecondary, onSecondary = OceanOnBg,
    background = OceanBg, onBackground = OceanOnBg,
    surface = OceanSurface, onSurface = OceanOnSurface,
    surfaceVariant = OceanSurfaceVariant, onSurfaceVariant = OceanMuted,
    outline = OceanBorder, error = FailedRed, onError = Color.White
)

private fun forestScheme() = darkColorScheme(
    primary = ForestPrimary, onPrimary = ForestOnPrimary,
    secondary = ForestSecondary, onSecondary = ForestOnBg,
    background = ForestBg, onBackground = ForestOnBg,
    surface = ForestSurface, onSurface = ForestOnSurface,
    surfaceVariant = ForestSurfaceVariant, onSurfaceVariant = ForestMuted,
    outline = ForestBorder, error = FailedRed, onError = Color.White
)

private fun roseScheme() = darkColorScheme(
    primary = RosePrimary, onPrimary = RoseOnPrimary,
    secondary = RoseSecondary, onSecondary = RoseOnBg,
    background = RoseBg, onBackground = RoseOnBg,
    surface = RoseSurface, onSurface = RoseOnSurface,
    surfaceVariant = RoseSurfaceVariant, onSurfaceVariant = RoseMuted,
    outline = RoseBorder, error = FailedRed, onError = Color.White
)

@Composable
fun ProjectCenterTheme(
    themeOption: AppThemeOption = AppThemeOption.VERCEL_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeOption) {
        AppThemeOption.VERCEL_DARK -> vercelDarkScheme()
        AppThemeOption.VERCEL_LIGHT -> vercelLightScheme()
        AppThemeOption.MIDNIGHT -> midnightScheme()
        AppThemeOption.OCEAN -> oceanScheme()
        AppThemeOption.FOREST -> forestScheme()
        AppThemeOption.ROSE -> roseScheme()
    }

    CompositionLocalProvider(LocalAppThemeOption provides themeOption) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = GeistShapes,
            content = content
        )
    }
}
