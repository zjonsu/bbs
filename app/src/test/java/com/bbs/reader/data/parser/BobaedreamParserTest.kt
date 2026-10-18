package com.bbs.reader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BobaedreamParserTest {
    @Test
    fun parsePostList_extractsItems() {
        val html = """
            <html><body>
              <ul class="rank">
                <li>
                  <div class="info">
                    <a href="/board/bbs_view/best/1040725/2/1">
                      <div class="txt"><span class="cont">무슬림 존중을 안 하는, 한국 여행은 최악</span></div>
                      <div class="txt2">
                        <span class="block">신유머</span>
                        <span class="block">tinyjoy</span>
                        <span class="block">11:41</span>
                        <span class="block">조회 2372</span>
                        <span class="block" style="color:#A02928;">추천 41</span>
                      </div>
                    </a>
                    <div class="txt5"><span class="num">11</span></div>
                  </div>
                </li>
                <li>
                  <div class="info">
                    <a href="/board/bbs_view/best/1040728/2/1">
                      <div class="txt"><span class="cont">와이프가 딸 같다는 블라인</span></div>
                      <div class="txt2">
                        <span class="block">신유머</span>
                        <span class="block">영자의전성시대</span>
                        <span class="block">11:39</span>
                        <span class="block">조회 1,313</span>
                        <span class="block">추천 21</span>
                      </div>
                    </a>
                    <div class="txt5"><span class="num">3</span></div>
                  </div>
                </li>
              </ul>
            </body></html>
        """.trimIndent()

        val posts = BobaedreamParser.parsePostList(html, "bobaedream_best", "best")
        assertEquals(2, posts.size)
        assertEquals("1040725", posts[0].id)
        assertEquals("무슬림 존중을 안 하는, 한국 여행은 최악", posts[0].title)
        assertEquals("신유머", posts[0].boardLabel)
        assertEquals("tinyjoy", posts[0].author)
        assertEquals("11:41", posts[0].time)
        assertEquals(2372, posts[0].viewCount)
        assertEquals(41, posts[0].voteCount)
        assertEquals(11, posts[0].commentCount)
        assertEquals("bobaedream:best", posts[0].mid)
        assertEquals(1313, posts[1].viewCount)
        assertEquals(3, posts[1].commentCount)
    }

    @Test
    fun parsePostDetail_extractsContentAndComments() {
        val html = """
            <html><body>
              <header class="article-tit">
                <div class="title"><h3 class="subject">테스트 제목</h3><span class="data1">(5)</span></div>
                <div class="util">
                  <time>11:41</time>
                  <span class="data3">추천 41</span>
                  <span class="data4">조회 2390</span>
                </div>
                <div class="util2"><div class="info"><span>tinyjoy</span></div></div>
              </header>
              <div class="article-body">
                <p><img src="//file1.bobaedream.co.kr/a.jpg"></p>
                <p>본문입니다</p>
              </div>
              <div class="reple_body">
                <ul class="list">
                  <li class="best">
                    <div class="con_area">
                      <div class="reply"><span class="ico3">베플</span>베스트 댓글</div>
                      <div class="util"><span class="data4">유저A</span><span>12:10</span></div>
                      <div class="util4" id="repl_456709"></div>
                    </div>
                  </li>
                  <li>
                    <div class="con_area">
                      <div class="reply">일반 댓글</div>
                      <div class="util"><span class="data4">유저B</span><span>11:49</span></div>
                    </div>
                  </li>
                </ul>
              </div>
            </body></html>
        """.trimIndent()

        val detail = BobaedreamParser.parsePostDetail(
            html,
            "https://m.bobaedream.co.kr/board/bbs_view/best/1040725",
            "bobaedream:best",
            "1040725"
        )
        assertEquals("테스트 제목", detail.title)
        assertEquals("tinyjoy", detail.author)
        assertEquals(2390, detail.viewCount)
        assertEquals(41, detail.voteCount)
        assertTrue(detail.contentHtml.contains("본문입니다"))
        assertTrue(detail.contentHtml.contains("https://file1.bobaedream.co.kr/a.jpg"))
        assertEquals(2, detail.comments.size)
        assertEquals("유저A", detail.comments[0].author)
        assertTrue(detail.comments[0].contentHtml.contains("베스트 댓글"))
        assertTrue(!detail.comments[0].contentHtml.contains("베플") || detail.comments[0].contentHtml.contains("베스트"))
    }

    @Test
    fun listUrl_buildsPages() {
        assertEquals(
            "https://m.bobaedream.co.kr/board/new_writing/best",
            BobaedreamParser.listUrl("best", 1)
        )
        assertEquals(
            "https://m.bobaedream.co.kr/board/new_writing/best/3",
            BobaedreamParser.listUrl("best", 3)
        )
    }
}
