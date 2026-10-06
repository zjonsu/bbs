package com.bbs.reader.data.parser

import com.bbs.reader.data.model.CommentItem
import com.bbs.reader.data.model.PostDetail
import com.bbs.reader.data.model.PostSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.regex.Pattern

object DdanziParser {
    private val documentSrlPattern = Pattern.compile("document_srl=(\\d+)")
    private val pathIdPattern = Pattern.compile("/(?:free|doctu|blogs|gallery|hot_all|best_all|new_all)/(\\d+)")
    private val digitPattern = Pattern.compile("(\\d+)")

    fun parsePostList(html: String, boardId: String): List<PostSummary> {
        val doc = Jsoup.parse(html)
        val items = doc.select("#list_style > ul > li")
        val result = mutableListOf<PostSummary>()

        for (li in items) {
            if (li.hasClass("titleweek") || li.selectFirst("a.link.mtitle") == null) continue
            val link = li.selectFirst("a.link.mtitle") ?: continue
            val href = link.absUrl("href").ifBlank {
                normalizeUrl(link.attr("href"))
            }
            val id = extractDocumentId(href) ?: continue
            val titleEl = link.selectFirst("div.title") ?: continue

            val boardLabel = titleEl.selectFirst("span[style]")?.text()?.trim()
            val talkText = titleEl.selectFirst("span.talk")?.text().orEmpty()
            val commentCount = extractDigits(talkText)

            // Remove talk / attach icons for clean title
            val titleClone = titleEl.clone()
            titleClone.select("span.talk, span.attach_icon").remove()
            var title = titleClone.text().trim()
            if (!boardLabel.isNullOrBlank()) {
                title = title.removePrefix(boardLabel).trim()
            }

            val author = li.selectFirst(".asideBox .author")?.text()?.trim().orEmpty()
            val time = li.selectFirst(".asideBox .time")?.text()
                ?.replace("|", "")
                ?.trim()
                .orEmpty()
            val viewCount = extractDigits(li.selectFirst(".asideBox .hits")?.text().orEmpty())
            val voteCount = extractDigits(li.selectFirst(".asideBox .voteNum")?.text().orEmpty())
            val mid = extractMid(href) ?: "free"

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
                url = "https://www.ddanzi.com/$mid/$id",
                mid = mid
            )
        }
        return result
    }

    fun parsePostDetail(html: String, postUrl: String, mid: String, postId: String): PostDetail {
        val doc = Jsoup.parse(html, postUrl)
        val title = doc.selectFirst(".read_header .top_title h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringAfter(" - ")
                ?.trim()
            ?: "제목 없음"
        val author = doc.selectFirst(".read_header .author")?.text()?.trim().orEmpty()
        val time = doc.selectFirst(".read_header .time")?.text()?.trim().orEmpty()
        val viewCount = extractDigits(doc.selectFirst(".read_header .read")?.text().orEmpty())
        val voteCount = extractDigits(doc.selectFirst(".read_header .vote")?.text().orEmpty())
        val commentCount = extractDigits(doc.selectFirst(".mo_detail_talk .talk")?.text().orEmpty())

        val contentEl = doc.selectFirst(".read_content .xe_content")
            ?: doc.selectFirst(".xe_content")
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

    fun parseComments(commentHtml: String): List<CommentItem> {
        if (commentHtml.isBlank()) return emptyList()
        val doc = Jsoup.parseBodyFragment(commentHtml)
        val items = mutableListOf<CommentItem>()

        for (li in doc.select("li[id^=comment_]")) {
            val id = li.id().removePrefix("comment_")
            val author = li.selectFirst(".fbMeta .author a")?.ownText()?.trim()
                ?: li.selectFirst(".fbMeta .author")?.text()?.trim().orEmpty()
            val time = li.selectFirst(".fbMeta .time")?.text()?.trim().orEmpty()
            val contentHtml = li.selectFirst(".fdComment .xe_content")?.html().orEmpty()
            val replyTo = li.selectFirst(".re_com_nickname .pconName")?.text()?.trim()
            val isReply = li.hasClass("re_comment") || !replyTo.isNullOrBlank()
            val depth = estimateDepth(li)

            items += CommentItem(
                id = id,
                author = author,
                time = time,
                contentHtml = contentHtml.ifBlank { li.selectFirst(".fdComment")?.html().orEmpty() },
                depth = depth,
                isReply = isReply,
                replyTo = replyTo
            )
        }
        return items
    }

    fun extractCommentHtmlFromJson(json: String): String {
        // Lightweight extraction to avoid adding a JSON library for one field.
        val key = "\"commentHtml\":\""
        val start = json.indexOf(key)
        if (start < 0) return ""
        var i = start + key.length
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                val next = json[i + 1]
                when (next) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 5 < json.length) {
                            val hex = json.substring(i + 2, i + 6)
                            sb.append(hex.toInt(16).toChar())
                            i += 5
                        }
                    }
                    else -> sb.append(next)
                }
                i += 2
                continue
            }
            if (c == '"') break
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun estimateDepth(li: Element): Int {
        val style = li.attr("style")
        val matcher = Pattern.compile("padding-left\\s*:\\s*(\\d+)px").matcher(style)
        if (matcher.find()) {
            val px = matcher.group(1).toIntOrNull() ?: 20
            return ((px - 20) / 15).coerceAtLeast(0)
        }
        return if (li.hasClass("re_comment")) 1 else 0
    }

    private fun extractDocumentId(url: String): String? {
        val m1 = documentSrlPattern.matcher(url)
        if (m1.find()) return m1.group(1)
        val m2 = pathIdPattern.matcher(url)
        if (m2.find()) return m2.group(1)
        val trailing = Pattern.compile("/(\\d+)(?:\\?.*)?$").matcher(url)
        return if (trailing.find()) trailing.group(1) else null
    }

    private fun extractMid(url: String): String? {
        val midMatcher = Pattern.compile("[?&]mid=([^&]+)").matcher(url)
        if (midMatcher.find()) return midMatcher.group(1)
        val pathMatcher = Pattern.compile("ddanzi\\.com/([a-zA-Z0-9_]+)/\\d+").matcher(url)
        return if (pathMatcher.find()) pathMatcher.group(1) else null
    }

    private fun extractDigits(text: String): Int {
        val m = digitPattern.matcher(text.replace(",", ""))
        return if (m.find()) m.group(1).toIntOrNull() ?: 0 else 0
    }

    private fun normalizeUrl(href: String): String {
        return when {
            href.startsWith("http") -> href
            href.startsWith("/") -> "https://www.ddanzi.com$href"
            else -> "https://www.ddanzi.com/$href"
        }
    }
}
