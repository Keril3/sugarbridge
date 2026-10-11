package com.sugarbridge.app.ui

import android.content.Context
import android.content.res.Resources
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sugarbridge.app.R
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A message for the snackbar. It is turned into text only when shown, in the language of that moment. */
fun interface UiText {
    fun resolve(res: Resources): String
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText { it.getString(id, *args) }

/** Clock time in the app language and the phone's 12/24-hour setting: "20:05" or "8:05 PM". */
fun clockFormat(context: Context, locale: Locale = context.resources.configuration.locales[0]): DateTimeFormatter {
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

@Composable
fun rememberClockFormat(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = currentLocale()
    return remember(locale) { clockFormat(context, locale) }
}

/** A date pattern from a skeleton, ordered the way the app language writes dates. */
@Composable
fun rememberDateFormat(skeleton: String): DateTimeFormatter {
    val locale = currentLocale()
    return remember(locale, skeleton) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale) }
}

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun ago(d: Duration): String {
    val minutes = d.toMinutes()
    return when {
        minutes < 1 -> stringResource(R.string.ago_now)
        minutes < 60 -> stringResource(R.string.ago_minutes, minutes.toInt())
        minutes < 24 * 60 -> stringResource(R.string.ago_hours, (minutes / 60).toInt(), (minutes % 60).toInt())
        else -> stringResource(R.string.ago_days, (minutes / (24 * 60)).toInt())
    }
}

@Composable
fun minutesText(minutes: Int): String =
    if (minutes < 60) stringResource(R.string.duration_minutes, minutes) else stringResource(R.string.duration_hours, minutes / 60)
