package com.bbs.reader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AagagParserTest {
    @Test
    fun parsePostList_extractsTitleCountsAndIdx() {
        val html = """
            <div class="list">
              <a class="article t" href="/issue/?idx=1659590" ss="issue_1659590">
                <div class="thumb"></div>
                <span class="title">테스트 이슈 제목
                  <span class="btmlayer">
                    <span class="byte"><u>1.2 MB</u></span>
                    <span class="hit"><u>458</u></span>
                    <span class="good"><u><i class="f_aicon navit"></i>3</u></span>
                    <span class="good"><u><i class="f_aicon good"></i>2</u></span>
                    <span class="time right"><u>5분전</u></span>
                  </span>
                </span>
              </a>
              <a class="article t" href="/issue/?idx=509613_1" ss="issue_509613_1">
                <span class="title">언더스코어 아이디
                  <span class="btmlayer">
                    <span class="hit"><u>100</u></span>
                    <span class="good"><u>1</u></span>
                    <span class="good"><u>0</u></span>
                    <span class="time right"><u>1시간전</u></span>
                  </span>
                </span>
              </a>
            </div>
        """.trimIndent()

        val posts = AagagParser.parsePostList(html, "aagag_issue", "issue")
        assertEquals(2, posts.size)
        assertEquals("1659590", posts[0].id)
        assertEquals("테스트 이슈 제목", posts[0].title)
        assertEquals(3, posts[0].commentCount)
        assertEquals(458, posts[0].viewCount)
        assertEquals(2, posts[0].voteCount)
        assertEquals("issue", posts[0].mid)
        assertEquals("https://aagag.com/issue/?idx=1659590", posts[0].url)
        assertEquals("509613_1", posts[1].id)
    }

    @Test
    fun convertStags_replacesImgTags() {
        val raw = """<p>[sTag]{"m":"img","q":"KyA9Z","width":900,"height":740}[/sTag]</p>"""
        val html = AagagParser.convertStags(raw)
        assertTrue(html.contains("https://i.aagag.com/o/KyA9Z.webp"))
        assertTrue(html.contains("<img"))
    }

    @Test
    fun parsePostDetail_readsTitleContentAndMeta() {
        val html = """
            <html><body>
              <h1 class="title">상세 제목</h1>
              <h4 class="other ns"><span class="t odate">2026-10-05 17:38</span></h4>
              <a class="article now t on" href="/issue/?idx=1">
                <span class="title">x
                  <span class="btmlayer">
                    <span class="hit"><u>99</u></span>
                    <span class="good"><u>4</u></span>
                    <span class="good"><u>7</u></span>
                  </span>
                </span>
              </a>
              <div id="vContent"></div>
              <script>
              AAGAG_AA.title = "상세 제목";
              AAGAG_AA.content = "<p>[sTag]{\"m\":\"img\",\"q\":\"AbC12\",\"width\":100,\"height\":100}[/sTag]</p>";
              AAGAG_AA.cmt_cnt = 14;
              </script>
            </body></html>
        """.trimIndent()

        val detail = AagagParser.parsePostDetail(
            html,
            "https://aagag.com/issue/?idx=1",
            "issue",
            "1"
        )
        assertEquals("상세 제목", detail.title)
        assertEquals("2026-10-05 17:38", detail.time)
        assertEquals(99, detail.viewCount)
        assertEquals(7, detail.voteCount)
        assertEquals(14, detail.commentCount)
        assertTrue(detail.contentHtml.contains("AbC12.webp"))
    }

    @Test
    fun parseCommentsJson_extractsNestedReplies() {
        val json = """
            {"mode":"success","total":2,"comment":[
              {"w_idx":8806034,"w_cmt_reply":null,"w_nick":"블랑","w_content":"첫댓<br>글","w_good":2,"w_bad":0,"stime":"2026-10-05 17:40:54","state":1},
              {"w_idx":8806036,"w_cmt_reply":"01|","w_nick":"지그재그","w_content":"대댓글","w_good":1,"w_bad":0,"stime":"2026-10-05 17:41:05","state":1}
            ]}
        """.trimIndent()

        val comments = AagagParser.parseCommentsJson(json)
        assertEquals(2, comments.size)
        assertEquals("8806034", comments[0].id)
        assertEquals("블랑", comments[0].author)
        assertTrue(comments[0].contentHtml.contains("첫댓"))
        assertEquals(0, comments[0].depth)
        assertEquals(1, comments[1].depth)
        assertTrue(comments[1].isReply)
    }
}
