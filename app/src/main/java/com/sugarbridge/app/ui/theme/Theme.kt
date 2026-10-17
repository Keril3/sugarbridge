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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sugarbridge.app.glucose.GlucoseLevel
import com.sugarbridge.app.settings.AppFont
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.ThemeMode

/**
 * The look of keril.art: a sky from pale to deep blue (navy and violet at night), Windows XP style windows
 * with a gradient title bar and glossy blue buttons; the neutral theme mutes it all to warm greys.
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
    val starry: Boolean = true,       // the field of dots and the tint; the low vision look has a plain background
    // glossy buttons: the main one, the light one, the small one with a chain; blue like on the site
    val buttonGloss: List<Color> = listOf(Color(0xFFF4FAFF), Color(0xFFBFE0FB), Color(0xFF6FAEE8), Color(0xFF3C82D6)),
    val buttonBorder: Color = Color(0xFF2A5FA8),
    val buttonText: Color = Color(0xFF0A2A52),   // glossy buttons stay light at night too, like on the site
    val secondaryGloss: List<Color> = listOf(Color.White, Color(0xFFDCEBF7)),
    val secondaryBorder: Color = Color(0xFF7FA7D4),
    val smallButtonGloss: List<Color> = listOf(Color(0xFFF2F8FF), Color(0xFF9FC3E8)),
    val smallButtonBorder: Color = Color(0xFF5A86B8),
    val meter: Color = Color(0xFF4FA8E8),       // the blocks of a progress bar
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

/**
 * The neutral theme: warm grey paper and soft ink, muted buttons, no dots and no tint. Nothing bright or
 * saturated, so it is easy on the eyes; light or dark as the phone is.
 */
val NeutralLightXp = XpColors(
    sky = listOf(Color(0xFFEDEBE7), Color(0xFFE6E3DE), Color(0xFFDDD9D2)),
    windowBody = Color(0xFFF8F7F4),
    windowBorder = Color(0xFFD6D2CA),
    bar = listOf(Color(0xFFD9D5CE), Color(0xFFB8B2A8)),
    text = Color(0xFF2E2D2B),
    textMuted = Color(0xFF6A665F),
    statusBar = listOf(Color(0xF0F8F7F4), Color(0xCCEDEBE7)),
    statusBorder = Color(0xFFD6D2CA),
    accent = Color(0xFF4D6B78),
    dots = Color(0xFFB4AEA4),
    floaterStar = Color(0xFF8A8478),
    shadow = Color(0x26000000),
    starry = false,
    buttonGloss = listOf(Color(0xFFFBFAF8), Color(0xFFEFECE7), Color(0xFFE2DED7), Color(0xFFD5D0C7)),
    buttonBorder = Color(0xFF9D978C),
    buttonText = Color(0xFF2E2D2B),
    secondaryGloss = listOf(Color(0xFFFFFFFF), Color(0xFFF0EEEA)),
    secondaryBorder = Color(0xFFBDB7AC),
    smallButtonGloss = listOf(Color(0xFFFBFAF8), Color(0xFFE2DED7)),
    smallButtonBorder = Color(0xFF9D978C),
    meter = Color(0xFF6E8B98),
)

val NeutralDarkXp = XpColors(
    sky = listOf(Color(0xFF1F2022), Color(0xFF1C1D1F), Color(0xFF191A1C)),
    windowBody = Color(0xFF27282B),
    windowBorder = Color(0xFF3B3C40),
    bar = listOf(Color(0xFF3A3C40), Color(0xFF2C2D31)),
    text = Color(0xFFDAD8D3),
    textMuted = Color(0xFFA29F99),
    statusBar = listOf(Color(0xF02A2B2E), Color(0xCC232427)),
    statusBorder = Color(0xFF45464B),
    accent = Color(0xFF9FB6C1),
    dots = Color(0xFF55575C),
    floaterStar = Color(0xFF8E8C88),
    shadow = Color(0x66000000),
    starry = false,
    buttonGloss = listOf(Color(0xFF45474C), Color(0xFF3C3E42), Color(0xFF36383C), Color(0xFF303236)),
    buttonBorder = Color(0xFF63666C),
    buttonText = Color(0xFFE3E1DC),
    secondaryGloss = listOf(Color(0xFF35373B), Color(0xFF2D2F33)),
    secondaryBorder = Color(0xFF4D5055),
    smallButtonGloss = listOf(Color(0xFF3C3E42), Color(0xFF303236)),
    smallButtonBorder = Color(0xFF63666C),
    meter = Color(0xFF7F99A5),
)

