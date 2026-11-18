package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.model.TemaMode

@Immutable
data class AppExtendedColors(
    val headerBg: Color,
    val headingText: Color,
    val cardSubtleBg: Color,
    val green: Color,
    val greenContainer: Color,
    val red: Color,
    val redContainer: Color,
    val amber: Color,
    val amberContainer: Color,
    val blue: Color,
    val blueContainer: Color,
    val purple: Color,
    val purpleContainer: Color,
    val teal: Color,
    val tealContainer: Color,
    val bannerAlertBg: Color,
    val bannerOkBg: Color,
    val borderSubtle: Color,
    val isDark: Boolean,
)

private val LightExtendedColors = AppExtendedColors(
    headerBg = NavyHeader,
    headingText = NavyHeader,
    cardSubtleBg = LightSurfaceSubtle,
    green = EmeraldGreen,
    greenContainer = GreenContainerLight,
    red = CrimsonRed,
    redContainer = RedContainerLight,
    amber = WarmAmber,
    amberContainer = AmberContainerLight,
    blue = AccentBlue,
    blueContainer = BlueContainerLight,
    purple = RoyalPurple,
    purpleContainer = PurpleContainerLight,
    teal = DeepTeal,
    tealContainer = TealContainerLight,
    bannerAlertBg = BannerAlertLight,
    bannerOkBg = BannerOkLight,
    borderSubtle = LightBorderSubtle,
    isDark = false,
)

private val DarkExtendedColors = AppExtendedColors(
    headerBg = NavyHeaderDark,
    headingText = DarkHeading,
    cardSubtleBg = DarkSurfaceSubtle,
    green = EmeraldGreenDark,
    greenContainer = GreenContainerDark,
    red = CrimsonRedDark,
    redContainer = RedContainerDark,
    amber = WarmAmberDark,
    amberContainer = AmberContainerDark,
    blue = AccentBlueDark,
    blueContainer = BlueContainerDark,
    purple = RoyalPurpleDark,
    purpleContainer = PurpleContainerDark,
    teal = DeepTealDark,
    tealContainer = TealContainerDark,
    bannerAlertBg = BannerAlertDark,
    bannerOkBg = BannerOkDark,
    borderSubtle = DarkBorderSubtle,
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = NavyHeader,
    secondary = EmeraldGreen,
    onSecondary = Color.White,
    secondaryContainer = GreenContainerLight,
    onSecondaryContainer = EmeraldGreen,
    tertiary = RoyalPurple,
    onTertiary = Color.White,
    tertiaryContainer = PurpleContainerLight,
    onTertiaryContainer = RoyalPurple,
    error = CrimsonRed,
    onError = Color.White,
    errorContainer = RedContainerLight,
    onErrorContainer = CrimsonRed,
    background = LightBackground,
    onBackground = LightTextMain,
    surface = LightSurface,
    onSurface = LightTextMain,
    surfaceVariant = LightSurfaceSubtle,
    onSurfaceVariant = LightTextMuted,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlueDark,
    onPrimary = Color.White,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = DarkTextMain,
    secondary = EmeraldGreenDark,
    onSecondary = Color.White,
    secondaryContainer = GreenContainerDark,
    onSecondaryContainer = EmeraldGreenDark,
    tertiary = RoyalPurpleDark,
    onTertiary = Color.Black,
    tertiaryContainer = PurpleContainerDark,
    onTertiaryContainer = RoyalPurpleDark,
    error = CrimsonRedDark,
    onError = Color.White,
    errorContainer = RedContainerDark,
    onErrorContainer = CrimsonRedDark,
    background = DarkBackground,
    onBackground = DarkTextMain,
    surface = DarkSurface,
    onSurface = DarkTextMain,
    surfaceVariant = DarkSurfaceSubtle,
    onSurfaceVariant = DarkTextMuted,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
)

@Composable
fun TareasYAvisosTheme(
    temaMode: TemaMode = TemaMode.SISTEMA,
    content: @Composable () -> Unit,
) {
    val isDark = when (temaMode) {
        TemaMode.SISTEMA -> isSystemInDarkTheme()
        TemaMode.CLARO -> false
        TemaMode.OSCURO -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val extendedColors = if (isDark) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalAppColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
