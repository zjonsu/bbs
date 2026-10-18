package com.bbs.reader.data.parser

import com.bbs.reader.data.model.CommentItem
import com.bbs.reader.data.model.PostDetail
import com.bbs.reader.data.model.PostSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.regex.Pattern

object SlrclubParser {
    const val MID_PREFIX = "slrclub:"
    const val BASE = "https://m.slrclub.com"

    private val pathPattern = Pattern.compile("/v/([a-zA-Z0-9_]+)/(\\d+)")
    private val pagePathPattern = Pattern.compile("/l/[a-zA-Z0-9_]+/(\\d+)")
    private val viewPattern = Pattern.compile("조회\\s*([\\d,]+)")
    private val votePattern = Pattern.compile("추천\\s*([\\d,]+)")
    private val digitPattern = Pattern.compile("(\\d[\\d,]*)")

    fun midOf(boardSlug: String): String = "$MID_PREFIX$boardSlug"

    fun boardOfMid(mid: String): String? =
        if (mid.startsWith(MID_PREFIX)) mid.removePrefix(MID_PREFIX) else null

    fun detailUrl(boardSlug: String, postId: String): String =
        "$BASE/v/$boardSlug/$postId"

    fun isLoginRequired(html: String): Boolean {
        val doc = Jsoup.parse(html)
        val err = doc.selectFirst("#err_ct .err_msg")?.text().orEmpty()
        if (err.contains("로그인")) return true
        return html.contains("로그인 후 이용해주세요")
    }

    /** page 1 HTML의 paging에서 2페이지 ID를 추출한다. */
    fun extractPage2Id(html: String): Int? {
        val doc = Jsoup.parse(html, BASE)
        val first = doc.selectFirst("div.paging a[href]") ?: return null
        val href = first.attr("href")
        val m = pagePathPattern.matcher(href)
        return if (m.find()) m.group(1)?.toIntOrNull() else null
    }

    fun parsePostList(html: String, boardId: String, boardSlug: String): List<PostSummary> {
        val doc = Jsoup.parse(html, BASE)
        val result = mutableListOf<PostSummary>()
        val seen = mutableSetOf<String>()

        for (li in doc.select("ul.list > li")) {
            val post = parseListItem(li, boardId, boardSlug) ?: continue
            if (!seen.add(post.id)) continue
            result += post
        }
        return result
    }

    private fun parseListItem(li: Element, boardId: String, boardSlug: String): PostSummary? {
        // 갤러리(썸네일): div.thumb + div.subject
        val gallerySubject = li.selectFirst("div.subject a[href*=/v/]")
        val article = li.selectFirst("div.article")
        val a = when {
            article != null -> article.selectFirst("div.subject a[href*=/v/]")
            else -> gallerySubject
        } ?: return null

        val href = a.attr("href")
        val parsed = parseDetailPath(href) ?: return null
        val (slug, id) = parsed
        val resolvedSlug = slug.ifBlank { boardSlug }

        // 제목: subject 링크 ownText (댓글 수 span 제외)
        val title = a.ownText().trim().ifBlank { a.text().trim() }
        if (title.isBlank()) return null

        val thumbSrc = li.selectFirst("div.thumb img[src]")?.attr("abs:src")
            ?.takeIf { it.isNotBlank() }
            ?: li.selectFirst("div.thumb img[src]")?.attr("src")?.let { absolutizeUrl(it) }

        val infoText = article?.selectFirst("div.article-info")?.text().orEmpty()
        val author = article?.selectFirst("div.article-info span.lop")?.text()?.trim().orEmpty()
        val time = when {
            article != null -> extractTime(infoText, author)
            else -> li.selectFirst("div.date")?.text()?.trim().orEmpty()
        }
        val viewCount = extractNamedCount(infoText, viewPattern)
        val voteCount = extractNamedCount(infoText, votePattern)
        val commentCount = when {
            article != null -> extractDigits(li.selectFirst("div.cmt2")?.text().orEmpty())
            else -> extractDigits(li.selectFirst("div.subject span.cmt")?.text().orEmpty())
        }
        val boardLabel = li.selectFirst("div.category")?.text()?.trim()?.takeIf { it.isNotBlank() }

        return PostSummary(
            id = id,
            boardId = boardId,
            title = title,
            boardLabel = boardLabel,
            author = author,
            time = time,
            commentCount = commentCount,
            viewCount = viewCount,
            voteCount = voteCount,
            url = detailUrl(resolvedSlug, id),
            mid = midOf(resolvedSlug),
            thumbnailUrl = thumbSrc
        )
    }

    private fun absolutizeUrl(src: String): String? = when {
        src.isBlank() -> null
        src.startsWith("//") -> "https:$src"
        src.startsWith("/") -> "$BASE$src"
        src.startsWith("http") -> src
        else -> src
    }

    fun parsePostDetail(html: String, postUrl: String, mid: String, postId: String): PostDetail {
        val doc = Jsoup.parse(html, postUrl)
        val title = doc.selectFirst("div.subject")?.ownText()?.trim()
            ?: doc.selectFirst("div.subject")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" - ")
                ?.trim()
            ?: "제목 없음"

        val infoText = doc.selectFirst("div.info-wrap")?.text().orEmpty()
        val author = doc.selectFirst("div.info-wrap span.lop")?.text()?.trim().orEmpty()
        val time = extractTime(infoText, author)
        val viewCount = extractNamedCount(infoText, viewPattern)
        val voteCount = extractNamedCount(infoText, votePattern)
        val commentCount = extractDigits(doc.selectFirst("#cmcnt")?.text().orEmpty())

