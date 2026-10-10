package com.sugarbridge.app.libreview

/**
 * Сайт LibreView для выгрузки истории. У российских аккаунтов он свой (libreview.ru), у остальных —
 * libreview.com. Регион берём из входа в LibreLinkUp, а до входа — по стране телефона.
 */
fun libreViewSite(region: String?, country: String): String {
    val russian = region?.equals("ru", ignoreCase = true) ?: country.equals("RU", ignoreCase = true)
    return if (russian) "https://www.libreview.ru/" else "https://www.libreview.com/"
}
