package com.bbs.reader.ui.detail

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.regex.Pattern

/**
 * 상세 본문 HTML을 테마에 맞게 정리한다.
 * - 배경과 대비가 낮은 글자색을 제거해 기본 본문색이 적용되도록 함
 * - 연속 줄바꿈·빈 단락을 줄여 과도한 세로 여백 제거
 * - http(s) URL을 클릭 가능한 링크로 변환
 */
internal fun prepareContentHtml(html: String, darkTheme: Boolean): String {
    if (html.isBlank()) return html
    val doc = Jsoup.parseBodyFragment(html)
    val body = doc.body()

    adjustColors(body, darkTheme)
    collapseExcessiveBreaks(body)
    prepareLinks(body)

    return body.html()
}

private val urlPattern: Pattern = Pattern.compile(
    """(?i)\b((?:https?://|www\.)[^\s<>"'\]）)]+)"""
)

private fun prepareLinks(root: Element) {
    // 기존 <a> 의 상대경로·www 등을 http(s) 로 정규화
    for (a in root.select("a[href]")) {
        val href = a.attr("href").trim()
        val normalized = normalizeHref(href) ?: continue
        a.attr("href", normalized)
        if (!a.hasAttr("target")) a.attr("target", "_blank")
        a.attr("rel", "noopener noreferrer")
    }

    // 텍스트 속 미링크 URL 을 <a> 로 감쌈 (<a> 안은 제외)
    for (node in collectTextNodes(root)) {
        val parentTag = (node.parent() as? Element)?.tagName()
        if (parentTag == "a" || parentTag == "script" || parentTag == "style") continue
        linkifyTextNode(node)
    }
}

private fun collectTextNodes(root: Element): List<TextNode> {
    val result = mutableListOf<TextNode>()
    fun walk(node: Node) {
        when (node) {
            is TextNode -> if (node.wholeText.isNotBlank()) result += node
            is Element -> node.childNodes().forEach(::walk)
        }
    }
    walk(root)
    return result
}

private fun normalizeHref(href: String): String? {
    if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:", ignoreCase = true) ||
        href.startsWith("mailto:", ignoreCase = true) || href.startsWith("tel:", ignoreCase = true)
    ) {
        return null
    }
    return when {
        href.startsWith("http://", ignoreCase = true) ||
            href.startsWith("https://", ignoreCase = true) -> href
        href.startsWith("www.", ignoreCase = true) -> "https://$href"
        href.startsWith("//") -> "https:$href"
        else -> null // 사이트 내부 상대경로는 브라우저로 열지 않음
    }
}

private fun linkifyTextNode(node: TextNode) {
    val text = node.wholeText
    val matcher = urlPattern.matcher(text)
    if (!matcher.find()) return
    if (node.parent() !is Element) return

    val fragment = StringBuilder()
    var last = 0
    matcher.reset()
    while (matcher.find()) {
        val raw = matcher.group(1) ?: continue
        val cleaned = raw.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '>', '"', '\'')
        if (cleaned.length < 4) continue
        val href = normalizeHref(cleaned) ?: continue
        fragment.append(escapeHtmlText(text.substring(last, matcher.start())))
        fragment.append("""<a href="${escapeHtmlText(href)}" target="_blank" rel="noopener noreferrer">""")
        fragment.append(escapeHtmlText(cleaned))
        fragment.append("</a>")
        if (cleaned.length < raw.length) {
            fragment.append(escapeHtmlText(raw.substring(cleaned.length)))
        }
        last = matcher.end()
    }
    if (last == 0) return
    fragment.append(escapeHtmlText(text.substring(last)))
    node.before(fragment.toString())
    node.remove()
}

private fun escapeHtmlText(text: String): String =
    text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

private fun adjustColors(root: Element, darkTheme: Boolean) {
    for (el in root.select("[style],[color]")) {
        val colorAttr = el.attr("color").takeIf { it.isNotBlank() }
        if (colorAttr != null) {
            val parsed = parseCssColor(colorAttr)
            if (parsed != null && !isReadable(parsed, darkTheme)) {
                el.removeAttr("color")
            }
        }

        val style = el.attr("style")
        if (style.isBlank()) continue
        val updated = rewriteStyleColors(style, darkTheme)
        if (updated != style) {
            if (updated.isBlank()) el.removeAttr("style") else el.attr("style", updated)
        }
    }
}

private val colorPropPattern: Pattern =
    Pattern.compile("""(?i)(?:^|;)\s*color\s*:\s*([^;]+)""")

