package com.bbs.reader.data.parser

import com.bbs.reader.data.model.CommentItem
import com.bbs.reader.data.model.PostDetail
import com.bbs.reader.data.model.PostSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.regex.Pattern

object BobaedreamParser {
    const val MID_PREFIX = "bobaedream:"
    const val BASE = "https://m.bobaedream.co.kr"

    private val viewPathPattern = Pattern.compile("/board/bbs_view/([a-zA-Z0-9_]+)/(\\d+)")
    private val digitPattern = Pattern.compile("(\\d[\\d,]*)")

    fun midOf(boardCode: String): String = "$MID_PREFIX$boardCode"

    fun boardOfMid(mid: String): String? =
        if (mid.startsWith(MID_PREFIX)) mid.removePrefix(MID_PREFIX) else null

    fun detailUrl(boardCode: String, postId: String): String =
        "$BASE/board/bbs_view/$boardCode/$postId"

    fun listUrl(boardCode: String, page: Int): String {
        val base = "$BASE/board/new_writing/$boardCode"
        return if (page <= 1) base else "$base/$page"
    }

    fun parsePostList(html: String, boardId: String, boardCode: String): List<PostSummary> {
        val doc = Jsoup.parse(html, BASE)
        val result = mutableListOf<PostSummary>()
        val seen = mutableSetOf<String>()

        for (li in doc.select("ul.rank > li")) {
            val a = li.selectFirst("div.info a[href*=/bbs_view/]") ?: continue
            val href = a.attr("href")
            val parsed = parseViewPath(href) ?: continue
            val (code, id) = parsed
            if (!seen.add(id)) continue

            val title = a.selectFirst("span.cont")?.text()?.trim().orEmpty()
            if (title.isBlank()) continue

            val blocks = a.select("div.txt2 span.block").map { it.text().trim() }.filter { it.isNotBlank() }
            var boardLabel: String? = null
            var author = ""
            var time = ""
            var viewCount = 0
            var voteCount = 0
            for (block in blocks) {
                when {
                    block.startsWith("조회") -> viewCount = extractDigits(block)
                    block.startsWith("추천") -> voteCount = extractDigits(block)
                    TIME_PATTERN.matcher(block).matches() -> time = block
                    author.isBlank() && boardLabel != null -> author = block
                    boardLabel == null && !block.startsWith("조회") && !block.startsWith("추천") ->
                        boardLabel = block
                    author.isBlank() -> author = block
                }
            }

            val commentCount = extractDigits(li.selectFirst("div.txt5 span.num")?.text().orEmpty())
            val resolvedCode = code.ifBlank { boardCode }

            result += PostSummary(
                id = id,
                boardId = boardId,
                title = title,
                boardLabel = boardLabel,
                author = author,
                time = time,
                commentCount = commentCount,
                viewCount = viewCount,
                voteCount = voteCount,
                url = detailUrl(resolvedCode, id),
                mid = midOf(resolvedCode)
            )
        }
        return result
    }

    fun parsePostDetail(html: String, postUrl: String, mid: String, postId: String): PostDetail {
        val doc = Jsoup.parse(html, postUrl)
        val title = doc.selectFirst("h3.subject")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: "제목 없음"

        val author = doc.selectFirst("header.article-tit div.util2 div.info > span")?.text()?.trim()
            .orEmpty()
        val time = doc.selectFirst("header.article-tit time")?.text()?.trim().orEmpty()
        val viewCount = extractDigits(
            doc.selectFirst("header.article-tit span.data4")?.text().orEmpty()
        )
        val voteCount = extractDigits(
            doc.selectFirst("header.article-tit span.data3")?.text().orEmpty()
                .ifBlank { doc.selectFirst("span.good#tempPublic")?.text().orEmpty() }
        )
        val commentCount = extractDigits(
            doc.selectFirst("header.article-tit span.data1")?.text().orEmpty()
        )

        val contentEl = doc.selectFirst("div.article-body")
        absolutizeMedia(contentEl)
        // 인라인 style/script 정리
        contentEl?.select("script, style")?.remove()
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
            commentCount = commentCount.coerceAtLeast(comments.size),
            contentHtml = contentHtml,
            url = postUrl,
            mid = mid,
            comments = comments
        )
    }

    fun parseComments(html: String): List<CommentItem> =
        parseComments(Jsoup.parse(html, BASE))

    private fun parseComments(doc: org.jsoup.nodes.Document): List<CommentItem> {
        val result = mutableListOf<CommentItem>()
        val items = doc.select("div.reple_body > ul.list > li")
        items.forEachIndexed { index, li ->
            val replyEl = li.selectFirst("div.reply") ?: return@forEachIndexed
            val clone = replyEl.clone()
            clone.select("span.ico3").remove()
            val contentHtml = clone.html().trim()
            if (contentHtml.isBlank()) return@forEachIndexed

            val util = li.selectFirst("div.util")
            val author = util?.selectFirst("span.data4")?.text()?.trim().orEmpty()
            val time = util?.select("span")
                ?.map { it.text().trim() }
                ?.firstOrNull { it.isNotBlank() && !it.contains("신고") && it != author }
                .orEmpty()

            val id = li.selectFirst("[id^=repl_]")?.id()?.removePrefix("repl_")
                ?: li.selectFirst("div.util4[id^=repl_]")?.id()?.removePrefix("repl_")
                ?: "c$index"

            result += CommentItem(
                id = id,
                author = author,
                time = time,
                contentHtml = contentHtml,
                depth = 0,
                isReply = false,
                replyTo = null
            )
        }
        return result
    }

    private fun parseViewPath(href: String): Pair<String, String>? {
        val m = viewPathPattern.matcher(href)
        if (!m.find()) return null
        val code = m.group(1) ?: return null
        val id = m.group(2) ?: return null
        return code to id
    }

    private fun extractDigits(text: String): Int {
        val m = digitPattern.matcher(text.replace(",", ""))
        return if (m.find()) m.group(1)?.replace(",", "")?.toIntOrNull() ?: 0 else 0
    }

    private fun absolutizeMedia(root: Element?) {
        if (root == null) return
        for (img in root.select("img[src]")) {
            val src = img.attr("src")
            when {
                src.startsWith("//") -> img.attr("src", "https:$src")
                src.startsWith("/") -> img.attr("src", "$BASE$src")
            }
        }
        for (a in root.select("a[href]")) {
            val href = a.attr("href")
            when {
                href.startsWith("//") -> a.attr("href", "https:$href")
                href.startsWith("/") -> a.attr("href", "$BASE$href")
            }
        }
    }

    private val TIME_PATTERN: Pattern =
        Pattern.compile("^(\\d{1,2}:\\d{2}|\\d{1,2}\\.\\d{1,2}|\\d{2}\\.\\d{2}|\\d{4}-\\d{2}-\\d{2})$")
}