        val contentEl = doc.selectFirst("#userct")
        absolutizeMedia(contentEl)
        val contentHtml = contentEl?.html().orEmpty().ifBlank {
            "<p>본문을 불러오지 못했습니다.</p>"
        }

        return PostDetail(
            id = postId,
            title = title,
            author = author,
            time = time,
            viewCount = viewCount,
            voteCount = voteCount,
            commentCount = commentCount,
            contentHtml = contentHtml,
            url = postUrl,
            mid = mid,
            comments = emptyList()
        )
    }

    data class CommentLoadParams(
        val bbsId: String,
        val tos: String,
        val cmrNo: String,
        val cmx: String,
        val page: String = "1",
        val split: String = "50"
    )

    fun extractCommentParams(html: String): CommentLoadParams? {
        val doc = Jsoup.parse(html)
        val box = doc.selectFirst("#comment_box") ?: return null
        val bbsId = box.attr("data-bbsid").ifBlank { return null }
        val tos = box.attr("data-tos").ifBlank { return null }
        val cmrNo = box.attr("data-cmrno").ifBlank { return null }
        val cmx = box.attr("data-cmx").ifBlank { "0" }
        val page = box.attr("data-npage").ifBlank { "1" }
        val split = box.attr("data-splno").ifBlank { "50" }
        return CommentLoadParams(bbsId, tos, cmrNo, cmx, page, split)
    }

    fun parseCommentsJson(json: String): List<CommentItem> {
        val arrayBody = extractJsonArray(json, "c") ?: return emptyList()
        return splitJsonObjects(arrayBody).mapIndexedNotNull { index, obj ->
            if (matchGroup(jsonNumberField("del"), obj, 1) == "1") return@mapIndexedNotNull null
            val id = unescapeJson(matchGroup(jsonStringField("pk"), obj, 1)).ifBlank { "c$index" }
            val author = unescapeJson(matchGroup(jsonStringField("name"), obj, 1)).trim()
            val time = unescapeJson(matchGroup(jsonStringField("dt"), obj, 1)).trim()
            var content = unescapeJson(matchGroup(jsonStringField("memo"), obj, 1))
            val replyTo = unescapeJson(matchGroup(jsonStringField("tn"), obj, 1))
                .takeIf { it.isNotBlank() && it != "null" }
            val replyFlag = matchGroup(jsonNumberField("reply"), obj, 1).toIntOrNull() ?: 0
            if (!replyTo.isNullOrBlank() && !content.contains("re_prtnm")) {
                content = """<span class="re_prtnm">[$replyTo]</span> $content"""
            }
            val depth = if (!replyTo.isNullOrBlank() || replyFlag > 0) 1 else 0
            CommentItem(
                id = id,
                author = author,
                time = time,
                contentHtml = content,
                depth = depth,
                isReply = depth > 0,
                replyTo = replyTo
            )
        }
    }

    private fun extractJsonArray(json: String, key: String): String? {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        val start = json.indexOf('[', keyIdx)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until json.length) {
            val c = json[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return json.substring(start + 1, i)
                }
            }
        }
        return null
    }

    private fun splitJsonObjects(arrayBody: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escape = false
        for (i in arrayBody.indices) {
            val c = arrayBody[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        result += arrayBody.substring(start, i + 1)
                        start = -1
                    }
                }
            }
        }
        return result
    }

    private fun jsonStringField(name: String): Pattern =
        Pattern.compile("\"$name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")

    private fun jsonNumberField(name: String): Pattern =
        Pattern.compile("\"$name\"\\s*:\\s*(-?\\d+)")

    private fun matchGroup(pattern: Pattern, text: String, group: Int): String {
        val m = pattern.matcher(text)
        return if (m.find()) m.group(group).orEmpty() else ""
    }

    private fun unescapeJson(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 5 < raw.length) {
                            val hex = raw.substring(i + 2, i + 6)
                            sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                            i += 6
                            continue
                        }
                        sb.append(next)
                    }
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun parseDetailPath(href: String): Pair<String, String>? {
        val m = pathPattern.matcher(href)
        if (!m.find()) return null
        val slug = m.group(1) ?: return null
        val id = m.group(2) ?: return null
        return slug to id
    }

    private fun extractTime(infoText: String, author: String): String {
        val cleaned = infoText
            .replace(author, "")
            .replace(Regex("\\|"), " ")
            .trim()
        // "19:47" or "10.09" style tokens before 조회
        val beforeView = cleaned.substringBefore("조회").trim()
        val parts = beforeView.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        return parts.lastOrNull().orEmpty()
    }

    private fun extractNamedCount(text: String, pattern: Pattern): Int {
        val m = pattern.matcher(text)
        return if (m.find()) extractDigits(m.group(1).orEmpty()) else 0
    }

    private fun extractDigits(text: String): Int {
        val m = digitPattern.matcher(text.replace(",", ""))
        return if (m.find()) m.group(1)?.replace(",", "")?.toIntOrNull() ?: 0 else 0
    }

    private fun absolutizeMedia(root: Element?) {
        if (root == null) return
        for (img in root.select("img[src]")) {
            val src = img.attr("src")
            if (src.startsWith("//")) {
                img.attr("src", "https:$src")
            } else if (src.startsWith("/")) {
                img.attr("src", "$BASE$src")
            }
        }
        for (a in root.select("a[href]")) {
            val href = a.attr("href")
            if (href.startsWith("//")) {
                a.attr("href", "https:$href")
            } else if (href.startsWith("/")) {
                a.attr("href", "$BASE$href")
            }
        }
    }
}
