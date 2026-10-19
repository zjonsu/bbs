package com.bbs.reader.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

object HttpClient {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val metaCharsetPattern =
        Regex("""charset\s*=\s*["']?\s*([a-zA-Z0-9_\-]+)""", RegexOption.IGNORE_CASE)

    val cookieJar = AppCookieJar()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // 일부 사이트(딴지일보 등)에서 gzip 스트림이 중간에 끊기며
        // "source exhausted prematurely" 가 발생하므로 압축을 비활성화한다.
        .addNetworkInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept-Encoding", "identity")
                .build()
            chain.proceed(request)
        }
        .followRedirects(true)
        .build()

    fun get(url: String, referer: String? = null, forceCharset: String? = null): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Accept-Encoding", "identity")
            .header("Connection", "close")
        if (referer != null) {
            builder.header("Referer", referer)
        }
        return executeWithRetry(builder.build(), forceCharset)
    }

    /**
     * Rhymix/XE jQuery.exec_json compatible request:
     * Content-Type is application/json, body is application/x-www-form-urlencoded.
     */
    fun postXeJson(url: String, fields: Map<String, String>, referer: String? = null): String {
        val encoded = encodeForm(fields)
        val body = encoded.toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Encoding", "identity")
            .header("Connection", "close")
        if (referer != null) {
            builder.header("Referer", referer)
        }
        return executeWithRetry(builder.build())
    }

    /** Standard application/x-www-form-urlencoded POST (e.g. aagag.com/api/cmt). */
    fun postForm(url: String, fields: Map<String, String>, referer: String? = null): String {
        val encoded = encodeForm(fields)
        val body = encoded.toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Encoding", "identity")
            .header("Connection", "close")
        if (referer != null) {
            builder.header("Referer", referer)
        }
        return executeWithRetry(builder.build())
    }

    private fun encodeForm(fields: Map<String, String>): String =
        fields.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

    private fun executeWithRetry(
        request: Request,
        forceCharset: String? = null,
        maxAttempts: Int = 3
    ): String {
        var lastError: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}: ${request.url}")
                    }
                    val body = response.body ?: return ""
                    val headerCharset = body.contentType()?.charset()
                    val bytes = body.bytes()
                    val charset = resolveCharset(bytes, forceCharset, headerCharset)
                    return String(bytes, charset)
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt == maxAttempts - 1) throw e
                Thread.sleep(300L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("요청 실패: ${request.url}")
    }

    private fun resolveCharset(
        bytes: ByteArray,
        forceCharset: String?,
        headerCharset: Charset?
    ): Charset {
        if (!forceCharset.isNullOrBlank()) {
            runCatching { return Charset.forName(forceCharset) }
        }
        if (headerCharset != null) return headerCharset
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val name = metaCharsetPattern.find(head)?.groupValues?.getOrNull(1)
        if (!name.isNullOrBlank()) {
            runCatching { return Charset.forName(name) }
        }
        return Charsets.UTF_8
    }
}
