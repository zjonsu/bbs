package com.bbs.reader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DamoangParserTest {
    @Test
    fun parsePostList_recommendExtractsFields() {
        val html = """
            <ul class="divide-border divide-y">
              <li>
                <a href="/free/7497952" class="hover:bg-muted flex items-center gap-2.5 px-4 py-2">
                  <span class="inline-flex rounded-full px-2 py-0.5 text-xs font-bold">480</span>
                  <span class="bg-muted text-muted-foreground rounded px-1.5 py-0.5 text-xs">자유게시판</span>
                  <span class="min-w-0 flex-1 truncate">유해진 소속사 공지 올렸네요</span>
                  <span class="text-primary shrink-0 text-xs font-medium">[44]</span>
                  <span class="text-muted-foreground hidden sm:flex">
                    <span>5.8k</span>
                    <span>8시간 전</span>
                  </span>
                </a>
              </li>
            </ul>
        """.trimIndent()

        val posts = DamoangParser.parsePostList(html, "damoang_empathy")
        assertEquals(1, posts.size)
        assertEquals("7497952", posts[0].id)
        assertEquals("유해진 소속사 공지 올렸네요", posts[0].title)
        assertEquals(44, posts[0].commentCount)
        assertEquals(480, posts[0].voteCount)
        assertEquals("damoang:free", posts[0].mid)
        assertEquals("https://damoang.net/free/7497952", posts[0].url)
    }

    @Test
    fun parsePostList_groupEmpathyUsesWidgetRows() {
        val html = """
            <div data-slot="card">
              <div data-slot="card-header">
                <a href="/groups" class="hover:text-foreground"><h3>소모임 공감글</h3></a>
              </div>
              <ul>
                <li>
                  <a href="/stock/21160" class="hover:bg-muted widget-post-row">
                    <span class="rounded-full">26</span>
                    <span class="bg-muted">주식</span>
                    <span class="min-w-0 flex-1 truncate">삼성전자 정리</span>
                  </a>
                </li>
              </ul>
            </div>
            <a href="/free/1" class="widget-post-row"><span class="truncate">다른위젯</span></a>
        """.trimIndent()

        val posts = DamoangParser.parsePostList(
            html,
            "damoang_group_empathy",
            DamoangParser.ListMode.GROUP_EMPATHY
        )
        assertEquals(1, posts.size)
        assertEquals("21160", posts[0].id)
        assertEquals("damoang:stock", posts[0].mid)
        assertEquals("삼성전자 정리", posts[0].title)
    }

    @Test
    fun parsePostDetailJson_andComments() {
        val detailJson = """
            {"data":{"author":"작성자","comments_count":2,"content":"<p>본문</p>","created_at":"2026-10-05T09:26:22+09:00","id":1,"likes":10,"title":"제목","views":100},"success":true}
        """.trimIndent()
        val detail = DamoangParser.parsePostDetailJson(detailJson, "free", "1")
        assertEquals("제목", detail.title)
        assertEquals("작성자", detail.author)
        assertEquals(100, detail.viewCount)
        assertEquals(10, detail.voteCount)
        assertTrue(detail.contentHtml.contains("본문"))
        assertEquals("damoang:free", detail.mid)

        val commentsJson = """
            {"data":[{"author":"댓글러","content":"<p>안녕하세요</p>","created_at":"2026-10-05T09:30:30+09:00","depth":0,"id":11},{"author":"대댓","content":"<p>네</p>","created_at":"2026-10-05T09:31:00+09:00","depth":1,"id":12}]}
        """.trimIndent()
        val comments = DamoangParser.parseCommentsJson(commentsJson)
        assertEquals(2, comments.size)
        assertEquals("댓글러", comments[0].author)
        assertEquals(1, comments[1].depth)
        assertTrue(comments[1].isReply)
    }
}
