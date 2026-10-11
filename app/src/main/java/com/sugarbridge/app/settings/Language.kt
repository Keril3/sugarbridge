package com.sugarbridge.app.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

/** A language the app is translated to, with its name written in that language. */
class Language(val tag: String, val name: String)

/** The same list is in res/xml/locales_config.xml, which the system per-app language settings use. */
val LANGUAGES = listOf(
    Language("en", "English"),
    Language("ru", "Русский"),
    Language("uk", "Українська"),
    Language("de", "Deutsch"),
    Language("fr", "Français"),
    Language("es", "Español"),
    Language("pt", "Português"),
    Language("it", "Italiano"),
    Language("tr", "Türkçe"),
    Language("zh-CN", "简体中文"),
    Language("ja", "日本語"),
    Language("ko", "한국어"),
)

/** The language chosen in the app, or null when it follows the phone. */
fun appLanguage(context: Context): Language? {
    val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
    if (locales.isEmpty) return null
    val chosen = locales[0]
    return LANGUAGES.firstOrNull { Locale.forLanguageTag(it.tag) == chosen }
        ?: LANGUAGES.firstOrNull { Locale.forLanguageTag(it.tag).language == chosen.language }
}

/**
 * Switches the app language (Android 13+ per-app languages); null follows the phone again.
 * Android itself restarts the open screens in the new language and remembers the choice.
 */
fun setAppLanguage(context: Context, tag: String?) {
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}
