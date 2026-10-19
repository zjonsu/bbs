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
        val html = """<p>참고 https://example.com/page 입니다</p>"""
        val result = prepareContentHtml(html, darkTheme = true)
        assertTrue(result.contains("""href="https://example.com/page""""))
        assertTrue(result.contains("<a "))
    }

    @Test
    fun embedsImageUrlsAsImgTags() {
        val html = """<p>사진 https://cdn.example.com/pic.webp?w=800 끝</p>"""
        val result = prepareContentHtml(html, darkTheme = true)
        assertTrue(result.contains("""src="https://cdn.example.com/pic.webp?w=800""""))
        assertTrue(result.contains("<img"))
        assertFalse(result.contains("""href="https://cdn.example.com/pic.webp"""))
    }

    @Test
    fun embedsEmojiImageLinksInline() {
        val html = """<a href="https://cdn.example.com/emoji/smile.png">이모지</a>"""
        val result = prepareContentHtml(html, darkTheme = true)
        assertTrue(result.contains("""src="https://cdn.example.com/emoji/smile.png""""))
        assertTrue(result.contains("class=\"emoji\"") || result.contains("class=emoji"))
    }

    @Test
    fun embedsYoutubeLinksAsPlayer() {
        val html = """<p>참고 https://youtube.com/shorts/abc123XYZ 입니다</p>"""
        val result = prepareContentHtml(html, darkTheme = true)
        assertTrue(result.contains("youtube.com/embed/abc123XYZ"))
        assertTrue(result.contains("video-embed"))
        assertTrue(result.contains("<iframe"))
    }

    @Test
    fun embedsDirectVideoLinksAsVideoTag() {
        val html = """<p>https://cdn.example.com/clip.mp4</p>"""
        val result = prepareContentHtml(html, darkTheme = true)
        assertTrue(result.contains("<video"))
        assertTrue(result.contains("""src="https://cdn.example.com/clip.mp4""""))
        assertTrue(result.contains("controls"))
    }

    @Test
    fun promotesLazyLoadedImageSrc() {
        val html = """<img data-src="https://cdn.example.com/lazy.jpg" src="">"""
        val result = prepareContentHtml(html, darkTheme = true)
        assertTrue(result.contains("""src="https://cdn.example.com/lazy.jpg""""))
    }

    @Test
    fun stripsScriptsForSafeMediaJs() {
        val html = """<p>본문</p><script>alert(1)</script><img src="https://cdn.example.com/a.png" onclick="evil()">"""
        val result = prepareContentHtml(html, darkTheme = true)
        assertFalse(result.contains("<script"))
        assertFalse(result.contains("onclick"))
        assertTrue(result.contains("본문"))
    }

    @Test
    fun replacesUrlOnlyLinksWithFetchedTitles() {
        val html = """<p>참고 https://news.example.com/article/1 입니다</p>"""
        val titles = mapOf("https://news.example.com/article/1" to "속보: 테스트 제목")
        val result = prepareContentHtml(html, darkTheme = true, linkTitles = titles)
        assertTrue(result.contains("속보: 테스트 제목"))
        assertTrue(result.contains("""href="https://news.example.com/article/1""""))
        assertTrue(result.contains("ext-link"))
        assertFalse(result.contains(">https://news.example.com/article/1<"))
    }

    @Test
    fun keepsCustomAnchorTextWhenNotUrl() {
        val html = """<a href="https://news.example.com/a">직접 쓴 제목</a>"""
        val titles = mapOf("https://news.example.com/a" to "원격 제목")
        val result = prepareContentHtml(html, darkTheme = true, linkTitles = titles)
        assertTrue(result.contains("직접 쓴 제목"))
        assertFalse(result.contains("원격 제목"))
    }
}
