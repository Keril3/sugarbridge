package com.sugarbridge.app.libreview

import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class LibreViewSiteTest {

    @Test
    fun russianAccountsUseLibreViewRu() {
        assertEquals("https://www.libreview.ru/", libreViewSite("ru", "DE"))
        assertEquals("https://www.libreview.com/", libreViewSite("eu", "RU"))   // the account region beats the country
        assertEquals("https://www.libreview.com/", libreViewSite("us", "US"))
    }

    @Test
    fun beforeSignInCountryDecides() {
        assertEquals("https://www.libreview.ru/", libreViewSite(null, "RU"))
        assertEquals("https://www.libreview.com/", libreViewSite(null, "FR"))
    }

    @Test
    fun onlyLibreViewsOwnSitesCount() {
        for (host in listOf("libreview.com", "www.libreview.com", "api-eu.libreview.io", "www.libreview.ru")) assertTrue(host, isLibreViewHost(host))
        for (host in listOf("example.com", "libreview.com.example.com", "notlibreview.com", "", null)) assertFalse(host.toString(), isLibreViewHost(host))
    }

    @Test
    fun aFileIsTakenFromADataLink() {
        assertEquals("6,4", String(decodeDataUrl("data:text/csv;base64,Niw0")!!))
        assertEquals("6,4 a", String(decodeDataUrl("data:text/csv,6%2C4%20a")!!))
    }

    @Test
    fun aBrokenOrHugeDataLinkGivesNothingAndDoesNotCrash() {
        assertNull(decodeDataUrl("data:text/csv,100%"))                  // a broken escape used to bring the app down
        assertNull(decodeDataUrl("data:text/csv"))
        assertNull(decodeDataUrl("https://www.libreview.com/x.csv"))
        assertNull(decodeDataUrl("data:text/csv,0123456789", limit = 5))
    }
}
