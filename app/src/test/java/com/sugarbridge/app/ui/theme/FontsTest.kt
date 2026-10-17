package com.sugarbridge.app.ui.theme

import com.sugarbridge.app.settings.LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.nio.ByteBuffer
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The fonts that come with the app are cut to the characters of its texts. A new or changed text with a letter
 * they lack would show that letter in the phone's font, so every letter, digit and Chinese, Japanese or Korean
 * punctuation mark of every language must be in each font made for it. tools/make_fonts.py cuts them anew.
 */
class FontsTest {

    private val res = File("src/main/res")

    /** Each weight of each font that comes with the app: its Latin and Cyrillic file, and its CJK files with %s for zh, ja or ko. */
    private val fonts = listOf(
        "monocraft" to "pixel_%s",
        "noto_sans_regular" to "noto_sans_%s_regular",
        "noto_sans_bold" to "noto_sans_%s_bold",
        "noto_serif_regular" to "noto_serif_%s_regular",
        "noto_serif_bold" to "noto_serif_%s_bold",
    )

    private fun folderOf(tag: String): String = when {
        tag == "en" -> "values"
        "-" in tag -> "values-" + tag.substringBefore('-') + "-r" + tag.substringAfter('-')
        else -> "values-$tag"
    }

    /** The text of every string and plural of one language. */
    private fun texts(folder: String): String {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings.xml"))
        return listOf("string", "item").joinToString("") { tag ->
            val nodes = doc.getElementsByTagName(tag)
            (0 until nodes.length).joinToString("") { (nodes.item(it) as Element).textContent }
        }
    }

    /** The characters that must look the same as the rest; symbols such as ↗ or ☁ may come from the phone. */
    private fun mustHave(c: Int) = Character.isLetterOrDigit(c) || c in 0x3000..0x303F || c in 0xFF01..0xFF5E

    @Test
    fun everyLetterOfEveryLanguageIsInItsFonts() {
        val cmaps = HashMap<String, Set<Int>>()
        fun cmapOf(name: String) = cmaps.getOrPut(name) { cmap(File(res, "font/$name.ttf")) }
        for (language in LANGUAGES) {
            val chars = texts(folderOf(language.tag)).codePoints().toArray().toSet().filter(::mustHave)
            val cjk = language.tag.substringBefore('-').takeIf { it in setOf("zh", "ja", "ko") }
            for ((latin, cjkFiles) in fonts) {
                val names = listOfNotNull(latin, cjk?.let { cjkFiles.format(it) })
                val covered = names.flatMap(::cmapOf).toSet()
                val missing = chars.filter { it !in covered }.joinToString("") { String(Character.toChars(it)) }
                assertEquals("${language.tag}: $names lack", "", missing)
            }
        }
    }

    @Test
    fun theReaderFindsWhatAFontHas() {
        val monocraft = cmap(File(res, "font/monocraft.ttf"))
        assertTrue('A'.code in monocraft && 'я'.code in monocraft)
        assertFalse(0x2197 in monocraft)   // ↗ comes from the phone
        val chinese = cmap(File(res, "font/noto_sans_zh_regular.ttf"))
        assertTrue("设".codePointAt(0) in chinese)
    }

    /** The characters a TrueType or OpenType font has a glyph for, from its cmap formats 4 and 12. */
    private fun cmap(file: File): Set<Int> {
        val b = ByteBuffer.wrap(file.readBytes())
        fun u16(at: Int) = b.getShort(at).toInt() and 0xFFFF
        val table = (0 until u16(4)).map { 12 + it * 16 }.first { String(ByteArray(4) { i -> b.get(it + i) }, Charsets.US_ASCII) == "cmap" }
        val cmap = b.getInt(table + 8)
        val chars = HashSet<Int>()
        for (i in 0 until u16(cmap + 2)) {
            val sub = cmap + b.getInt(cmap + 4 + i * 8 + 4)
            when (u16(sub)) {
                4 -> {
                    val segments = u16(sub + 6) / 2
                    val ends = sub + 14
                    val starts = ends + segments * 2 + 2
                    val deltas = starts + segments * 2
                    val offsets = deltas + segments * 2
                    for (s in 0 until segments) {
                        val start = u16(starts + s * 2)
                        val end = u16(ends + s * 2)
                        val delta = u16(deltas + s * 2)
                        val offset = u16(offsets + s * 2)
                        for (c in start..end) {
                            if (c == 0xFFFF) continue
                            val glyph = if (offset == 0) (c + delta) and 0xFFFF
                            else u16(offsets + s * 2 + offset + (c - start) * 2).let { if (it == 0) 0 else (it + delta) and 0xFFFF }
                            if (glyph != 0) chars += c
                        }
                    }
                }
                12 -> {
                    for (g in 0 until b.getInt(sub + 12)) {
                        val group = sub + 16 + g * 12
                        val start = b.getInt(group)
                        val firstGlyph = b.getInt(group + 8)
                        for (c in start..b.getInt(group + 4)) if (firstGlyph + (c - start) != 0) chars += c
                    }
                }
            }
        }
        return chars
    }
}
