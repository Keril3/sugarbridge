package com.sugarbridge.app

import com.sugarbridge.app.settings.LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every language has every string, with the same placeholders as English. A missing or extra %1$d would
 * crash the app or show garbage only in that one language, so it is checked here instead of by hand.
 */
class TranslationsTest {

    private val res = File("src/main/res")

    private class Entry(val text: String, val translatable: Boolean)

    private fun strings(folder: String): Map<String, Entry> {
        val file = File(res, "$folder/strings.xml")
        assertTrue("missing $file", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val e = nodes.item(i) as Element
            e.getAttribute("name") to Entry(e.textContent, e.getAttribute("translatable") != "false")
        }
    }

    private fun folderOf(tag: String): String = when {
        tag == "en" -> "values"
        "-" in tag -> "values-" + tag.substringBefore('-') + "-r" + tag.substringAfter('-')
        else -> "values-$tag"
    }

    private val placeholder = Regex("""%\d+\$[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]""")

    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyLanguageHasEveryStringWithTheSamePlaceholders() {
        val english = strings("values").filterValues { it.translatable }
        for (language in LANGUAGES.filter { it.tag != "en" }) {
            val folder = folderOf(language.tag)
            val translated = strings(folder)
            assertEquals("$folder: keys", english.keys.sorted(), translated.keys.sorted())
            for ((key, entry) in english) {
                val text = translated.getValue(key).text
                assertTrue("$folder/$key is empty", text.isNotBlank())
                assertEquals("$folder/$key: placeholders", placeholders(entry.text), placeholders(text))
            }
        }
    }

    @Test
    fun percentSignsMatchHowTheStringIsUsed() {
        for (language in LANGUAGES) {
            val folder = folderOf(language.tag)
            for ((key, entry) in strings(folder)) {
                val formatted = placeholders(entry.text).isNotEmpty()
                val rest = placeholder.replace(entry.text, "")
                if (formatted) {
                    // a formatted string: a literal % must be written as %%
                    assertFalse("$folder/$key has a lone %", rest.replace("%%", "").contains('%'))
                } else {
                    // shown without formatting: %% would appear on screen as is
                    assertFalse("$folder/$key has %% but no arguments", rest.contains("%%"))
                }
            }
        }
    }

    @Test
    fun apostrophesAreEscaped() {
        for (language in LANGUAGES) {
            val folder = folderOf(language.tag)
            val raw = File(res, "$folder/strings.xml").readText()
            val bad = Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""").findAll(raw)
                .filter { Regex("""(?<!\\)'""").containsMatchIn(it.groupValues[2]) }
                .map { it.groupValues[1] }
                .toList()
            assertEquals("$folder: unescaped apostrophes", emptyList<String>(), bad)
        }
    }

    @Test
    fun localeConfigListsTheSameLanguages() {
        val config = File(res, "xml/locales_config.xml").readText()
        val listed = Regex("""android:name="([^"]+)"""").findAll(config).map { it.groupValues[1] }.toList()
        assertEquals(LANGUAGES.map { it.tag }, listed)
    }
}
