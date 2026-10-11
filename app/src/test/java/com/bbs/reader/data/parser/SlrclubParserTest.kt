package com.bbs.reader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlrclubParserTest {
    @Test
    fun parsePostList_extractsItems() {
        val html = """
            <html><body>
              <ul class="list">
                <li>
                  <div class="article">
                    <div class="subject"><a href="/v/hot_article/1462767">헉 유해진</a></div>
                    <div class="article-info"><span class="lop">SLR회원</span> | 19:47 | 조회 429 </div>
                  </div>
                  <div class="cmt2">6</div>
                </li>
                <li>
                  <div class="article">
                    <div class="subject"><a href="/v/hot_article/1462763">소박한 저녁</a></div>
                    <div class="article-info"><span class="lop">날아라</span> | 18:23 | 조회 1,813 | 추천 1 </div>
                  </div>
                  <div class="cmt2">17</div>
                </li>
              </ul>
            </body></html>
        """.trimIndent()

        val posts = SlrclubParser.parsePostList(html, "slrclub_hot", "hot_article")
        assertEquals(2, posts.size)
        assertEquals("1462767", posts[0].id)
        assertEquals("헉 유해진", posts[0].title)
        assertEquals("SLR회원", posts[0].author)
        assertEquals("19:47", posts[0].time)
        assertEquals(429, posts[0].viewCount)
        assertEquals(6, posts[0].commentCount)
        assertEquals("slrclub:hot_article", posts[0].mid)
        assertEquals(1813, posts[1].viewCount)
        assertEquals(1, posts[1].voteCount)
    }

    @Test
    fun parsePostDetail_extractsContent() {
        val html = """
            <html><body>
              <div class="subject">헉 유해진</div>
              <div class="info-wrap">
                <span class="lop">SLR회원</span> | 19:47 | 조회 437 | 추천 0
              </div>
              <div id="userct"><p>본문입니다</p><img src="//media.slrclub.com/a.jpg"></div>
              <span id="cmcnt">6</span>
              <div id="comment_box" data-bbsid="free" data-tos="44u8Z" data-cmrno="17qm3p"
                   data-cmx="6" data-npage="1" data-splno="50"></div>
            </body></html>
        """.trimIndent()

        val detail = SlrclubParser.parsePostDetail(
            html,
            "https://m.slrclub.com/v/hot_article/1462767",
            "slrclub:hot_article",
            "1462767"
        )
        assertEquals("헉 유해진", detail.title)
        assertEquals("SLR회원", detail.author)
        assertEquals(437, detail.viewCount)
        assertTrue(detail.contentHtml.contains("본문입니다"))
        assertTrue(detail.contentHtml.contains("https://media.slrclub.com/a.jpg"))

        val params = SoftAssertCommentParams(html)
        assertEquals("free", params.bbsId)
        assertEquals("17qm3p", params.cmrNo)
    }

    @Test
    fun isLoginRequired_detectsGate() {
        val html = """
            <div id="err_ct"><p class="err_msg">로그인 후 이용해주세요!</p></div>
        """.trimIndent()
        assertTrue(SlrclubParser.isLoginRequired(html))
        assertFalse(SlrclubParser.isLoginRequired("<ul class=\"list\"><li>ok</li></ul>"))
    }

    @Test
    fun extractPage2Id_fromPaging() {
        val html = """
            <div class="paging">
              <span id="actpg">1</span>
              <a href="/l/hot_article/38128">2</a>
              <a href="/l/hot_article/38127">3</a>
            </div>
        """.trimIndent()
        assertEquals(38128, SoftAssertPage2(html))
    }

    @Test
    fun parseCommentsJson_extractsReplies() {
        val json = """
            {"cmx":"2","c":[
              {"pk":"a1","name":"유저A","memo":"댓글1","dt":"19:49","del":0,"reply":0,"tn":""},
              {"pk":"a2","name":"유저B","memo":"답글","dt":"19:51","del":0,"reply":0,"tn":"유저A"}
            ]}
        """.trimIndent()
        val comments = SoftAssertComments(json)
        assertEquals(2, comments.size)
        assertEquals("유저A", comments[0].author)
        assertFalse(comments[0].isReply)
        assertTrue(comments[1].isReply)
        assertEquals("유저A", comments[1].replyTo)
    }

    @Test
    fun parseCommentsJson_decodesUnicodeEscapes() {
        val json = """
            {"cmx":"1","c":[
              {"pk":"boclc7","name":"\ubc1c\ub354\uc2a4\uac8c\uc774\ud2b8\uc0bc",
               "memo":" \uae40\ud61c\uc218\uc640 \uc2e4\uc81c \uc0ac\uadc0\uae30\ub3c4 \ud588\uc2b5\ub2c8\ub2e4.",
               "dt":"19:49","del":0,"reply":0,"tn":""}
            ]}
        """.trimIndent()
        val comments = SoftAssertComments(json)
        assertEquals(1, comments.size)
        assertEquals("발더스게이트삼", comments[0].author)
        assertTrue(comments[0].contentHtml.contains("김혜수와 실제 사귀기도 했습니다."))
    }

    private fun SoftAssertCommentParams(html: String) =
        SlrclubParser.extractCommentParams(html)!!

    private fun SoftAssertPage2(html: String) =
        SlrclubParser.extractPage2Id(html)

    private fun SoftAssertComments(json: String) =
        SlrclubParser.parseCommentsJson(json)
}
