package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AppThemeMode(val storageKey: String) {
    WARM_CREAM("WARM_CREAM"),
    DEEP_NIGHT("DEEP_NIGHT");

    val isDark: Boolean
        get() = this == DEEP_NIGHT

    companion object {
        fun fromStorageKey(key: String?): AppThemeMode {
            return entries.firstOrNull { it.storageKey == key } ?: WARM_CREAM
        }
    }
}

@Immutable
data class ThemeController(
    val themeMode: AppThemeMode = AppThemeMode.WARM_CREAM,
    val setThemeMode: (AppThemeMode) -> Unit = {},
    val toggleTheme: () -> Unit = {}
) {
    val isDark: Boolean
        get() = themeMode.isDark
}

@Immutable
data class AntiMagerColors(
    val idealAccent: Color = SageGreen,
    val idealContainer: Color = SageContainer,
    val idealBorder: Color = SageBorder,
    val lightAccent: Color = WarmTerracotta,
    val lightContainer: Color = TerracottaContainer,
    val lightBorder: Color = TerracottaBorder,
    val cardBorder: Color = SoftWarmBorder,
    val restCircle: Color = SoftRestCircle,
    val warmSandSurface: Color = WarmSand,
    val subtleInk: Color = SubtleInk,
    val stravaTint: Color = StravaTint,
    val stravaContainer: Color = StravaContainer,
    val healthTint: Color = HealthTint,
    val healthContainer: Color = HealthContainer,
    val heartRateTint: Color = HeartRateTint,
    val heartRateContainer: Color = HeartRateContainer,
    val dangerTint: Color = DangerRed,
    val dangerContainer: Color = DangerContainer
)

private val WarmCreamExtendedColors = AntiMagerColors()

private val DeepNightExtendedColors = AntiMagerColors(
    idealAccent = DeepNightSage,
    idealContainer = DeepNightSageContainer,
    idealBorder = DeepNightSageBorder,
    lightAccent = DeepNightTerracotta,
    lightContainer = DeepNightTerracottaContainer,
    lightBorder = DeepNightTerracottaBorder,
    cardBorder = DeepNightBorder,
    restCircle = DeepNightRestCircle,
    warmSandSurface = DeepNightSurfaceVariant,
    subtleInk = DeepNightSubtleInk,
    stravaTint = DeepNightStravaTint,
    stravaContainer = DeepNightStravaContainer,
    healthTint = DeepNightHealthTint,
    healthContainer = DeepNightHealthContainer,
    heartRateTint = DeepNightHeartRateTint,
    heartRateContainer = DeepNightHeartRateContainer,
    dangerTint = DeepNightDangerRed,
    dangerContainer = DeepNightDangerContainer
)

val LocalAntiMagerColors = staticCompositionLocalOf { WarmCreamExtendedColors }
val LocalThemeController = staticCompositionLocalOf { ThemeController() }

private val CalmLightColorScheme = lightColorScheme(
    primary = SageGreen,
    onPrimary = PureWhite,
    primaryContainer = SageContainer,
    onPrimaryContainer = SageGreenDark,
    secondary = WarmTerracotta,
    onSecondary = PureWhite,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = WarmTerracottaDark,
    tertiary = HealthTint,
    onTertiary = PureWhite,
    tertiaryContainer = HealthContainer,
    onTertiaryContainer = WarmInk,
    background = WarmCream,
    onBackground = WarmInk,
    surface = PureWhite,
    onSurface = WarmInk,
    surfaceVariant = WarmSand,
    onSurfaceVariant = MutedInk,
    outline = SoftWarmBorder,
    outlineVariant = SoftWarmBorder,
    error = DangerRed,
    onError = PureWhite,
    errorContainer = DangerContainer,
    onErrorContainer = DangerRed
)

private val DeepNightDarkColorScheme = darkColorScheme(
    primary = DeepNightSage,
    onPrimary = DeepNightOnSage,
    primaryContainer = DeepNightSageContainer,
    onPrimaryContainer = DeepNightSage,
    secondary = DeepNightTerracotta,
    onSecondary = DeepNightOnTerracotta,
    secondaryContainer = DeepNightTerracottaContainer,
    onSecondaryContainer = DeepNightTerracotta,
    tertiary = DeepNightHealthTint,
    onTertiary = DeepNightBackground,
    tertiaryContainer = DeepNightHealthContainer,
    onTertiaryContainer = DeepNightInk,
    background = DeepNightBackground,
    onBackground = DeepNightInk,
    surface = DeepNightSurface,
    onSurface = DeepNightInk,
    surfaceVariant = DeepNightSurfaceVariant,
    onSurfaceVariant = DeepNightMutedInk,
    outline = DeepNightBorder,
    outlineVariant = DeepNightBorder,
    error = DeepNightDangerRed,
    onError = DeepNightBackground,
    errorContainer = DeepNightDangerContainer,
    onErrorContainer = DeepNightDangerRed
)

val AntiMagerShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

object AntiMagerTheme {
    val extendedColors: AntiMagerColors
        @Composable
        get() = LocalAntiMagerColors.current

    val controller: ThemeController
        @Composable
        get() = LocalThemeController.current
}

@Composable
fun AntiMagerCoachTheme(
    themeMode: AppThemeMode = AppThemeMode.WARM_CREAM,
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    content: @Composable () -> Unit
) {
    val isDark = themeMode.isDark
    val colorScheme = if (isDark) DeepNightDarkColorScheme else CalmLightColorScheme
    val extendedColors = if (isDark) DeepNightExtendedColors else WarmCreamExtendedColors
    val controller = ThemeController(
        themeMode = themeMode,
        setThemeMode = onThemeModeChange,
        toggleTheme = {
            val next = if (themeMode == AppThemeMode.WARM_CREAM) {
                AppThemeMode.DEEP_NIGHT
            } else {
                AppThemeMode.WARM_CREAM
            }
            onThemeModeChange(next)
        }
    )

    CompositionLocalProvider(
        LocalAntiMagerColors provides extendedColors,
        LocalThemeController provides controller
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AntiMagerShapes,
            content = content
        )
    }
}
