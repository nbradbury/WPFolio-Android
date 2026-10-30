package com.nbradbury.wpreadme.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.nbradbury.wpreadme.data.prefs.ThemeMode

/**
 * The interface is off-white, paper or near-black so that featured images supply the colour.
 */
@Immutable
data class WpReadMeColors(
    val isDark: Boolean,
    val background: Color,
    val articleBackground: Color,
    val text: Color,
    val textSecondary: Color,
    val body: Color,
    val bodyMuted: Color,
    val divider: Color,
    val outline: Color,
    val inverse: Color,
    val onInverse: Color,
    val pill: Color,
    val pillIcon: Color,
    val pillSelected: Color,
    val pillSelectedIcon: Color,
    val sheet: Color,
    val card: Color,
    val placeholderAvatar: Color,
)

val LightColors = WpReadMeColors(
    isDark = false,
    background = Color(0xFFFAFAF8),
    articleBackground = Color(0xFFFAFAF8),
    text = Color(0xFF141414),
    textSecondary = Color(0xFF62615D),
    body = Color(0xFF2A2A28),
    bodyMuted = Color(0xFF4A4A48),
    divider = Color(0x14000000),
    outline = Color(0x24000000),
    inverse = Color(0xFF141414),
    onInverse = Color.White,
    pill = Color(0xF0161616),
    pillIcon = Color.White,
    pillSelected = Color.White,
    pillSelectedIcon = Color(0xFF141414),
    sheet = Color.White,
    card = Color.White,
    placeholderAvatar = Color(0xFFD8C7B0),
)

val PaperColors = WpReadMeColors(
    isDark = false,
    background = Color(0xFFF1E8D8),
    articleBackground = Color(0xFFF1E8D8),
    text = Color(0xFF3B2F22),
    textSecondary = Color(0xFF7A6A55),
    body = Color(0xFF3B2F22),
    bodyMuted = Color(0xFF5A4A38),
    divider = Color(0x1A3B2F22),
    outline = Color(0x2E3B2F22),
    inverse = Color(0xFF3B2F22),
    onInverse = Color(0xFFF1E8D8),
    pill = Color(0xF03B2F22),
    pillIcon = Color(0xFFF1E8D8),
    pillSelected = Color(0xFFF1E8D8),
    pillSelectedIcon = Color(0xFF3B2F22),
    sheet = Color(0xFFF7F0E3),
    card = Color(0xFFF7F0E3),
    placeholderAvatar = Color(0xFFD8C7B0),
)

val DarkColors = WpReadMeColors(
    isDark = true,
    background = Color(0xFF0F0F10),
    articleBackground = Color(0xFF121212),
    text = Color(0xFFF2F2F0),
    textSecondary = Color(0xFFA3A3A0),
    body = Color(0xFFD6D6D2),
    bodyMuted = Color(0xFFB9B9B5),
    divider = Color(0x12FFFFFF),
    outline = Color(0x29FFFFFF),
    inverse = Color(0xFFF2F2F0),
    onInverse = Color(0xFF141414),
    pill = Color(0xF0303032),
    pillIcon = Color.White,
    pillSelected = Color(0xFFF2F2F0),
    pillSelectedIcon = Color(0xFF141414),
    sheet = Color(0xFF1F1F21),
    card = Color(0xFF1F1F21),
    placeholderAvatar = Color(0xFF8A7A66),
)

val Destructive = Color(0xFFB3261E)
val SnackbarBackground = Color(0xFF262626)
val SnackbarAction = Color(0xFFE8C9CF)

val LocalWpReadMeColors = staticCompositionLocalOf { LightColors }

object WpReadMe {
    val colors: WpReadMeColors
        @Composable get() = LocalWpReadMeColors.current
}

@Composable
fun WpReadMeTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = when (mode) {
        ThemeMode.LIGHT -> LightColors
        ThemeMode.PAPER -> PaperColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkColors else LightColors
    }
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.inverse, onPrimary = colors.onInverse,
            background = colors.background, onBackground = colors.text,
            surface = colors.background, onSurface = colors.text,
            surfaceContainerLow = colors.sheet, surfaceContainer = colors.sheet,
            onSurfaceVariant = colors.textSecondary, outline = colors.outline,
        )
    } else {
        lightColorScheme(
            primary = colors.inverse, onPrimary = colors.onInverse,
            background = colors.background, onBackground = colors.text,
            surface = colors.background, onSurface = colors.text,
            surfaceContainerLow = colors.sheet, surfaceContainer = colors.sheet,
            onSurfaceVariant = colors.textSecondary, outline = colors.outline,
        )
    }
    CompositionLocalProvider(LocalWpReadMeColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = WpReadMeTypography, content = content)
    }
}

/**
 * Sets system bar icon colour while this screen is shown. The status bar follows whatever is
 * under it (often an image); the navigation bar sits over themed content, so it follows the theme.
 */
@Composable
fun SystemBarIcons(lightIcons: Boolean, lightNavIcons: Boolean = WpReadMe.colors.isDark) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(lightIcons, lightNavIcons) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !lightIcons
                isAppearanceLightNavigationBars = !lightNavIcons
            }
        }
        onDispose {}
    }
}
