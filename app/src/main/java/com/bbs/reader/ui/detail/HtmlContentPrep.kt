package com.bbs.reader.ui.detail

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.regex.Pattern

/**
 * 상세 본문 HTML을 테마에 맞게 정리한다.
 * - 인라인 글자색·배경색을 제거해 리더 기본색이 적용되도록 함
 * - 연속 줄바꿈·빈 단락을 줄여 과도한 세로 여백 제거
 * - http(s) URL을 클릭 가능한 링크로 변환
 * - URL 전용 링크는 가져온 페이지 제목으로 표시 (링크색·밑줄)
 * - 이미지·이모지 URL은 img 태그로, 동영상 URL은 재생 가능한 미디어로 변환
 * - script / 인라인 이벤트 제거 (미디어 재생용 JS 허용에 대비)
 */
internal fun prepareContentHtml(
    html: String,
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean,
    baseUrl: String? = null,
    linkTitles: Map<String, String> = emptyMap()
): String {
    if (html.isBlank()) return html
    val doc = if (baseUrl.isNullOrBlank()) {
        Jsoup.parseBodyFragment(html)
    } else {
        Jsoup.parseBodyFragment(html, baseUrl)
    }
    val body = doc.body()

    sanitizeScripts(body)
    stripHtmlColors(body)
    collapseExcessiveBreaks(body)
    prepareLinks(body, linkTitles)
    prepareMedia(body)

    return body.html()
}

private val urlPattern: Pattern = Pattern.compile(
    """(?i)\b((?:https?://|www\.)[^\s<>"'\]）)]+)"""
)

private fun prepareLinks(root: Element, linkTitles: Map<String, String>) {
    // 기존 <a> 의 상대경로·www 등을 http(s) 로 정규화하고 제목 적용
    for (a in root.select("a[href]")) {
        val href = a.attr("href").trim()
        val normalized = normalizeHref(href) ?: continue
        a.attr("href", normalized)
        if (!a.hasAttr("target")) a.attr("target", "_blank")
        a.attr("rel", "noopener noreferrer")
        a.addClass("ext-link")
        stripAnchorVisualOverrides(a)
        applyLinkTitle(a, normalized, linkTitles)
    }

    // 텍스트 속 미링크 URL 을 <a> 로 감쌈 (<a> 안은 제외)
    for (node in collectTextNodes(root)) {
        val parentTag = (node.parent() as? Element)?.tagName()
        if (parentTag == "a" || parentTag == "script" || parentTag == "style") continue
        linkifyTextNode(node, linkTitles)
    }
}

private fun applyLinkTitle(a: Element, href: String, linkTitles: Map<String, String>) {
    if (a.selectFirst("img, video, iframe") != null) return
    val title = lookupLinkTitle(href, linkTitles) ?: return
    val label = a.text().trim()
    if (label.isNotBlank() && !isUrlLikeAnchorLabel(label, href)) return
    a.text(title)
}

private fun lookupLinkTitle(href: String, linkTitles: Map<String, String>): String? {
    if (linkTitles.isEmpty()) return null
    linkTitles[href]?.let { return it }
    val trimmed = href.trimEnd('/')
    linkTitles[trimmed]?.let { return it }
    linkTitles.entries.firstOrNull { (key, _) ->
        key.trimEnd('/') == trimmed
    }?.value?.let { return it }
    return null
}

private fun isUrlLikeAnchorLabel(label: String, href: String): Boolean {
    val t = label.trim()
    if (t.startsWith("http://", ignoreCase = true) ||
        t.startsWith("https://", ignoreCase = true) ||
        t.startsWith("www.", ignoreCase = true)
    ) {
        return true
    }
    val compactLabel = t.replace(Regex("\\s+"), "")
    val compactHref = href.replace(Regex("\\s+"), "")
    return compactLabel.equals(compactHref, ignoreCase = true) ||
        compactLabel.equals(
            compactHref.removePrefix("https://").removePrefix("http://"),
            ignoreCase = true
        )
}

