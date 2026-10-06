package com.bbs.reader.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HumorunivParserTest {
    @Test
    fun parsePdsBestList_extractsRankedAndNormalItems() {
        val html = """
            <html><body>
              <a href="/board/read.html?table=pds&page=0&number=1427547&from=best" class="li">
                <li id="list_best_normal_li">
                  <div class="rank_num">1</div>
                  장례식장에서 싸운 썰
                  <font color=red>760</font>
                  <div style="display:inline-block;color:#999;font-size:10px;">겨울사람</div>
                </li>
              </a>
              <a href="read.html?table=pds&number=1427658" class="list_body_href post_link">
                <li id="li_chk_pds-1427658">
                  <span id="title_chk_pds-1427658" class="link_hover">지나친 국뽕은 경계해야한다.jpg</span>
                  <span class="ok_num">42</span>
                  <span class="comment_num">3</span>
                </li>
              </a>
            </body></html>
        """.trimIndent()

        val posts = HumorunivParser.parsePostList(
            html,
            "humoruniv_pds_best",
            HumorunivParser.ListMode.PDS_BEST
        )
        assertEquals(2, posts.size)
        assertEquals("1427547", posts[0].id)
        assertEquals("장례식장에서 싸운 썰", posts[0].title)
        assertEquals(760, posts[0].voteCount)
        assertEquals("겨울사람", posts[0].author)
        assertEquals("humoruniv:pds", posts[0].mid)
        assertEquals("1427658", posts[1].id)
        assertEquals("지나친 국뽕은 경계해야한다.jpg", posts[1].title)
        assertEquals(42, posts[1].voteCount)
        assertEquals(3, posts[1].commentCount)
    }

    @Test
    fun parseSsudaBestList_extractsBoardLabelAndComments() {
        val html = """
            <html><body>
              <div id="main_list_best" class="bg0">
                <div class="title bg1">웃긴자료 베스트</div>
              </div>
              <div id="main_list_best" class="bg0">
                <div class="title bg1">웃수다 베스트</div>
                <div class="main_thumb_type">
                  <a href="/board/humor/read.html?table=pride&number=1342043">
                    <dd><span id="title_chk_pride-1342043">[스포츠] 아 뭐 빠진 것 같은데.. 뭐더라.. </span><em>28</em></dd>
                  </a>
                  <a href="/board/humor/read.html?table=workshop&number=140128">
                    <dd><span id="title_chk_workshop-140128">[직장] 알바 합격했어요 </span><em>23</em></dd>
                  </a>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val posts = HumorunivParser.parsePostList(
            html,
            "humoruniv_ssuda_best",
            HumorunivParser.ListMode.SSUDA_BEST
        )
        assertEquals(2, posts.size)
        assertEquals("1342043", posts[0].id)
        assertEquals("스포츠", posts[0].boardLabel)
        assertEquals("아 뭐 빠진 것 같은데.. 뭐더라..", posts[0].title)
        assertEquals(28, posts[0].commentCount)
        assertEquals("humoruniv:pride", posts[0].mid)
        assertEquals("workshop", HumorunivParser.tableOfMid(posts[1].mid))
        assertTrue(posts[0].url.contains("table=pride"))
    }

    @Test
    fun parsePostDetail_extractsContentAuthorAndComments() {
        val html = """
            <html><body>
              <div id="read_subject_div">
                <h2><a href="#"><span class="color_dark">테스트 제목</span></a> <span class="comment_num">[2]</span></h2>
              </div>
              <div id="content_info">
                <span class="nick"><span class="hu_nick_txt">겨울사람</span></span>
                <p id="read_profile_desc">
                  <span class="etc">작성 2026-10-04 09:33:31</span>
                  <span class="ok"><span id="ok_div">&#55;&#54;&#48;</span></span>
                  <span class="comment_num">2</span>
                  <span class="etc"><img src="/images/ic_view.png"> 40,172</span>
                </p>
              </div>
              <div class="daum-wm-content">
                <div class="body_editor">
                  <img src="//down-webp.humoruniv.com/a.webp" />
                  <p>본문입니다</p>
                </div>
              </div>
              <ul>
                <li id="comment_li_1">
                  <span class="nick"><span class="hu_nick_txt">댓글러</span></span>
                  <span class="etc">2026-10-05 <bMon> 17:41:20</span>
                  <div class="comment_more">첫댓글</div>
                </li>
                <li name="sub_comm_block" id="comment_li_2" class="sub_comm_bt">
                  <span class="nick"><span class="hu_nick_txt">대댓</span></span>
                  <span class="etc">2026-10-05 17:42:00</span>
                  <div class="comment_more">대댓글</div>
                </li>
              </ul>
            </body></html>
        """.trimIndent()

        val detail = HumorunivParser.parsePostDetail(
            html,
            "https://m.humoruniv.com/board/read.html?table=pds&number=1",
            "humoruniv:pds",
            "1"
        )
        assertEquals("테스트 제목", detail.title)
        assertEquals("겨울사람", detail.author)
        assertEquals("2026-10-04 09:33:31", detail.time)
        assertEquals(760, detail.voteCount)
        assertTrue(detail.contentHtml.contains("본문입니다"))
        assertTrue(detail.contentHtml.contains("https://down-webp.humoruniv.com/a.webp"))
        assertEquals(2, detail.comments.size)
        assertEquals("댓글러", detail.comments[0].author)
        assertEquals(0, detail.comments[0].depth)
        assertEquals(1, detail.comments[1].depth)
        assertTrue(detail.comments[1].isReply)
        assertEquals("2026-10-05 17:41:20", detail.comments[0].time)
    }
}
