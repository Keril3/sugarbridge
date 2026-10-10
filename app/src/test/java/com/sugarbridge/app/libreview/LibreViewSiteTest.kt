package com.sugarbridge.app.libreview

import org.junit.Assert.assertEquals
import org.junit.Test

class LibreViewSiteTest {

    @Test
    fun russianAccountsUseLibreViewRu() {
        assertEquals("https://www.libreview.ru/", libreViewSite("ru", "DE"))
        assertEquals("https://www.libreview.com/", libreViewSite("eu", "RU"))   // регион аккаунта важнее страны
        assertEquals("https://www.libreview.com/", libreViewSite("us", "US"))
    }

    @Test
    fun beforeSignInCountryDecides() {
        assertEquals("https://www.libreview.ru/", libreViewSite(null, "RU"))
        assertEquals("https://www.libreview.com/", libreViewSite(null, "FR"))
    }
}
