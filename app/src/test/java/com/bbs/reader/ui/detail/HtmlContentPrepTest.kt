package com.bbs.reader.ui.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlContentPrepTest {
    @Test
    fun darkTheme_removesBlackTextColor() {
        val html = """<p style="color:#000000">검은글씨</p><span style="color:black">x</span>"""
        val result = prepareContentHtml(html, darkTheme = true)
        assertFalse(result.contains("color:#000000", ignoreCase = true))
        assertFalse(result.contains("color:black", ignoreCase = true))
        assertTrue(result.contains("검은글씨"))
    }

    @Test
    fun lightTheme_removesWhiteTextColor() {
        val html = """<p style="color:#ffffff">흰글씨</p>"""
        val result = prepareContentHtml(html, darkTheme = false)
        assertFalse(result.contains("color:#ffffff", ignoreCase = true))
        assertTrue(result.contains("흰글씨"))
    }

    @Test
    fun keepsReadableAccentColor() {
        val html = """<p style="color:#C62828">강조</p>"""
        val dark = prepareContentHtml(html, darkTheme = true)
        val light = prepareContentHtml(html, darkTheme = false)
        assertTrue(dark.contains("#C62828", ignoreCase = true))
        assertTrue(light.contains("#C62828", ignoreCase = true))
    }

    @Test
    fun collapsesDoubleLineBreaks() {
        val html = """첫줄<br><br><br>둘째줄<p><br></p><p>셋째줄</p>"""
        val result = prepareContentHtml(html, darkTheme = true)
        assertFalse(Regex("""(?i)(<br\s*/?>\s*){2,}""").containsMatchIn(result))
        assertTrue(result.contains("첫줄"))
        assertTrue(result.contains("둘째줄"))
        assertTrue(result.contains("셋째줄"))
    }

    @Test
    fun linkifiesPlainHttpUrls() {
        val html = """<p>참고 https://youtube.com/shorts/abc 입니다</p>"""
        val result = prepareContentHtml(html, darkTheme = true)
        assertTrue(result.contains("""href="https://youtube.com/shorts/abc""""))
        assertTrue(result.contains("<a "))
    }
}
