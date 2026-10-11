package com.sugarbridge.app

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import android.os.SystemClock
import com.sugarbridge.app.settings.setAppLanguage
import java.util.Locale

/** Switches the app language for a test (null = the phone's) and waits until the app process uses it. */
fun useAppLanguage(context: Context, tag: String?) {
    setAppLanguage(context, tag)
    val want = tag?.let { Locale.forLanguageTag(it).language } ?: return
    val deadline = SystemClock.uptimeMillis() + 5_000
    while (context.resources.configuration.locales[0].language != want && SystemClock.uptimeMillis() < deadline) {
        Thread.sleep(100)
    }
}

/** The app's resources in a given language: what the screens and the notification are expected to show. */
fun resourcesIn(context: Context, tag: String): Resources {
    val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
    return context.createConfigurationContext(config).resources
}
