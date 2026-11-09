package com.sugarbridge.app.libreview

import java.net.URLDecoder
import java.util.Base64

/**
 * The LibreView site for the history export. Russian accounts have their own (libreview.ru), everyone else
 * uses libreview.com. The region comes from the LibreLinkUp sign-in, or from the phone's country before it.
 */
fun libreViewSite(region: String?, country: String): String {
    val russian = region?.equals("ru", ignoreCase = true) ?: country.equals("RU", ignoreCase = true)
    return if (russian) "https://www.libreview.ru/" else "https://www.libreview.com/"
}

/** LibreView's own sites: files are taken only from their pages, and the sign-in cookies go only to them. */
fun isLibreViewHost(host: String?): Boolean =
    host != null && listOf("libreview.com", "libreview.io", "libreview.ru").any { host == it || host.endsWith(".$it") }

/** The file in a "data:[type][;base64],content" link; null when it is broken or too large. */
fun decodeDataUrl(url: String, limit: Int = LibreViewCsv.MAX_BYTES): ByteArray? {
    if (!url.startsWith("data:") || url.length > limit * 2L) return null
    val comma = url.indexOf(',')
    if (comma < 0) return null
    val payload = url.substring(comma + 1)
    return try {
        val bytes = if (url.substring(0, comma).endsWith(";base64")) {
            Base64.getMimeDecoder().decode(payload)
        } else {
            URLDecoder.decode(payload, "UTF-8").toByteArray()
        }
        bytes.takeIf { it.size <= limit }
    } catch (e: IllegalArgumentException) {
        null
    }
}