// For low vision: a plain background, black or white text, dark title bars and clear window edges
val LightContrastXp = XpColors(
    sky = listOf(Color(0xFFEAF2FB), Color(0xFFEAF2FB)),
    windowBody = Color.White,
    windowBorder = Color(0xFF0B1630),
    bar = listOf(Color(0xFF1F5FBF), Color(0xFF15468F)),
    text = Color.Black,
    textMuted = Color(0xFF1E2B45),
    statusBar = listOf(Color.White, Color.White),
    statusBorder = Color(0xFF0B1630),
    accent = Color(0xFF0B47A8),
    dots = Color(0xFF1F5FBF),
    floaterStar = Color(0xFF0B47A8),
    shadow = Color(0x4D000000),
    starry = false,
)

val DarkContrastXp = XpColors(
    sky = listOf(Color(0xFF02040E), Color(0xFF02040E)),
    windowBody = Color(0xFF0A0F24),
    windowBorder = Color(0xFFE3EEFF),
    bar = listOf(Color(0xFF3557D6), Color(0xFF1E3399)),
    text = Color.White,
    textMuted = Color(0xFFDCE6FF),
    statusBar = listOf(Color(0xFF0A0F24), Color(0xFF0A0F24)),
    statusBorder = Color(0xFFE3EEFF),
    accent = Color(0xFFB5D3FF),
    dots = Color(0xFF8FA9F0),
    floaterStar = Color.White,
    shadow = Color(0x99000000),
    starry = false,
)

