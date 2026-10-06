package com.bbs.reader.data.parser

import com.bbs.reader.data.model.CommentItem
import com.bbs.reader.data.model.PostDetail
import com.bbs.reader.data.model.PostSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.regex.Pattern

object HumorunivParser {
    const val MID_PREFIX = "humoruniv:"
    private const val BASE = "https://m.humoruniv.com"

    private val numberInQuery = Pattern.compile("[?&]number=(\\d+)")
    private val tableInQuery = Pattern.compile("[?&]table=([a-zA-Z0-9_]+)")
    private val digitPattern = Pattern.compile("(\\d[\\d,]*)")
    private val boardLabelPattern = Pattern.compile("^\\[([^\\]]+)\\]\\s*(.*)$")
    private val writtenAtPattern = Pattern.compile("작성\\s*(\\d{4}-\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2}:\\d{2})")

    enum class ListMode {
        PDS_BEST,
        SSUDA_BEST
    }

    fun midOf(table: String): String = "$MID_PREFIX$table"

    fun tableOfMid(mid: String): String? =
        if (mid.startsWith(MID_PREFIX)) mid.removePrefix(MID_PREFIX) else null

    fun detailUrl(table: String, number: String): String =
        "$BASE/board/read.html?table=$table&number=$number"

    fun parsePostList(html: String, boardId: String, mode: ListMode): List<PostSummary> {
        return when (mode) {
            ListMode.PDS_BEST -> parsePdsBestList(html, boardId)
            ListMode.SSUDA_BEST -> parseSsudaBestList(html, boardId)
        }
    }

    private fun parsePdsBestList(html: String, boardId: String): List<PostSummary> {
        val doc = Jsoup.parse(html, BASE)
        val result = mutableListOf<PostSummary>()
        val seen = mutableSetOf<String>()

        // 상단 베스트 랭킹
        for (a in doc.select("a.li[href*=number=]")) {
            val post = parsePdsAnchor(a, boardId, preferVoteAsOk = true) ?: continue
            if (seen.add(post.id)) result += post
        }

        // 일반 목록(추가 페이지/하단)
        for (a in doc.select("a.list_body_href[href*=number=], a.post_link[href*=number=]")) {
            val post = parsePdsAnchor(a, boardId, preferVoteAsOk = false) ?: continue
            if (seen.add(post.id)) result += post
        }

        return result
    }

    private fun parsePdsAnchor(
        a: Element,
        boardId: String,
        preferVoteAsOk: Boolean
    ): PostSummary? {
        val href = a.attr("href")
        val id = extractNumber(href) ?: return null
        val table = extractTable(href) ?: "pds"

        val title = a.selectFirst("span[id^=title_chk_], span.link_hover")?.text()?.trim()
            ?: a.ownText().trim().ifBlank {
                a.text()
                    .replace(Regex("\\d+\\s*$"), "")
                    .trim()
            }
        // rank/author 숫자 제거용: font[color=red] 는 추천수
        val cleanTitle = when {
            !title.isNullOrBlank() && !title.matches(Regex("^\\d+$")) -> {
                // a.text() fallback일 때 추천수·닉이 붙을 수 있음
                val fontOk = a.selectFirst("font[color=red], font[color=RED]")?.text()?.trim()
                var t = title
                if (!fontOk.isNullOrBlank()) t = t.replace(fontOk, "").trim()
                val authorHint = a.selectFirst("div[style*=color:#999]")?.text()?.trim()
                if (!authorHint.isNullOrBlank()) t = t.replace(authorHint, "").trim()
                t
            }
            else -> ""
        }.ifBlank {
            // best li: 텍스트 노드에서 font/div/rank 제외
            val clone = a.clone()
            clone.select("font, div, script, .rank_num").remove()
            clone.text().trim()
        }.replace(Regex("^\\d+\\s+"), "").trim()
        if (cleanTitle.isBlank()) return null

        val author = a.selectFirst("div[style*=color:#999], .nick, .hu_nick_txt")?.text()?.trim().orEmpty()
        val voteCount = extractDigits(
            a.selectFirst("font[color=red], font[color=RED], span.ok_num")?.text().orEmpty()
        )
        val commentCount = extractDigits(a.selectFirst("span.comment_num")?.text().orEmpty())
        val viewCount = extractDigits(a.selectFirst("span.view_num, span.hits")?.text().orEmpty())

        return PostSummary(
            id = id,
            boardId = boardId,
            title = cleanTitle,
            boardLabel = null,
            author = author,
            time = "",
            commentCount = commentCount,
            viewCount = viewCount,
            voteCount = if (preferVoteAsOk || voteCount > 0) voteCount else 0,
            url = detailUrl(table, id),
            mid = midOf(table)
        )
    }

    private fun parseSsudaBestList(html: String, boardId: String): List<PostSummary> {
        val doc = Jsoup.parse(html, BASE)
        val titleDiv = doc.select("div.title").firstOrNull { it.text().contains("웃수다 베스트") }
            ?: return emptyList()
        val section = titleDiv.parent() ?: return emptyList()
        val result = mutableListOf<PostSummary>()
        val seen = mutableSetOf<String>()

        for (a in section.select("a[href*=number=]")) {
            val href = a.attr("href")
            val id = extractNumber(href) ?: continue
            val table = extractTable(href) ?: continue
            if (!seen.add("$table:$id")) continue

            val rawTitle = a.selectFirst("span[id^=title_chk_]")?.text()?.trim()
                ?: a.text().trim()
            if (rawTitle.isBlank()) continue

            val labelMatch = boardLabelPattern.matcher(rawTitle)
            val boardLabel: String?
            val title: String
            if (labelMatch.find()) {
                boardLabel = labelMatch.group(1)
                title = labelMatch.group(2)?.trim().orEmpty().ifBlank { rawTitle }
            } else {
                boardLabel = null
                title = rawTitle
            }

            val commentCount = extractDigits(a.selectFirst("em")?.text().orEmpty())

            result += PostSummary(
                id = id,
                boardId = boardId,
                title = title,
                boardLabel = boardLabel,
                author = "",
                time = "",
                commentCount = commentCount,
                viewCount = 0,
                voteCount = 0,
                url = detailUrl(table, id),
                mid = midOf(table)
            )
        }
        return result
    }

