package com.sugarbridge.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.GlucoseLevel
import com.sugarbridge.app.settings.ThemeMode

/**
 * The look of keril.art: a sky from pale to deep blue (navy and violet at night), Windows XP style windows
 * with a gradient title bar, glossy blue buttons and the Monocraft pixel font.
 */
class XpColors(
    val sky: List<Color>,             // top, middle, bottom of the background
    val windowBody: Color,
    val windowBorder: Color,
    val bar: List<Color>,             // window title bar, top to bottom
    val text: Color,
    val textMuted: Color,
    val statusBar: List<Color>,       // the address bar at the top
    val statusBorder: Color,
    val accent: Color,                // links, selected items
    val dots: Color,                  // dotted lines between rows
    val floaterStar: Color,
    val shadow: Color,
)

val LightXp = XpColors(
    sky = listOf(Color(0xFFEAF6FF), Color(0xFF7FCDFB), Color(0xFF2E7FE0)),
    windowBody = Color.White,
    windowBorder = Color.White,
    bar = listOf(Color(0xFFCFEBFF), Color(0xFF4FA8E8)),
    text = Color(0xFF0B1630),
    textMuted = Color(0xFF4A6182),
    statusBar = listOf(Color(0xE6FFFFFF), Color(0x66FFFFFF)),
    statusBorder = Color.White,
    accent = Color(0xFF1C5FC4),
    dots = Color(0xFF4FA8E8),
    floaterStar = Color(0xFF3C82D6),
    shadow = Color(0x4D14468C),
)

val DarkXp = XpColors(
    sky = listOf(Color(0xFF0A1030), Color(0xFF18225E), Color(0xFF352C7E)),
    windowBody = Color(0xFF10183E),
    windowBorder = Color(0x8CAAD2FF),
    bar = listOf(Color(0xFF6F8FEA), Color(0xFF2A3C9A)),
    text = Color(0xFFE3EEFF),
    textMuted = Color(0xFFA9BEE8),
    statusBar = listOf(Color(0xE634468C), Color(0xA6162052)),
    statusBorder = Color(0x80AAD2FF),
    accent = Color(0xFF9DC3FF),
    dots = Color(0xFF6F8FEA),
    floaterStar = Color(0xFFE3EEFF),
    shadow = Color(0x73000000),
)

/** Colors that are the same in both themes. */
object Tenshi {
    val Pink = Color(0xFFFFC9E8)
    val PinkDeep = Color(0xFFE0559A)
    val ButtonText = Color(0xFF0A2A52)          // glossy buttons stay light at night too, like on the site
    val ButtonBorder = Color(0xFF2A5FA8)
    val ButtonGloss = listOf(Color(0xFFF4FAFF), Color(0xFFBFE0FB), Color(0xFF6FAEE8), Color(0xFF3C82D6))
    val SecondaryBorder = Color(0xFF7FA7D4)
    val SecondaryGloss = listOf(Color.White, Color(0xFFDCEBF7))
    val DangerBorder = Color(0xFFA82020)
    val DangerGloss = listOf(Color(0xFFFFE3E0), Color(0xFFFF9A90))
    val DangerText = Color(0xFF6E0F0F)
    val CloseGloss = listOf(Color(0xFFFF9A90), Color(0xFFD32F2F))
    val SmallButtonBorder = Color(0xFF5A86B8)
    val SmallButtonGloss = listOf(Color(0xFFF2F8FF), Color(0xFF9FC3E8))

    // Glucose levels: below is pink-red, in range is green, above is orange; readable on white and on navy
    val Low = Color(0xFFE8508F)
    val InRange = Color(0xFF25A974)
    val High = Color(0xFFF08A24)
}

fun GlucoseLevel.color(): Color = when (this) {
    GlucoseLevel.LOW -> Tenshi.Low
    GlucoseLevel.IN_RANGE -> Tenshi.InRange
    GlucoseLevel.HIGH -> Tenshi.High
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7FE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEBFF),
    onPrimaryContainer = Color(0xFF0A2A52),
    secondary = Color(0xFFE0559A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6F4),
    onSecondaryContainer = Color(0xFF5A1033),
    tertiary = Color(0xFF6F8FEA),
    onTertiary = Color.White,
    background = Color(0xFFEAF6FF),
    onBackground = LightXp.text,
    surface = Color.White,
    onSurface = LightXp.text,
    surfaceVariant = Color(0xFFEAF6FF),
    onSurfaceVariant = LightXp.textMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4FAFF),
    surfaceContainer = Color(0xFFEAF6FF),
    surfaceContainerHigh = Color(0xFFE3F1FD),
    surfaceContainerHighest = Color(0xFFD8ECFC),
    outline = Color(0xFF8EBDE8),
    outlineVariant = Color(0xFFCFE6FB),
    error = Color(0xFFD32F2F),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB4FF),
    onPrimary = Color(0xFF0A1030),
    primaryContainer = Color(0xFF2A3C9A),
    onPrimaryContainer = Color(0xFFE3EEFF),
    secondary = Color(0xFFFF8FC8),
    onSecondary = Color(0xFF3A0A22),
    secondaryContainer = Color(0xFF5A2A60),
    onSecondaryContainer = Color(0xFFFFE6F4),
    tertiary = Color(0xFF6F8FEA),
    onTertiary = Color(0xFF0A1030),
    background = Color(0xFF0A1030),
    onBackground = DarkXp.text,
    surface = Color(0xFF10183E),
    onSurface = DarkXp.text,
    surfaceVariant = Color(0xFF1B2558),
    onSurfaceVariant = DarkXp.textMuted,
    surfaceContainerLowest = Color(0xFF0A1030),
    surfaceContainerLow = Color(0xFF10183E),
    surfaceContainer = Color(0xFF162052),
    surfaceContainerHigh = Color(0xFF1B2558),
    surfaceContainerHighest = Color(0xFF243070),
    outline = Color(0xFF6F8FEA),
    outlineVariant = Color(0xFF2A3C9A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0A0A),
)

/** Monocraft: the pixel font of keril.art, with Latin and Cyrillic. Other scripts fall back to the system font. */
val Monocraft = FontFamily(Font(R.font.monocraft, FontWeight.Normal))

// Monocraft has a single weight; a synthetic bold would smear the pixels, so every style is Normal
private fun style(size: Int, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = Monocraft,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

// Tight lines: the pixel font reads best with little space between lines
private val XpTypography = Typography(
    displayLarge = style(52, 56),
    displayMedium = style(44, 48),
    displaySmall = style(32, 36),
    headlineLarge = style(28, 32),
    headlineMedium = style(24, 28),
    headlineSmall = style(20, 24, 0.5),
    titleLarge = style(19, 23),
    titleMedium = style(16, 20, 0.5),
    titleSmall = style(14, 18),
    bodyLarge = style(15, 19),
    bodyMedium = style(13, 17),
    bodySmall = style(12, 16),
    labelLarge = style(14, 18),
    labelMedium = style(12, 15),
    labelSmall = style(11, 14),
)

private val XpShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** true in the night theme. */
val LocalNight = staticCompositionLocalOf { false }

/** The keril.art colors of the current theme. */
val LocalXp = staticCompositionLocalOf { LightXp }

@Composable
fun isNight(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SugarTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val night = isNight(mode)
    CompositionLocalProvider(LocalNight provides night, LocalXp provides if (night) DarkXp else LightXp) {
        MaterialTheme(
            colorScheme = if (night) DarkColors else LightColors,
            typography = XpTypography,
            shapes = XpShapes,
            content = content,
        )
    }
}
