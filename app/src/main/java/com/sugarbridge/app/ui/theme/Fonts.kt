package com.sugarbridge.app.ui.theme

import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import androidx.annotation.FontRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontFamily
import com.sugarbridge.app.R
import com.sugarbridge.app.settings.AppFont
import com.sugarbridge.app.ui.currentLocale

/**
 * The fonts that come with the app. Each one is a Latin and Cyrillic font (with Turkish, German, French,
 * Ukrainian …) followed by Chinese, Japanese and Korean fonts of the same design, cut to the characters of the
 * app's texts and dates in those languages, so all twelve languages look alike. The language of the app goes
 * first, because Chinese and Japanese draw the same character differently. Whatever is still missing, a name
 * or a symbol, comes from the phone's fonts.
 */
object AppFonts {
    private class FontSet(val latin: List<Pair<Int, Int>>, val zh: List<Pair<Int, Int>>, val ja: List<Pair<Int, Int>>, val ko: List<Pair<Int, Int>>, val system: String)

    private const val REGULAR = 400
    private const val BOLD = 700

    private val sets = mapOf(
        AppFont.PIXEL to FontSet(
            listOf(R.font.monocraft to REGULAR),
            listOf(R.font.pixel_zh to REGULAR), listOf(R.font.pixel_ja to REGULAR), listOf(R.font.pixel_ko to REGULAR),
            "sans-serif",
        ),
        AppFont.SANS to FontSet(
            listOf(R.font.noto_sans_regular to REGULAR, R.font.noto_sans_bold to BOLD),
            listOf(R.font.noto_sans_zh_regular to REGULAR, R.font.noto_sans_zh_bold to BOLD),
            listOf(R.font.noto_sans_ja_regular to REGULAR, R.font.noto_sans_ja_bold to BOLD),
            listOf(R.font.noto_sans_ko_regular to REGULAR, R.font.noto_sans_ko_bold to BOLD),
            "sans-serif",
        ),
        AppFont.SERIF to FontSet(
            listOf(R.font.noto_serif_regular to REGULAR, R.font.noto_serif_bold to BOLD),
            listOf(R.font.noto_serif_zh_regular to REGULAR, R.font.noto_serif_zh_bold to BOLD),
            listOf(R.font.noto_serif_ja_regular to REGULAR, R.font.noto_serif_ja_bold to BOLD),
            listOf(R.font.noto_serif_ko_regular to REGULAR, R.font.noto_serif_ko_bold to BOLD),
            "serif",
        ),
    )

    private val cache = HashMap<Pair<AppFont, String>, Typeface>()

    /** The typeface of [font] for an app in [language] ("zh", "ja", "ko" or anything else); null for the phone's font. */
    @Synchronized
    fun typeface(resources: Resources, font: AppFont, language: String): Typeface? {
        val set = sets[font] ?: return null
        val key = font to language.takeIf { it in CJK }.orEmpty()
        return cache.getOrPut(key) {
            val cjk = mapOf("zh" to set.zh, "ja" to set.ja, "ko" to set.ko)
            val order = listOfNotNull(language.takeIf { it in CJK }) + CJK.filter { it != language }
            val builder = Typeface.CustomFallbackBuilder(family(resources, set.latin))
            order.forEach { builder.addCustomFallback(family(resources, cjk.getValue(it))) }
            builder.setSystemFallback(set.system).build()
        }
    }

    private fun family(resources: Resources, fonts: List<Pair<Int, Int>>): PlatformFontFamily {
        fun font(@FontRes id: Int, weight: Int) = PlatformFont.Builder(resources, id).setWeight(weight).build()
        val builder = PlatformFontFamily.Builder(font(fonts[0].first, fonts[0].second))
        fonts.drop(1).forEach { (id, weight) -> builder.addFont(font(id, weight)) }
        return builder.build()
    }

    private val CJK = listOf("zh", "ja", "ko")
}

/** The chosen font for the app's language; the phone's own font for [AppFont.SYSTEM]. */
@Composable
fun rememberAppFont(font: AppFont): FontFamily {
    val resources = LocalResources.current
    val language = currentLocale().language
    return remember(font, language) {
        AppFonts.typeface(resources, font, language)?.let { FontFamily(it) } ?: FontFamily.Default
    }
}