private fun stripAnchorVisualOverrides(a: Element) {
    val style = a.attr("style")
    if (style.isBlank()) return
    val cleaned = style
        .split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot {
            val key = it.substringBefore(':').trim().lowercase()
            key == "color" || key == "text-decoration" || key == "text-decoration-line"
        }
        .joinToString("; ")
    if (cleaned.isBlank()) a.removeAttr("style") else a.attr("style", cleaned)
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

private fun linkifyTextNode(node: TextNode, linkTitles: Map<String, String>) {
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
        val label = lookupLinkTitle(href, linkTitles) ?: cleaned
        fragment.append(escapeHtmlText(text.substring(last, matcher.start())))
        fragment.append(
            """<a class="ext-link" href="${escapeHtmlText(href)}" target="_blank" rel="noopener noreferrer">"""
        )
        fragment.append(escapeHtmlText(label))
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

private fun sanitizeScripts(root: Element) {
    root.select("script, style").remove()
    for (el in root.select("*").toList()) {
        val dangerous = el.attributes()
            .map { it.key }
            .filter { it.startsWith("on", ignoreCase = true) }
        for (attr in dangerous) el.removeAttr(attr)
    }
}

private val imageExtPattern: Pattern = Pattern.compile(
    """(?i)\.(?:jpe?g|png|gif|webp|bmp|svg|avif)(?:$|[?#])"""
)
private val videoExtPattern: Pattern = Pattern.compile(
    """(?i)\.(?:mp4|webm|m4v|mov|m3u8)(?:$|[?#])"""
)
private val youtubeIdPattern: Pattern = Pattern.compile(
    """(?i)(?:youtube(?:-nocookie)?\.com/(?:watch\?(?:[^#]*&)?v=|embed/|shorts/|live/)|youtu\.be/)([A-Za-z0-9_-]{6,})"""
)
private val vimeoIdPattern: Pattern = Pattern.compile(
    """(?i)(?:player\.)?vimeo\.com/(?:video/)?(\d+)"""
)

private fun prepareMedia(root: Element) {
    promoteLazyImages(root)
    absolutizeMediaUrls(root)
    markEmojiImages(root)

    for (a in root.select("a[href]").toList()) {
        val href = a.attr("abs:href").ifBlank { a.attr("href") }.trim()
        val normalized = normalizeHref(href) ?: continue
        when {
            isImageUrl(normalized) -> replaceAnchorWithImage(a, normalized)
            isDirectVideoUrl(normalized) -> replaceAnchorWithVideo(a, normalized)
            else -> {
                val yt = extractYoutubeId(normalized)
                if (yt != null) {
                    replaceAnchorWithYoutube(a, yt)
                } else {
                    val vimeo = extractVimeoId(normalized)
                    if (vimeo != null) replaceAnchorWithVimeo(a, vimeo)
                }
            }
        }
    }
}

private fun promoteLazyImages(root: Element) {
    for (img in root.select("img")) {
        val src = img.attr("src").trim()
        if (src.isNotBlank() && !src.startsWith("data:image", ignoreCase = true)) continue
        val lazy = sequenceOf(
            "data-src",
            "data-original",
            "data-lazy-src",
            "data-url",
            "data-img",
            "data-echo"
        ).map { img.attr(it).trim() }.firstOrNull { it.isNotBlank() }
            ?: img.attr("srcset").substringBefore(',').substringBefore(' ').trim()
                .takeIf { it.isNotBlank() }
        if (!lazy.isNullOrBlank()) {
            img.attr("src", lazy)
        }
    }
}

private fun absolutizeMediaUrls(root: Element) {
    for (el in root.select("img[src], video[src], source[src], iframe[src]")) {
        val src = el.attr("src").trim()
        val abs = when {
            src.startsWith("//") -> "https:$src"
            src.startsWith("http://", ignoreCase = true) ||
                src.startsWith("https://", ignoreCase = true) ||
                src.startsWith("data:", ignoreCase = true) -> src
            else -> el.absUrl("src").ifBlank { src }
        }
        if (abs.isNotBlank() && abs != src) el.attr("src", abs)
    }
    for (video in root.select("video[poster]")) {
        val poster = video.attr("poster").trim()
        when {
            poster.startsWith("//") -> video.attr("poster", "https:$poster")
        }
    }
}

private fun isImageUrl(url: String): Boolean = imageExtPattern.matcher(url).find()

private fun isDirectVideoUrl(url: String): Boolean = videoExtPattern.matcher(url).find()

private fun extractYoutubeId(url: String): String? {
    val m = youtubeIdPattern.matcher(url)
    return if (m.find()) m.group(1) else null
}

private fun extractVimeoId(url: String): String? {
    val m = vimeoIdPattern.matcher(url)
    return if (m.find()) m.group(1) else null
}

private fun replaceAnchorWithImage(a: Element, href: String) {
    if (a.selectFirst("img") != null) {
        // 이미지가 이미 있으면 링크만 유지하고 src 보정
        for (img in a.select("img")) {
            if (img.attr("src").isBlank()) img.attr("src", href)
        }
        return
    }
    val alt = a.text().trim().ifBlank { "" }
    val img = Element("img")
        .attr("src", href)
        .attr("alt", alt)
        .attr("loading", "lazy")
    if (looksLikeEmojiUrl(href)) {
        img.addClass("emoji")
    }
    a.replaceWith(img)
}

private fun markEmojiImages(root: Element) {
    for (img in root.select("img[src]")) {
        if (looksLikeEmojiImage(img)) img.addClass("emoji")
    }
}

private fun looksLikeEmojiImage(img: Element): Boolean {
    if (looksLikeEmojiUrl(img.attr("src"))) return true
    val cls = img.className().lowercase()
    if (cls.contains("emoji") || cls.contains("emoticon") || cls.contains("sticker")) return true
    val width = img.attr("width").toIntOrNull()
    val height = img.attr("height").toIntOrNull()
    return width != null && height != null && width <= 64 && height <= 64
}

private fun looksLikeEmojiUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("emoji") ||
        lower.contains("emoticon") ||
        lower.contains("sticker") ||
        lower.contains("/emo/")
}

private fun replaceAnchorWithVideo(a: Element, href: String) {
    val video = Element("video")
        .attr("src", href)
        .attr("controls", "true")
        .attr("playsinline", "true")
        .attr("preload", "metadata")
    a.replaceWith(video)
}

private fun replaceAnchorWithYoutube(a: Element, videoId: String) {
    val wrap = Element("div").attr("class", "video-embed")
    val iframe = Element("iframe")
        .attr("src", "https://www.youtube.com/embed/$videoId")
        .attr("title", "YouTube video")
        .attr("frameborder", "0")
        .attr("allowfullscreen", "true")
        .attr(
            "allow",
            "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
        )
        .attr("referrerpolicy", "strict-origin-when-cross-origin")
    wrap.appendChild(iframe)
    a.replaceWith(wrap)
}

private fun replaceAnchorWithVimeo(a: Element, videoId: String) {
    val wrap = Element("div").attr("class", "video-embed")
    val iframe = Element("iframe")
        .attr("src", "https://player.vimeo.com/video/$videoId")
        .attr("title", "Vimeo video")
        .attr("frameborder", "0")
        .attr("allowfullscreen", "true")
        .attr("allow", "autoplay; fullscreen; picture-in-picture")
    wrap.appendChild(iframe)
    a.replaceWith(wrap)
}

/** 원문 HTML의 글자색·배경색을 제거해 리더 테마색만 쓰이게 한다. */
private fun stripHtmlColors(root: Element) {
    for (el in root.select("[style],[color],[bgcolor]")) {
        if (el.hasAttr("color")) el.removeAttr("color")
        if (el.hasAttr("bgcolor")) el.removeAttr("bgcolor")

        val style = el.attr("style")
        if (style.isBlank()) continue
        val updated = stripColorStyleProps(style)
        if (updated != style) {
            if (updated.isBlank()) el.removeAttr("style") else el.attr("style", updated)
        }
    }
}

private val colorStyleKeys = setOf(
    "color",
    "background",
    "background-color",
    "background-image",
    "-webkit-text-fill-color",
    "border-color",
    "outline-color",
)

private fun stripColorStyleProps(style: String): String =
    style
        .split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { decl ->
            val key = decl.substringBefore(':').trim().lowercase()
            key in colorStyleKeys
        }
        .joinToString("; ")

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
