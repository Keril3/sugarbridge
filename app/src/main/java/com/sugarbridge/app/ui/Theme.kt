package com.sugarbridge.app.ui

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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.GlucoseLevel
import com.sugarbridge.app.R

/** Фирменные цвета «тэнси каваии»: сакура, небо, лаванда и золото нимба. */
object Tenshi {
    val Sakura = Color(0xFFF58BB8)
    val SakuraDeep = Color(0xFFE5609A)
    val Sky = Color(0xFF8EC8FF)
    val Lavender = Color(0xFFC59BFF)
    val Halo = Color(0xFFFFC94A)
    val HaloLight = Color(0xFFFFF1BF)
    val Ink = Color(0xFF4A2B4F)

    /** Градиент кнопок и акцентов: сакура → лаванда → небо. */
    val Rainbow = listOf(Sakura, Lavender, Sky)

    // Цвета уровней сахара: ниже — прохладный барвинок, в диапазоне — мята, выше — персик
    val Low = Color(0xFF7C83F0)
    val InRange = Color(0xFF3FBF94)
    val High = Color(0xFFFF8A5C)
}

fun GlucoseLevel.color(): Color = when (this) {
    GlucoseLevel.LOW -> Tenshi.Low
    GlucoseLevel.IN_RANGE -> Tenshi.InRange
    GlucoseLevel.HIGH -> Tenshi.High
}

private val LightColors = lightColorScheme(
    primary = Color(0xFFE5609A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E8),
    onPrimaryContainer = Color(0xFF5A1033),
    secondary = Color(0xFF5B9FE0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEEFF),
    onSecondaryContainer = Color(0xFF0E3558),
    tertiary = Color(0xFF9B76F0),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEEE3FF),
    onTertiaryContainer = Color(0xFF34176B),
    background = Color(0xFFFFF5FA),
    onBackground = Color(0xFF3B2A3F),
    surface = Color(0xFFFFFBFD),
    onSurface = Color(0xFF3B2A3F),
    surfaceVariant = Color(0xFFF7EAF2),
    onSurfaceVariant = Color(0xFF7A6580),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF6FA),
    surfaceContainer = Color(0xFFFCEFF6),
    surfaceContainerHigh = Color(0xFFF8E8F1),
    surfaceContainerHighest = Color(0xFFF3E1EC),
    outline = Color(0xFFDDBFD3),
    outlineVariant = Color(0xFFF0DDE8),
    error = Color(0xFFD9466F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDDE5),
    onErrorContainer = Color(0xFF5C0A24),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9CC6),
    onPrimary = Color(0xFF4A0F2D),
    primaryContainer = Color(0xFF6B2549),
    onPrimaryContainer = Color(0xFFFFD9E8),
    secondary = Color(0xFF9CCBFF),
    onSecondary = Color(0xFF0E3558),
    secondaryContainer = Color(0xFF234A73),
    onSecondaryContainer = Color(0xFFDCEEFF),
    tertiary = Color(0xFFCDB4FF),
    onTertiary = Color(0xFF34176B),
    tertiaryContainer = Color(0xFF4A3380),
    onTertiaryContainer = Color(0xFFEEE3FF),
    background = Color(0xFF1B1528),
    onBackground = Color(0xFFF3E9F5),
    surface = Color(0xFF241C33),
    onSurface = Color(0xFFF3E9F5),
    surfaceVariant = Color(0xFF33294A),
    onSurfaceVariant = Color(0xFFCDBCD6),
    surfaceContainerLowest = Color(0xFF16111F),
    surfaceContainerLow = Color(0xFF211A2E),
    surfaceContainer = Color(0xFF2A2139),
    surfaceContainerHigh = Color(0xFF332942),
    surfaceContainerHighest = Color(0xFF3D3250),
    outline = Color(0xFF6A5878),
    outlineVariant = Color(0xFF3F3352),
    error = Color(0xFFFF8FAE),
    onError = Color(0xFF5C0A24),
    errorContainer = Color(0xFF7A1E3C),
    onErrorContainer = Color(0xFFFFDDE5),
)

/** Nunito — округлый шрифт с кириллицей; один файл с переменной толщиной. */
@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) =
    Font(R.font.nunito, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Nunito = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700), nunito(800), nunito(900))

private fun TextStyle.nunito(weight: FontWeight) = copy(fontFamily = Nunito, fontWeight = weight)

private val Base = Typography()

private val TenshiTypography = Typography(
    displayLarge = Base.displayLarge.nunito(FontWeight.Black),
    displayMedium = Base.displayMedium.nunito(FontWeight.Black),
    displaySmall = Base.displaySmall.nunito(FontWeight.ExtraBold),
    headlineLarge = Base.headlineLarge.nunito(FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.nunito(FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.nunito(FontWeight.ExtraBold),
    titleLarge = Base.titleLarge.nunito(FontWeight.ExtraBold),
    titleMedium = Base.titleMedium.nunito(FontWeight.Bold),
    titleSmall = Base.titleSmall.nunito(FontWeight.Bold),
    bodyLarge = Base.bodyLarge.nunito(FontWeight.Medium),
    bodyMedium = Base.bodyMedium.nunito(FontWeight.Medium),
    bodySmall = Base.bodySmall.nunito(FontWeight.Medium),
    labelLarge = Base.labelLarge.nunito(FontWeight.Bold),
    labelMedium = Base.labelMedium.nunito(FontWeight.Bold),
    labelSmall = Base.labelSmall.nunito(FontWeight.Bold),
)

private val TenshiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** true — ночная тема: фон и украшения рисуются в тёмных тонах. */
val LocalNight = staticCompositionLocalOf { false }

@Composable
fun SugarTheme(content: @Composable () -> Unit) {
    val night = isSystemInDarkTheme()
    CompositionLocalProvider(LocalNight provides night) {
        MaterialTheme(
            colorScheme = if (night) DarkColors else LightColors,
            typography = TenshiTypography,
            shapes = TenshiShapes,
            content = content,
        )
    }
}
