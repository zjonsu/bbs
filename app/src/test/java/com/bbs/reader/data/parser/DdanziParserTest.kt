package com.bbs.reader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DdanziParserTest {
    @Test
    fun parsePostList_extractsTitleAuthorAndCounts() {
        val html = """
            <div id="list_style" class="list_style">
              <ul>
                <li>
                  <div class="titleBox">
                    <a href="https://www.ddanzi.com/index.php?mid=free&amp;document_srl=12345" class="link mtitle">
                      <div class="title">
                        <span style="color:#a6a6a6;">[자유게시판]</span> 테스트 제목
                        <span class="talk">[12]</span>
                      </div>
                    </a>
                  </div>
                  <div class="asideBox">
                    <span class="author"><a href="#">작성자A</a></span>
                    <span class="time"><em class="bar">|</em>09:00:00</span>
                    <span class="hits"><em class="bar">|</em>100</span>
                    <span class="voteNum"><em class="bar">|</em>7</span>
                  </div>
                </li>
              </ul>
            </div>
        """.trimIndent()

        val posts = DdanziParser.parsePostList(html, "ddanzi_hot")
        assertEquals(1, posts.size)
        assertEquals("12345", posts[0].id)
        assertEquals("테스트 제목", posts[0].title)
        assertEquals("[자유게시판]", posts[0].boardLabel)
        assertEquals("작성자A", posts[0].author)
        assertEquals(12, posts[0].commentCount)
        assertEquals(100, posts[0].viewCount)
        assertEquals(7, posts[0].voteCount)
        assertEquals("free", posts[0].mid)
    }

    @Test
    fun extractCommentHtmlFromJson_handlesEscapes() {
        val json = """{"commentHtml":"<li id=\"comment_1\"><div>안녕\n세계</div></li>","error":0,"message":"success"}"""
        val html = DdanziParser.extractCommentHtmlFromJson(json)
        assertTrue(html.contains("comment_1"))
        assertTrue(html.contains("안녕\n세계"))
    }
}