private fun rewriteStyleColors(style: String, darkTheme: Boolean): String {
    val matcher = colorPropPattern.matcher(style)
    val sb = StringBuilder()
    var last = 0
    var changed = false
    while (matcher.find()) {
        val value = matcher.group(1)?.trim().orEmpty()
        val parsed = parseCssColor(value)
        if (parsed != null && !isReadable(parsed, darkTheme)) {
            // 매치 시작이 ';' 이면 ';'는 유지하고 color 선언만 제거
            val removeFrom = if (style[matcher.start()] == ';') matcher.start() + 1 else matcher.start()
            sb.append(style, last, removeFrom)
            last = matcher.end()
            changed = true
        }
    }
    if (!changed) return style
    sb.append(style, last, style.length)
    return sb.toString()
        .replace(Regex(""";\s*;+"""), ";")
        .trim()
        .trim(';')
        .trim()
}

private data class Rgb(val r: Int, val g: Int, val b: Int)

private fun isReadable(rgb: Rgb, darkTheme: Boolean): Boolean {
    val luminance = (0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b) / 255.0
    return if (darkTheme) {
        // 다크 배경에서 거의 검정에 가까운 색만 제거 (강조색·빨강 등은 유지)
        luminance >= 0.18
    } else {
        // 라이트 배경에서 거의 흰에 가까운 색만 제거
        luminance <= 0.88
    }
}

private fun parseCssColor(raw: String): Rgb? {
    val value = raw.trim().lowercase()
    if (value.isEmpty() || value == "inherit" || value == "currentcolor" || value == "transparent") {
        return null
    }
    return when {
        value.startsWith("#") -> parseHexColor(value)
        value.startsWith("rgb") -> {
            val nums = Regex("""[\d.]+""").findAll(value).map { it.value.toFloat() }.toList()
            if (nums.size < 3) null
            else Rgb(
                nums[0].toInt().coerceIn(0, 255),
                nums[1].toInt().coerceIn(0, 255),
                nums[2].toInt().coerceIn(0, 255)
            )
        }
        else -> namedColors[value]?.let { parseHexColor(it) }
    }
}

private fun parseHexColor(value: String): Rgb? {
    val hex = when (value.length) {
        4 -> "#${value[1]}${value[1]}${value[2]}${value[2]}${value[3]}${value[3]}"
        7 -> value
        9 -> value.substring(0, 7) // ignore alpha
        else -> return null
    }
    return try {
        val n = hex.removePrefix("#").toInt(16)
        Rgb((n shr 16) and 0xFF, (n shr 8) and 0xFF, n and 0xFF)
    } catch (_: NumberFormatException) {
        null
    }
}

private val namedColors = mapOf(
    "black" to "#000000",
    "white" to "#FFFFFF",
    "red" to "#FF0000",
    "green" to "#008000",
    "blue" to "#0000FF",
    "gray" to "#808080",
    "grey" to "#808080",
    "darkgray" to "#A9A9A9",
    "darkgrey" to "#A9A9A9",
    "lightgray" to "#D3D3D3",
    "lightgrey" to "#D3D3D3",
    "silver" to "#C0C0C0",
    "navy" to "#000080",
    "maroon" to "#800000",
    "purple" to "#800080",
    "teal" to "#008080",
    "olive" to "#808000",
    "lime" to "#00FF00",
    "aqua" to "#00FFFF",
    "fuchsia" to "#FF00FF",
    "yellow" to "#FFFF00",
    "orange" to "#FFA500"
)

private fun collapseExcessiveBreaks(root: Element) {
    for (p in root.select("p").toList()) {
        if (isVisuallyEmpty(p)) p.remove()
    }
    collapseAdjacentBreaks(root)

    // 남은 연속 br / 빈 p 패턴을 문자열 단계에서도 한 번 더 정리
    val cleaned = root.html()
        .replace(Regex("""(?i)(?:\s*<br\s*/?>\s*){2,}"""), "<br>")
        .replace(Regex("""(?i)(?:\s*<p>\s*(?:&nbsp;|\u00a0|\s|<br\s*/?>)*\s*</p>\s*)+"""), "")
    root.html(cleaned)

    for (p in root.select("p").toList()) {
        if (isVisuallyEmpty(p)) p.remove()
    }
    collapseAdjacentBreaks(root)
}

private fun collapseAdjacentBreaks(parent: Element) {
    val children = parent.childNodes().toList()
    var prevWasBr = false
    for (node in children) {
        when {
            node is Element && node.tagName().equals("br", ignoreCase = true) -> {
                if (prevWasBr) node.remove() else prevWasBr = true
            }
            node is TextNode && node.text().isBlank() -> Unit
            node is Element -> {
                prevWasBr = false
                collapseAdjacentBreaks(node)
            }
            else -> prevWasBr = false
        }
    }
}

private fun isVisuallyEmpty(el: Element): Boolean {
    val text = el.text().replace('\u00a0', ' ').trim()
    if (text.isNotEmpty()) return false
    if (el.selectFirst("img, video, iframe, table, hr") != null) return false
    return el.select("br").isNotEmpty() ||
        el.html().replace(Regex("""(?i)<br\s*/?>|&nbsp;|\s"""), "").isEmpty()
}
