package com.bbs.reader.data.parser

import com.bbs.reader.data.model.CommentItem
import com.bbs.reader.data.model.PostDetail
import com.bbs.reader.data.model.PostSummary
import org.jsoup.Jsoup
import java.util.regex.Matcher
import java.util.regex.Pattern

object AagagParser {
    private const val BASE = "https://aagag.com"
    private const val CDN = "https://i.aagag.com/o"

    private val idxPattern = Pattern.compile("[?&]idx=([\\w-]+)")
    private val boardFromHref = Pattern.compile("aagag\\.com/([a-zA-Z0-9_]+)/")
    private val digitPattern = Pattern.compile("(\\d+)")
    private val contentAssignPattern = Pattern.compile(
        """AAGAG_AA\.content\s*=\s*"((?:\\.|[^"\\])*)""""
    )
    private val titleAssignPattern = Pattern.compile(
        """AAGAG_AA\.title\s*=\s*"((?:\\.|[^"\\])*)""""
    )
    private val cmtCntAssignPattern = Pattern.compile("""AAGAG_AA\.cmt_cnt\s*=\s*(\d+)""")
    private val sTagPattern = Pattern.compile("""\[sTag\](.*?)\[/sTag\]""", Pattern.DOTALL)
    private val jsonStringField = { key: String ->
        Pattern.compile("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
    }
    private val jsonNumberField = { key: String ->
        Pattern.compile("\"$key\"\\s*:\\s*(-?\\d+)")
    }
    private val jsonNullOrStringField = { key: String ->
        Pattern.compile("\"$key\"\\s*:\\s*(null|\"((?:\\\\.|[^\"\\\\])*)\")")
    }

    fun parsePostList(html: String, boardId: String, boardMid: String): List<PostSummary> {
        val doc = Jsoup.parse(html, BASE)
        val articles = doc.select("a.article[href*=idx=]")
        val result = mutableListOf<PostSummary>()

        for (article in articles) {
            val href = article.attr("href")
            val id = extractIdx(href) ?: continue
            val mid = extractBoardMid(href) ?: boardMid

            val titleEl = article.selectFirst("span.title") ?: continue
            val titleClone = titleEl.clone()
            titleClone.select(".btmlayer, .cmt, .lo, .rank").remove()
            val title = titleClone.text().trim()
            if (title.isBlank()) continue

            val btm = titleEl.selectFirst(".btmlayer")
            val viewCount = extractDigits(btm?.selectFirst(".hit")?.text().orEmpty())
            val goods = btm?.select(".good") ?: emptyList()
            val commentCount = extractDigits(goods.getOrNull(0)?.text().orEmpty())
            val voteCount = extractDigits(goods.getOrNull(1)?.text().orEmpty())
            val time = btm?.selectFirst(".time")?.text()?.trim().orEmpty()

            result += PostSummary(
                id = id,
                boardId = boardId,
                title = title,
                boardLabel = null,
                author = "",
                time = time,
                commentCount = commentCount,
                viewCount = viewCount,
                voteCount = voteCount,
                url = "$BASE/$mid/?idx=$id",
                mid = mid
            )
        }
        return result.distinctBy { it.id }
    }

    fun parsePostDetail(html: String, postUrl: String, mid: String, postId: String): PostDetail {
        val doc = Jsoup.parse(html, postUrl)
        val title = doc.selectFirst("h1.title")?.text()?.trim()
            ?: unescapeJsString(matchGroup(titleAssignPattern, html, 1)).ifBlank { "제목 없음" }

        val time = doc.selectFirst("h4.other .odate, .taa_other_info .odate")?.text()?.trim()
            ?: doc.selectFirst(".ago")?.text()?.trim().orEmpty()

        val sidebar = doc.selectFirst("a.article.on, a.article.now")
        val viewCount = extractDigits(sidebar?.selectFirst(".hit")?.text().orEmpty())
        val goods = sidebar?.select(".good") ?: emptyList()
        val commentCountFromList = extractDigits(goods.getOrNull(0)?.text().orEmpty())
        val voteCount = extractDigits(goods.getOrNull(1)?.text().orEmpty())
        val commentCount = matchGroup(cmtCntAssignPattern, html, 1).toIntOrNull()
            ?: commentCountFromList

        val rawContent = unescapeJsString(matchGroup(contentAssignPattern, html, 1))
        val contentHtml = if (rawContent.isNotBlank()) {
            convertStags(rawContent)
        } else {
            doc.selectFirst("#vContent")?.html().orEmpty()
        }.ifBlank { "<p>본문을 불러오지 못했습니다.</p>" }

        return PostDetail(
            id = postId,
            title = title,
            author = "",
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

    fun parseCommentsJson(json: String): List<CommentItem> {
        if (!json.contains("\"mode\":\"success\"")) return emptyList()
        val arrayBody = extractJsonArray(json, "comment") ?: return emptyList()
        val objects = splitJsonObjects(arrayBody)
        return objects.mapNotNull { parseCommentObject(it) }
    }

    fun convertStags(content: String): String {
        val matcher = sTagPattern.matcher(content)
        val sb = StringBuffer()
        while (matcher.find()) {
            val json = matcher.group(1).orEmpty()
            val type = matchGroup(jsonStringField("m"), json, 1).ifBlank { "img" }
            val q = matchGroup(jsonStringField("q"), json, 1)
            val replacement = if (q.isBlank()) {
                ""
            } else {
                when (type) {
                    "img", "jpg" -> """<p><img src="$CDN/$q.webp" alt=""></p>"""
                    "gif", "mp4", "video" -> """<p><img src="$CDN/$q.jpg" alt="video"></p>"""
                    else -> """<p><img src="$CDN/$q.webp" alt=""></p>"""
                }
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun parseCommentObject(obj: String): CommentItem? {
        val id = matchGroup(jsonNumberField("w_idx"), obj, 1)
            .ifBlank { matchGroup(jsonStringField("w_idx"), obj, 1) }
        if (id.isBlank()) return null
        val author = unescapeJsonString(matchGroup(jsonStringField("w_nick"), obj, 1))
        val time = matchGroup(jsonStringField("stime"), obj, 1)
        val content = unescapeJsonString(matchGroup(jsonStringField("w_content"), obj, 1))
        val replyRaw = matchNullableString(obj, "w_cmt_reply")
        val depth = if (replyRaw.isNullOrBlank()) 0 else replyRaw.count { it == '|' }
        return CommentItem(
            id = id,
            author = author,
            time = time,
            contentHtml = content.ifBlank { "<p></p>" },
            depth = depth,
            isReply = depth > 0,
            replyTo = null
        )
    }

    private fun matchNullableString(obj: String, key: String): String? {
        val m = jsonNullOrStringField(key).matcher(obj)
        if (!m.find()) return null
        if (m.group(1) == "null") return null
        return unescapeJsonString(m.group(2).orEmpty())
    }

    private fun extractJsonArray(json: String, key: String): String? {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        val start = json.indexOf('[', keyIdx)
        if (start < 0) return null
        var depth = 0
        for (i in start until json.length) {
            when (json[i]) {
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

    private fun extractIdx(href: String): String? {
        val m = idxPattern.matcher(href)
        return if (m.find()) m.group(1) else null
    }

    private fun extractBoardMid(href: String): String? {
        val abs = if (href.startsWith("http")) href else "$BASE$href"
        val m = boardFromHref.matcher(abs)
        return if (m.find()) m.group(1) else null
    }

    private fun extractDigits(text: String): Int {
        val m = digitPattern.matcher(text.replace(",", ""))
        return if (m.find()) m.group(1)?.toIntOrNull() ?: 0 else 0
    }

    private fun matchGroup(pattern: Pattern, text: String, group: Int): String {
        val m = pattern.matcher(text)
        return if (m.find()) m.group(group).orEmpty() else ""
    }

    private fun unescapeJsString(raw: String): String {
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
                    '\'' -> sb.append('\'')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 5 < raw.length) {
                            val hex = raw.substring(i + 2, i + 6)
                            sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                            i += 6
                            continue
                        }
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

    private fun unescapeJsonString(raw: String): String = unescapeJsString(raw)
}