    fun parsePostDetail(html: String, postUrl: String, mid: String, postId: String): PostDetail {
        val doc = Jsoup.parse(html, postUrl)
        val title = doc.selectFirst("#read_subject_div h2 span.color_dark, #read_subject_div h2 a span")
            ?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: doc.title().trim().ifBlank { "제목 없음" }

        val author = doc.selectFirst("#content_info span.nick .hu_nick_txt, #content_info span.nick")
            ?.text()?.trim().orEmpty()

        val profileText = doc.selectFirst("#read_profile_desc")?.text().orEmpty()
        val timeMatcher = writtenAtPattern.matcher(profileText)
        val time = if (timeMatcher.find()) timeMatcher.group(1)?.trim().orEmpty() else ""

        val voteCount = extractDigits(doc.selectFirst("#ok_div")?.text().orEmpty())
        val viewCount = doc.select("#read_profile_desc span.etc").asSequence()
            .map { it.text().trim() }
            .mapNotNull { text ->
                if (text.contains("작성") || text.contains("이동")) null
                else extractDigits(text).takeIf { it > 0 }
            }
            .lastOrNull() ?: 0
        val commentCount = extractDigits(
            doc.selectFirst("#read_subject_div span.comment_num, #read_profile_desc span.comment_num")
                ?.text().orEmpty()
        )

        val contentEl = doc.selectFirst("div.daum-wm-content div.body_editor")
            ?: doc.selectFirst("div.body_editor")
            ?: doc.selectFirst("div.daum-wm-content")
        absolutizeMedia(contentEl)
        val contentHtml = contentEl?.html().orEmpty().ifBlank {
            "<p>본문을 불러오지 못했습니다.</p>"
        }

        val comments = parseComments(doc)

        return PostDetail(
            id = postId,
            title = title,
            author = author,
            time = time,
            viewCount = viewCount,
            voteCount = voteCount,
            commentCount = if (comments.isNotEmpty()) comments.size else commentCount,
            contentHtml = contentHtml,
            url = postUrl,
            mid = mid,
            comments = comments
        )
    }

    fun parseComments(html: String): List<CommentItem> {
        val doc = Jsoup.parse(html, BASE)
        return parseComments(doc)
    }

    private fun parseComments(doc: org.jsoup.nodes.Document): List<CommentItem> {
        val result = mutableListOf<CommentItem>()
        for (li in doc.select("li[id^=comment_li_]")) {
            val id = li.id().removePrefix("comment_li_")
            if (id.isBlank()) continue
            val isReply = li.hasClass("sub_comm_bt") ||
                (li.hasAttr("name") && li.attr("name") == "sub_comm_block")
            val author = li.selectFirst("span.nick .hu_nick_txt, span.nick")?.text()?.trim().orEmpty()
            val timeRaw = li.selectFirst("span.etc")?.text()?.trim().orEmpty()
            val time = cleanCommentTime(timeRaw)
            val body = li.selectFirst("div.comment_more")?.html()?.trim().orEmpty()
            if (body.isBlank() && author.isBlank()) continue
            result += CommentItem(
                id = id,
                author = author,
                time = time,
                contentHtml = body.ifBlank { "<p></p>" },
                depth = if (isReply) 1 else 0,
                isReply = isReply,
                replyTo = null
            )
        }
        return result
    }

    private fun cleanCommentTime(raw: String): String {
        if (raw.isBlank()) return ""
        // "2026-10-05 <bMon, ...> 17:41:20" → "2026-10-05 17:41:20"
        val noTag = raw.replace(Regex("<[^>]+>"), " ")
        val parts = noTag.split(Regex("\\s+")).filter { it.isNotBlank() }
        val date = parts.firstOrNull { it.matches(Regex("\\d{4}-\\d{1,2}-\\d{1,2}")) }
        val clock = parts.firstOrNull { it.matches(Regex("\\d{1,2}:\\d{2}:\\d{2}")) }
        return listOfNotNull(date, clock).joinToString(" ").ifBlank { noTag.trim() }
    }

    private fun absolutizeMedia(root: Element?) {
        if (root == null) return
        for (img in root.select("img[src]")) {
            val abs = img.absUrl("src")
            if (abs.isNotBlank()) img.attr("src", abs)
        }
        for (a in root.select("a[href]")) {
            val abs = a.absUrl("href")
            if (abs.isNotBlank()) a.attr("href", abs)
        }
    }

    private fun extractNumber(href: String): String? {
        val m = numberInQuery.matcher(href)
        return if (m.find()) m.group(1) else null
    }

    private fun extractTable(href: String): String? {
        val m = tableInQuery.matcher(href)
        return if (m.find()) m.group(1) else null
    }

    private fun extractDigits(text: String): Int {
        val m = digitPattern.matcher(text.replace(",", ""))
        return if (m.find()) m.group(1)?.replace(",", "")?.toIntOrNull() ?: 0 else 0
    }
}
