package com.sugarbridge.app.libreview

/**
 * The LibreView site for the history export. Russian accounts have their own (libreview.ru), everyone else
 * uses libreview.com. The region comes from the LibreLinkUp sign-in, or from the phone's country before it.
 */
fun libreViewSite(region: String?, country: String): String {
    val russian = region?.equals("ru", ignoreCase = true) ?: country.equals("RU", ignoreCase = true)
    return if (russian) "https://www.libreview.ru/" else "https://www.libreview.com/"
}
