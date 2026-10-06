package com.bbs.reader.data.parser

import com.bbs.reader.data.model.CommentItem
import com.bbs.reader.data.model.PostDetail
import com.bbs.reader.data.model.PostSummary
import org.jsoup.Jsoup
import java.util.regex.Pattern

object DamoangParser {
    const val MID_PREFIX = "damoang:"
    private const val BASE = "https://damoang.net"

    private val pathPattern = Pattern.compile("^/([a-zA-Z0-9_-]+)/(\\d+)/?$")
    private val digitPattern = Pattern.compile("(\\d+(?:\\.\\d+)?[kKmM]?)")
    private val jsonStringField = { key: String ->
        Pattern.compile("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
    }
    private val jsonNumberField = { key: String ->
        Pattern.compile("\"$key\"\\s*:\\s*(-?\\d+)")
    }

    fun midOf(board: String): String = "$MID_PREFIX$board"

    fun boardOfMid(mid: String): String? =
        if (mid.startsWith(MID_PREFIX)) mid.removePrefix(MID_PREFIX) else null

    fun parsePostList(html: String, boardId: String, mode: ListMode = ListMode.RECOMMEND): List<PostSummary> {
        val doc = Jsoup.parse(html, BASE)
        val anchors = when (mode) {
            ListMode.RECOMMEND -> doc.select("ul.divide-y li > a[href], ul.divide-border li > a[href]")
                .ifEmpty { doc.select("a.hover\\:bg-muted[href*=/]") }
            ListMode.GROUP_EMPATHY -> {
                val fromCard = doc.select("div[data-slot=card]:has(a[href=/groups]) a.widget-post-row[href]")
                if (fromCard.isNotEmpty()) {
                    fromCard
                } else {
                    val header = doc.selectFirst("a[href=/groups].hover\\:text-foreground")
                    header?.closest("div[data-slot=card]")?.select("a.widget-post-row[href]")
                        ?: emptyList()
                }
            }
        }

        val result = mutableListOf<PostSummary>()
        for (a in anchors) {
            val href = a.attr("href")
            val abs = if (href.startsWith("http")) href else "$BASE$href"
            val path = abs.removePrefix(BASE)
            val m = pathPattern.matcher(path.substringBefore('?'))
            if (!m.find()) continue
            val board = m.group(1) ?: continue
            val id = m.group(2) ?: continue

            val voteText = a.selectFirst("span.rounded-full, span[class*=rounded-full]")?.text().orEmpty()
            val voteCount = parseCount(voteText)
            val boardLabel = a.selectFirst("span.bg-muted")?.text()?.trim()
            val title = a.selectFirst("span.truncate, span.min-w-0")?.text()?.trim()
                ?: a.ownText().trim()
            if (title.isBlank()) continue
            val commentCount = a.select("span.text-primary").mapNotNull { span ->
                val t = span.text().trim()
                if (t.startsWith("[") && t.endsWith("]")) {
                    t.removeSurrounding("[", "]").toIntOrNull()
                } else null
            }.firstOrNull() ?: 0

            val metaSpans = a.select("span.text-muted-foreground span, span.text-muted-foreground")
            var viewCount = 0
            var time = ""
            for (span in metaSpans) {
                val t = span.ownText().ifBlank { span.text() }.trim()
                if (t.isBlank() || t == boardLabel) continue
                if (viewCount == 0 && t.matches(Regex(".*\\d.*")) && !t.contains("전") && !t.contains(":")) {
                    val maybeView = parseCount(t)
                    if (maybeView > 0 && maybeView != voteCount && maybeView != commentCount) {
                        viewCount = maybeView
                        continue
                    }
                }
                if (time.isEmpty() && (t.contains("전") || t.contains("-") || t.contains(":"))) {
                    time = t
                }
            }

            result += PostSummary(
                id = id,
                boardId = boardId,
                title = title,
                boardLabel = boardLabel,
                author = "",
                time = time,
                commentCount = commentCount,
                viewCount = viewCount,
                voteCount = voteCount,
                url = "$BASE/$board/$id",
                mid = midOf(board)
            )
        }
        return result.distinctBy { it.id }
    }

    fun parsePostDetailJson(json: String, board: String, postId: String): PostDetail {
        val data = extractObject(json, "data") ?: json
        val title = unescapeJson(matchGroup(jsonStringField("title"), data, 1)).ifBlank { "제목 없음" }
        val author = unescapeJson(matchGroup(jsonStringField("author"), data, 1))
        val content = unescapeJson(matchGroup(jsonStringField("content"), data, 1))
            .ifBlank { "<p>본문을 불러오지 못했습니다.</p>" }
        val created = matchGroup(jsonStringField("created_at"), data, 1)
        val time = formatIsoTime(created)
        val views = matchGroup(jsonNumberField("views"), data, 1).toIntOrNull() ?: 0
        val likes = matchGroup(jsonNumberField("likes"), data, 1).toIntOrNull() ?: 0
        val comments = matchGroup(jsonNumberField("comments_count"), data, 1).toIntOrNull() ?: 0
        return PostDetail(
            id = postId,
            title = title,
            author = author,
            time = time,
            viewCount = views,
            voteCount = likes,
            commentCount = comments,
            contentHtml = content,
            url = "$BASE/$board/$postId",
            mid = midOf(board),
            comments = emptyList()
        )
    }

    fun parseCommentsJson(json: String): List<CommentItem> {
        val arrayBody = extractJsonArray(json, "data") ?: return emptyList()
        return splitJsonObjects(arrayBody).mapNotNull { obj ->
            val id = matchGroup(jsonNumberField("id"), obj, 1)
            if (id.isBlank()) return@mapNotNull null
            val author = unescapeJson(matchGroup(jsonStringField("author"), obj, 1))
            val content = unescapeJson(matchGroup(jsonStringField("content"), obj, 1))
            val created = matchGroup(jsonStringField("created_at"), obj, 1)
            val depth = matchGroup(jsonNumberField("depth"), obj, 1).toIntOrNull() ?: 0
            CommentItem(
                id = id,
                author = author,
                time = formatIsoTime(created),
                contentHtml = content.ifBlank { "<p></p>" },
                depth = depth.coerceAtLeast(0),
                isReply = depth > 0,
                replyTo = null
            )
        }
    }

    enum class ListMode { RECOMMEND, GROUP_EMPATHY }

    private fun parseCount(text: String): Int {
        val cleaned = text.replace(",", "").trim()
        val m = digitPattern.matcher(cleaned)
        if (!m.find()) return 0
        val token = m.group(1) ?: return 0
        val num = token.replace(Regex("[kKmM]"), "").toDoubleOrNull() ?: return 0
        return when {
            token.endsWith("k", ignoreCase = true) -> (num * 1_000).toInt()
            token.endsWith("m", ignoreCase = true) -> (num * 1_000_000).toInt()
            else -> num.toInt()
        }
    }

    private fun formatIsoTime(iso: String): String {
        if (iso.isBlank()) return ""
        // 2026-10-05T09:26:22+09:00 -> 2026-10-05 09:26
        return iso.replace('T', ' ').take(16)
    }

    private fun extractObject(json: String, key: String): String? {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        val start = json.indexOf('{', keyIdx)
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
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return json.substring(start, i + 1)
                }
            }
        }
        return null
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
}
