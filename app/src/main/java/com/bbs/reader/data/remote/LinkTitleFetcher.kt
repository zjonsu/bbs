package com.bbs.reader.data.remote

import com.bbs.reader.data.model.PostDetail
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 본문/댓글의 외부 URL에서 페이지 제목(og:title / title / Content-Disposition)을 가져온다.
 */
object LinkTitleFetcher {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val MAX_URLS = 12
    private const val MAX_BODY_BYTES = 96 * 1024
    private const val MAX_TITLE_LEN = 80

    private val urlInTextPattern: Pattern = Pattern.compile(
        """(?i)\b((?:https?://|www\.)[^\s<>"'\]）)]+)"""
    )
    private val imageExtPattern: Pattern = Pattern.compile(
        """(?i)\.(?:jpe?g|png|gif|webp|bmp|svg|avif)(?:$|[?#])"""
    )
    private val videoExtPattern: Pattern = Pattern.compile(
        """(?i)\.(?:mp4|webm|m4v|mov|m3u8)(?:$|[?#])"""
    )
    private val youtubePattern: Pattern = Pattern.compile(
        """(?i)(?:youtube(?:-nocookie)?\.com/(?:watch\?(?:[^#]*&)?v=|embed/|shorts/|live/)|youtu\.be/)"""
    )
    private val vimeoPattern: Pattern = Pattern.compile(
        """(?i)(?:player\.)?vimeo\.com/(?:video/)?\d+"""
    )

    private val cache = ConcurrentHashMap<String, String>()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    fun collectUrls(detail: PostDetail): List<String> {
        val found = LinkedHashSet<String>()
        collectFromHtml(detail.contentHtml, found)
        for (comment in detail.comments) {
            collectFromHtml(comment.contentHtml, found)
            if (found.size >= MAX_URLS) break
        }
        return found.take(MAX_URLS)
    }

    suspend fun fetchTitles(urls: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext emptyMap()
        val semaphore = Semaphore(4)
        coroutineScope {
            urls.map { url ->
                async {
                    semaphore.withPermit {
                        val title = cache[url] ?: fetchTitle(url)?.also { cache[url] = it }
                        if (title.isNullOrBlank()) null else url to title
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    internal fun fetchTitle(url: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept-Encoding", "identity")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val dispositionTitle = filenameFromContentDisposition(
                    response.header("Content-Disposition")
                )
                if (!contentType.contains("html") && !contentType.contains("xml")) {
                    return dispositionTitle
                        ?: response.request.url.pathSegments.lastOrNull { it.isNotBlank() }
                }
                val body = response.body ?: return dispositionTitle
                val source = body.source()
                source.request(MAX_BODY_BYTES.toLong())
                val toRead = minOf(source.buffer.size, MAX_BODY_BYTES.toLong())
                if (toRead <= 0L) return dispositionTitle
                val html = source.buffer.readString(toRead, Charsets.UTF_8)
                parseHtmlTitle(html) ?: dispositionTitle
            }
        }.getOrNull()
    }

    private fun parseHtmlTitle(html: String): String? {
        val doc = Jsoup.parse(html)
        val og = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
        if (og.isNotBlank()) return cleanTitle(og)
        val twitter = doc.selectFirst("meta[name=twitter:title]")?.attr("content")?.trim().orEmpty()
        if (twitter.isNotBlank()) return cleanTitle(twitter)
        val title = doc.title().trim()
        if (title.isNotBlank()) return cleanTitle(title)
        return null
    }

    private fun cleanTitle(raw: String): String {
        val decoded = Jsoup.parse(raw).text().replace(Regex("\\s+"), " ").trim()
        if (decoded.isBlank()) return ""
        return if (decoded.length <= MAX_TITLE_LEN) decoded
        else decoded.take(MAX_TITLE_LEN - 1).trimEnd() + "…"
    }

    private fun filenameFromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val utf8 = Regex("""filename\*\s*=\s*UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)
        if (!utf8.isNullOrBlank()) {
            return cleanTitle(java.net.URLDecoder.decode(utf8.trim().trim('"'), "UTF-8"))
        }
        val plain = Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)
        return plain?.trim()?.takeIf { it.isNotBlank() }?.let(::cleanTitle)
    }

    private fun collectFromHtml(html: String, out: MutableSet<String>) {
        if (html.isBlank() || out.size >= MAX_URLS) return
        val doc = Jsoup.parseBodyFragment(html)
        for (a in doc.select("a[href]")) {
            if (out.size >= MAX_URLS) return
            val href = normalizeUrl(a.attr("href")) ?: continue
            if (!shouldFetchTitle(href)) continue
            val label = a.text().trim()
            if (label.isNotBlank() && !isUrlLikeLabel(label, href)) continue
            out += href
        }
        val matcher = urlInTextPattern.matcher(Jsoup.parseBodyFragment(html).body().text())
        while (matcher.find() && out.size < MAX_URLS) {
            val raw = matcher.group(1) ?: continue
            val cleaned = raw.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '>', '"', '\'')
            val href = normalizeUrl(cleaned) ?: continue
            if (shouldFetchTitle(href)) out += href
        }
    }

    private fun normalizeUrl(raw: String): String? {
        val href = raw.trim()
        if (href.isBlank()) return null
        return when {
            href.startsWith("http://", ignoreCase = true) ||
                href.startsWith("https://", ignoreCase = true) -> href
            href.startsWith("www.", ignoreCase = true) -> "https://$href"
            href.startsWith("//") -> "https:$href"
            else -> null
        }
    }

    private fun shouldFetchTitle(url: String): Boolean {
        if (imageExtPattern.matcher(url).find()) return false
        if (videoExtPattern.matcher(url).find()) return false
        if (youtubePattern.matcher(url).find()) return false
        if (vimeoPattern.matcher(url).find()) return false
        return true
    }

    internal fun isUrlLikeLabel(label: String, href: String): Boolean {
        val t = label.trim()
        if (t.isBlank()) return true
        if (t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true) ||
            t.startsWith("www.", ignoreCase = true)
        ) {
            return true
        }
        val compactLabel = t.replace(Regex("\\s+"), "")
        val compactHref = href.replace(Regex("\\s+"), "")
        return compactLabel.equals(compactHref, ignoreCase = true) ||
            compactLabel.equals(compactHref.removePrefix("https://").removePrefix("http://"), ignoreCase = true)
    }
}