/** Colors that are the same in both themes. */
object Tenshi {
    val Pink = Color(0xFFFFC9E8)
    val PinkDeep = Color(0xFFE0559A)
    val DangerBorder = Color(0xFFA82020)
    val DangerGloss = listOf(Color(0xFFFFE3E0), Color(0xFFFF9A90))
    val DangerText = Color(0xFF6E0F0F)
    val CloseGloss = listOf(Color(0xFFFF9A90), Color(0xFFD32F2F))

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

private val NeutralLightColors = lightColorScheme(
    primary = Color(0xFF4D6B78),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2DED7),
    onPrimaryContainer = Color(0xFF2E2D2B),
    secondary = Color(0xFF8A6F78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE5E7),
    onSecondaryContainer = Color(0xFF3B2A30),
    tertiary = Color(0xFF6E8B98),
    onTertiary = Color.White,
    background = Color(0xFFEDEBE7),
    onBackground = NeutralLightXp.text,
    surface = Color(0xFFF8F7F4),
    onSurface = NeutralLightXp.text,
    surfaceVariant = Color(0xFFE6E3DE),
    onSurfaceVariant = NeutralLightXp.textMuted,
    surfaceContainerLowest = Color(0xFFFBFAF8),
    surfaceContainerLow = Color(0xFFF4F2EF),
    surfaceContainer = Color(0xFFEDEBE7),
    surfaceContainerHigh = Color(0xFFE6E3DE),
    surfaceContainerHighest = Color(0xFFDDD9D2),
    outline = Color(0xFFB4AEA4),
    outlineVariant = Color(0xFFD6D2CA),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val NeutralDarkColors = darkColorScheme(
    primary = Color(0xFF9FB6C1),
    onPrimary = Color(0xFF1C2A30),
    primaryContainer = Color(0xFF3A3C40),
    onPrimaryContainer = Color(0xFFDAD8D3),
    secondary = Color(0xFFC9AEB6),
    onSecondary = Color(0xFF3B2A30),
    secondaryContainer = Color(0xFF45393D),
    onSecondaryContainer = Color(0xFFEDE5E7),
    tertiary = Color(0xFF7F99A5),
    onTertiary = Color(0xFF1C2A30),
    background = Color(0xFF1F2022),
    onBackground = NeutralDarkXp.text,
    surface = Color(0xFF27282B),
    onSurface = NeutralDarkXp.text,
    surfaceVariant = Color(0xFF2E2F33),
    onSurfaceVariant = NeutralDarkXp.textMuted,
    surfaceContainerLowest = Color(0xFF1C1D1F),
    surfaceContainerLow = Color(0xFF232427),
    surfaceContainer = Color(0xFF27282B),
    surfaceContainerHigh = Color(0xFF2E2F33),
    surfaceContainerHighest = Color(0xFF36383C),
    outline = Color(0xFF63666C),
    outlineVariant = Color(0xFF3B3C40),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
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

private fun style(family: FontFamily, size: Int, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = family,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

/**
 * The pixel font. Monocraft has a single weight and a synthetic bold would smear the pixels, so every style is
 * Normal; tight lines read best with it.
 */
private fun pixelTypography(family: FontFamily) = Typography(
    displayLarge = style(family, 52, 56),
    displayMedium = style(family, 44, 48),
    displaySmall = style(family, 32, 36),
    headlineLarge = style(family, 28, 32),
    headlineMedium = style(family, 24, 28),
    headlineSmall = style(family, 20, 24, 0.5),
    titleLarge = style(family, 19, 23),
    titleMedium = style(family, 16, 20, 0.5),
    titleSmall = style(family, 14, 18),
    bodyLarge = style(family, 15, 19),
    bodyMedium = style(family, 13, 17),
    bodySmall = style(family, 12, 16),
    labelLarge = style(family, 14, 18),
    labelMedium = style(family, 12, 15),
    labelSmall = style(family, 11, 14),
)

/**
 * The phone's font, Noto Sans or Noto Serif: bold titles and a little more space between lines. [readable] is
 * the low vision look: a size larger everywhere. Every style keeps its place, so the screens stay the same.
 */
private fun plainTypography(family: FontFamily, readable: Boolean) = Typography().let { m3 ->
    val more = if (readable) 2 else 0
    fun TextStyle.plain(size: Int, weight: FontWeight = FontWeight.Normal) =
        copy(fontFamily = family, fontWeight = weight, fontSize = (size + more).sp, lineHeight = ((size + more) * 1.3f).sp, letterSpacing = 0.sp)
    Typography(
        displayLarge = m3.displayLarge.plain(52, FontWeight.Bold),
        displayMedium = m3.displayMedium.plain(44, FontWeight.Bold),
        displaySmall = m3.displaySmall.plain(32, FontWeight.Bold),
        headlineLarge = m3.headlineLarge.plain(28, FontWeight.Bold),
        headlineMedium = m3.headlineMedium.plain(24, FontWeight.Bold),
        headlineSmall = m3.headlineSmall.plain(21, FontWeight.Bold),
        titleLarge = m3.titleLarge.plain(19, FontWeight.Bold),
        titleMedium = m3.titleMedium.plain(16, FontWeight.Bold),
        titleSmall = m3.titleSmall.plain(14, FontWeight.Bold),
        bodyLarge = m3.bodyLarge.plain(16),
        bodyMedium = m3.bodyMedium.plain(15),
        bodySmall = m3.bodySmall.plain(14),
        labelLarge = m3.labelLarge.plain(15, FontWeight.Bold),
        labelMedium = m3.labelMedium.plain(13, FontWeight.Bold),
        labelSmall = m3.labelSmall.plain(12, FontWeight.Medium),
    )
}

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

/** How much larger than the phone's font size the app's text is, from Settings. */
val LocalTextScale = staticCompositionLocalOf { 1f }

@Composable
fun isNight(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM, ThemeMode.NEUTRAL -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Applies the text size from Settings. A dialog is a window of its own that gets the phone's font size again,
 * so every dialog's content goes through this too (see XpDialog).
 */
@Composable
fun ProvideTextScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = LocalTextScale.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * scale), content = content)
}

@Composable
fun SugarTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    textScale: Float = 1f,
    lowVision: Boolean = false,
    font: AppFont = AppFont.SYSTEM,
    content: @Composable () -> Unit,
) {
    val night = isNight(mode)
    val neutral = mode == ThemeMode.NEUTRAL
    val xp = when {
        lowVision && night -> DarkContrastXp
        lowVision -> LightContrastXp
        neutral && night -> NeutralDarkXp
        neutral -> NeutralLightXp
        night -> DarkXp
        else -> LightXp
    }
    // low vision trades the pixel font for the phone's own; the other fonts stay, a size larger
    val chosen = if (lowVision && font == AppFont.PIXEL) AppFont.SYSTEM else font
    val family = rememberAppFont(chosen)
    val typography = remember(chosen, family, lowVision) {
        if (chosen == AppFont.PIXEL) pixelTypography(family) else plainTypography(family, lowVision)
    }
    CompositionLocalProvider(LocalNight provides night, LocalXp provides xp, LocalTextScale provides textScale) {
        MaterialTheme(
            colorScheme = when {
                neutral && night -> NeutralDarkColors
                neutral -> NeutralLightColors
                night -> DarkColors
                else -> LightColors
            },
            typography = typography,
            shapes = XpShapes,
        ) {
            ProvideTextScale(content)
        }
    }
}

/** Builds the theme from the saved settings. */
@Composable
fun SugarTheme(settings: AppSettings, content: @Composable () -> Unit) =
    SugarTheme(settings.theme, settings.textScale, settings.lowVision, settings.font, content)
