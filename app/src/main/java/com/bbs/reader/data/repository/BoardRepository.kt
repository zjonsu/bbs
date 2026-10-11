package com.bbs.reader.data.repository

import com.bbs.reader.data.model.BoardInfo
import com.bbs.reader.data.model.CommentItem
import com.bbs.reader.data.model.LoginRequiredException
import com.bbs.reader.data.model.PostDetail
import com.bbs.reader.data.model.PostSummary
import com.bbs.reader.data.parser.AagagParser
import com.bbs.reader.data.parser.DamoangParser
import com.bbs.reader.data.parser.DdanziParser
import com.bbs.reader.data.parser.HumorunivParser
import com.bbs.reader.data.parser.SlrclubParser
import com.bbs.reader.data.remote.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class BoardRepository {
    /** SLR 페이지 커서: boardId → page2 의 숫자 ID */
    private val slrPage2Ids = ConcurrentHashMap<String, Int>()

    suspend fun fetchPosts(boardId: String, page: Int = 1): List<PostSummary> =
        withContext(Dispatchers.IO) {
            val board = CommunityCatalog.findBoard(boardId)
                ?: throw IllegalArgumentException("알 수 없는 게시판: $boardId")
            when (board.communityId) {
                CommunityCatalog.AAGAG_ID -> fetchAagagPosts(board, page)
                CommunityCatalog.DAMOANG_ID -> fetchDamoangPosts(board, page)
                CommunityCatalog.HUMORUNIV_ID -> fetchHumorunivPosts(board, page)
                CommunityCatalog.SLRCLUB_ID -> fetchSlrclubPosts(board, page)
                else -> fetchDdanziPosts(board, page)
            }
        }

    suspend fun fetchPostDetail(mid: String, postId: String): PostDetail =
        withContext(Dispatchers.IO) {
            when {
                CommunityCatalog.isAagagMid(mid) -> fetchAagagDetail(mid, postId)
                CommunityCatalog.isDamoangMid(mid) -> fetchDamoangDetail(mid, postId)
                CommunityCatalog.isHumorunivMid(mid) -> fetchHumorunivDetail(mid, postId)
                CommunityCatalog.isSlrclubMid(mid) -> fetchSlrclubDetail(mid, postId)
                else -> fetchDdanziDetail(mid, postId)
            }
        }

    private fun fetchDdanziPosts(board: BoardInfo, page: Int): List<PostSummary> {
        val url = if (page <= 1) {
            board.listPath
        } else {
            val mid = board.listPath.substringAfterLast('/')
            "https://www.ddanzi.com/index.php?mid=$mid&page=$page"
        }
        val html = HttpClient.get(url)
        return DdanziParser.parsePostList(html, board.id)
    }

    private fun fetchDdanziDetail(mid: String, postId: String): PostDetail {
        val url = "https://www.ddanzi.com/$mid/$postId"
        val html = HttpClient.get(url, referer = "https://www.ddanzi.com/")
        val detail = DdanziParser.parsePostDetail(html, url, mid, postId)
        val comments = fetchDdanziComments(mid, postId, url)
        return detail.copy(
            comments = comments,
            commentCount = if (comments.isNotEmpty()) comments.size else detail.commentCount
        )
    }

    private fun fetchDdanziComments(mid: String, postId: String, referer: String): List<CommentItem> {
        return try {
            val json = HttpClient.postXeJson(
                url = "https://www.ddanzi.com/",
                fields = mapOf(
                    "module" to "board",
                    "act" to "dispBoardContentCommentListHtml",
                    "mid" to mid,
                    "document_srl" to postId,
                    "cpage" to "1"
                ),
                referer = referer
            )
            val commentHtml = DdanziParser.extractCommentHtmlFromJson(json)
            DdanziParser.parseComments(commentHtml)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchAagagPosts(board: BoardInfo, page: Int): List<PostSummary> {
        val base = board.listPath.trimEnd('/')
        val url = if (page <= 1) "$base/" else "$base/?page=$page"
        val html = HttpClient.get(url, referer = "https://aagag.com/")
        val mid = base.substringAfterLast('/')
        return AagagParser.parsePostList(html, board.id, mid)
    }

    private fun fetchAagagDetail(mid: String, postId: String): PostDetail {
        val url = "https://aagag.com/$mid/?idx=$postId"
        val html = HttpClient.get(url, referer = "https://aagag.com/$mid/")
        val detail = AagagParser.parsePostDetail(html, url, mid, postId)
        val comments = fetchAagagComments(postId, url)
        return detail.copy(
            comments = comments,
            commentCount = if (comments.isNotEmpty()) comments.size else detail.commentCount
        )
    }

    private fun fetchAagagComments(postId: String, referer: String): List<CommentItem> {
        return try {
            val json = HttpClient.postForm(
                url = "https://aagag.com/api/cmt",
                fields = mapOf("idx" to postId),
                referer = referer
            )
            AagagParser.parseCommentsJson(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchDamoangPosts(board: BoardInfo, page: Int): List<PostSummary> {
        // 목록은 SSR HTML 기준. page>1 은 사이트에 따라 막히므로 첫 페이지만 제공.
        if (page > 1) return emptyList()
        val html = HttpClient.get(board.listPath, referer = "https://damoang.net/")
        val mode = when (board.id) {
            CommunityCatalog.DAMOANG_GROUP_EMPATHY -> DamoangParser.ListMode.GROUP_EMPATHY
            else -> DamoangParser.ListMode.RECOMMEND
        }
        return DamoangParser.parsePostList(html, board.id, mode)
    }

    private fun fetchDamoangDetail(mid: String, postId: String): PostDetail {
        val board = DamoangParser.boardOfMid(mid)
            ?: throw IllegalArgumentException("잘못된 다모앙 mid: $mid")
        val pageUrl = "https://damoang.net/$board/$postId"
        val json = HttpClient.get(
            url = "https://damoang.net/api/v1/boards/$board/posts/$postId",
            referer = pageUrl
        )
        val detail = DamoangParser.parsePostDetailJson(json, board, postId)
        val comments = fetchDamoangComments(board, postId, pageUrl)
        return detail.copy(
            comments = comments,
            commentCount = if (comments.isNotEmpty()) comments.size else detail.commentCount
        )
    }

    private fun fetchDamoangComments(board: String, postId: String, referer: String): List<CommentItem> {
        return try {
            val json = HttpClient.get(
                url = "https://damoang.net/api/v1/boards/$board/posts/$postId/comments?page=1&limit=200",
                referer = referer
            )
            DamoangParser.parseCommentsJson(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchHumorunivPosts(board: BoardInfo, page: Int): List<PostSummary> {
        val mode = when (board.id) {
            CommunityCatalog.HUMORUNIV_SSUDA_BEST -> HumorunivParser.ListMode.SSUDA_BEST
            else -> HumorunivParser.ListMode.PDS_BEST
        }
        // 웃수다 베스트는 메인 위젯이라 1페이지만.
        if (mode == HumorunivParser.ListMode.SSUDA_BEST && page > 1) return emptyList()

        val url = when {
            mode == HumorunivParser.ListMode.PDS_BEST && page > 1 ->
                "${board.listPath}&pg=${page - 1}"
            else -> board.listPath
        }
        val html = HttpClient.get(
            url = url,
            referer = "https://m.humoruniv.com/main.html",
            forceCharset = "EUC-KR"
        )
        return HumorunivParser.parsePostList(html, board.id, mode)
    }

    private fun fetchHumorunivDetail(mid: String, postId: String): PostDetail {
        val table = HumorunivParser.tableOfMid(mid)
            ?: throw IllegalArgumentException("잘못된 웃대 mid: $mid")
        val url = HumorunivParser.detailUrl(table, postId)
        val html = HttpClient.get(
            url = url,
            referer = "https://m.humoruniv.com/main.html",
            forceCharset = "EUC-KR"
        )
        return HumorunivParser.parsePostDetail(html, url, mid, postId)
    }

    private fun fetchSlrclubPosts(board: BoardInfo, page: Int): List<PostSummary> {
        val base = board.listPath.trimEnd('/')
        val slug = base.substringAfterLast('/')
        val url = when {
            page <= 1 -> base
            else -> {
                val page2Id = slrPage2Ids[board.id]
                    ?: run {
                        val firstHtml = HttpClient.get(base, referer = SlrclubParser.BASE)
                        if (SlrclubParser.isLoginRequired(firstHtml)) {
                            throw LoginRequiredException(
                                communityId = board.communityId,
                                siteName = CommunityCatalog.findCommunity(board.communityId)?.name
                                    ?: "SLR클럽"
                            )
                        }
                        val id = SlrclubParser.extractPage2Id(firstHtml)
                        if (id != null) slrPage2Ids[board.id] = id
                        id
                    }
                if (page2Id == null) return emptyList()
                val pageId = page2Id - (page - 2)
                if (pageId < 1) return emptyList()
                "$base/$pageId"
            }
        }
        val html = HttpClient.get(url, referer = SlrclubParser.BASE)
        if (SlrclubParser.isLoginRequired(html)) {
            throw LoginRequiredException(
                communityId = board.communityId,
                siteName = CommunityCatalog.findCommunity(board.communityId)?.name ?: "SLR클럽"
            )
        }
        if (page <= 1) {
            SlrclubParser.extractPage2Id(html)?.let { slrPage2Ids[board.id] = it }
        }
        return SlrclubParser.parsePostList(html, board.id, slug)
    }

    private fun fetchSlrclubDetail(mid: String, postId: String): PostDetail {
        val slug = SlrclubParser.boardOfMid(mid)
            ?: throw IllegalArgumentException("잘못된 SLR mid: $mid")
        val url = SlrclubParser.detailUrl(slug, postId)
        val html = HttpClient.get(url, referer = "$BASE_SLR/l/$slug")
        if (SlrclubParser.isLoginRequired(html)) {
            throw LoginRequiredException(
                communityId = CommunityCatalog.SLRCLUB_ID,
                siteName = "SLR클럽"
            )
        }
        val detail = SlrclubParser.parsePostDetail(html, url, mid, postId)
        val comments = fetchSlrclubComments(html, url)
        return detail.copy(
            comments = comments,
            commentCount = if (comments.isNotEmpty()) comments.size else detail.commentCount
        )
    }

    private fun fetchSlrclubComments(detailHtml: String, referer: String): List<CommentItem> {
        return try {
            val params = SlrclubParser.extractCommentParams(detailHtml) ?: return emptyList()
            val json = HttpClient.postForm(
                url = "$BASE_SLR/bbs/comment_db/load.php",
                fields = mapOf(
                    "id" to params.bbsId,
                    "tos" to params.tos,
                    "no" to params.cmrNo,
                    "sno" to params.page,
                    "spl" to params.split,
                    "mno" to params.cmx,
                    "gp" to "mobile",
                    "ksearch" to ""
                ),
                referer = referer
            )
            SlrclubParser.parseCommentsJson(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val BASE_SLR = "https://m.slrclub.com"
    }
}
