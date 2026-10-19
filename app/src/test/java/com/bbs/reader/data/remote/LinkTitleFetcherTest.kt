package com.bbs.reader.data.remote

import com.bbs.reader.data.model.CommentItem
import com.bbs.reader.data.model.PostDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTitleFetcherTest {
    @Test
    fun collectUrls_findsPlainAndAnchorUrls() {
        val detail = PostDetail(
            id = "1",
            title = "t",
            author = "",
            time = "",
            viewCount = 0,
            voteCount = 0,
            commentCount = 1,
            contentHtml = """<p>본문 https://example.com/a 끝</p>""",
            url = "https://bbs.example.com/1",
            mid = "m",
            comments = listOf(
                CommentItem(
                    id = "c1",
                    author = "u",
                    time = "",
                    contentHtml = """<a href="https://example.com/b">https://example.com/b</a>""",
                    depth = 0,
                    isReply = false,
                    replyTo = null
                )
            )
        )
        val urls = LinkTitleFetcher.collectUrls(detail)
        assertTrue(urls.contains("https://example.com/a"))
        assertTrue(urls.contains("https://example.com/b"))
    }

    @Test
    fun collectUrls_skipsMediaAndCustomLabelLinks() {
        val detail = PostDetail(
            id = "1",
            title = "t",
            author = "",
            time = "",
            viewCount = 0,
            voteCount = 0,
            commentCount = 0,
            contentHtml = """
                <a href="https://cdn.example.com/a.jpg">https://cdn.example.com/a.jpg</a>
                <a href="https://youtube.com/watch?v=abcdefghijk">https://youtube.com/watch?v=abcdefghijk</a>
                <a href="https://example.com/page">이미 제목</a>
            """.trimIndent(),
            url = "https://bbs.example.com/1",
            mid = "m",
            comments = emptyList()
        )
        val urls = LinkTitleFetcher.collectUrls(detail)
        assertFalse(urls.any { it.contains(".jpg") })
        assertFalse(urls.any { it.contains("youtube") })
        assertFalse(urls.contains("https://example.com/page"))
    }

    @Test
    fun isUrlLikeLabel_detectsUrlText() {
        assertTrue(
            LinkTitleFetcher.isUrlLikeLabel(
                "https://example.com/x",
                "https://example.com/x"
            )
        )
        assertTrue(LinkTitleFetcher.isUrlLikeLabel("www.example.com/x", "https://www.example.com/x"))
        assertFalse(LinkTitleFetcher.isUrlLikeLabel("뉴스 보기", "https://example.com/x"))
        assertEquals(true, LinkTitleFetcher.isUrlLikeLabel("", "https://example.com/x"))
    }
}
