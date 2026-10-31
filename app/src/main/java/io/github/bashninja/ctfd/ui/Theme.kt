package io.github.bashninja.ctfd.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle

private val LocalSolved = staticCompositionLocalOf { Color(0xFF7EE787) }

/** Solved challenges read as terminal green; the accent is a separate blue so the two never blur. */
val SolvedGreen: Color
    @Composable @ReadOnlyComposable get() = LocalSolved.current

/** Points, places and flags are set in monospace, like a terminal. */
val Mono = FontFamily.Monospace

/** "50 pts" with only the number in monospace; a monospace "pts" spreads out too far. */
fun points(value: Int) = buildAnnotatedString {
    withStyle(SpanStyle(fontFamily = Mono)) { append("$value") }
    append(" pts")
}

private val dark = darkColorScheme(
    primary = Color(0xFF5CC8FF),
    onPrimary = Color(0xFF00283A),
    primaryContainer = Color(0xFF0F3447),
    onPrimaryContainer = Color(0xFFC7EBFF),
    secondary = Color(0xFF9DB2C8),
    onSecondary = Color(0xFF14202C),
    secondaryContainer = Color(0xFF1C3A4D),
    onSecondaryContainer = Color(0xFFD6ECFA),
    tertiary = Color(0xFFFFB454),
    onTertiary = Color(0xFF3A2500),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1C232C),
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceContainerLowest = Color(0xFF090C10),
    surfaceContainerLow = Color(0xFF12171E),
    surfaceContainer = Color(0xFF161B22),
    surfaceContainerHigh = Color(0xFF1C222A),
    surfaceContainerHighest = Color(0xFF232A33),
    outline = Color(0xFF3D444D),
    outlineVariant = Color(0xFF2A3038),
    error = Color(0xFFFF7B72),
    onError = Color(0xFF3D0A06),
)

// The same palette carried to light: deeper blue and green so both hold up on white.
private val light = lightColorScheme(
    primary = Color(0xFF0A6FA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDF2FF),
    onPrimaryContainer = Color(0xFF00344F),
    secondary = Color(0xFF4F6275),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E8F5),
    onSecondaryContainer = Color(0xFF0B1D2B),
    tertiary = Color(0xFF9A6700),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEFF2F5),
    onSurfaceVariant = Color(0xFF59636E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = Color(0xFFF3F5F7),
    surfaceContainerHigh = Color(0xFFECEFF2),
    surfaceContainerHighest = Color(0xFFE5E9ED),
    outline = Color(0xFFADB5BD),
    outlineVariant = Color(0xFFD8DEE4),
    error = Color(0xFFCF222E),
    onError = Color(0xFFFFFFFF),
)

/** Follows the phone's light or dark setting. */
@Composable
fun CtfdTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalSolved provides if (isDark) Color(0xFF7EE787) else Color(0xFF1A7F37)) {
        MaterialTheme(colorScheme = if (isDark) dark else light, typography = Typography(), content = content)
    }
}
