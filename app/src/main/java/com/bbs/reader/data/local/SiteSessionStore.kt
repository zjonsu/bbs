package com.bbs.reader.data.local

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bbs.reader.data.remote.HttpClient
import com.bbs.reader.data.repository.CommunityCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

private val Context.siteSessionDataStore by preferencesDataStore(name = "site_sessions")

/**
 * 사이트별 로그인 쿠키 저장소.
 * WebView CookieManager ↔ OkHttp CookieJar 를 동기화한다.
 */
class SiteSessionStore(private val context: Context) {
    private fun cookiesKey(communityId: String) =
        stringPreferencesKey("cookies_$communityId")

    fun isLoggedIn(communityId: String): Flow<Boolean> =
        context.siteSessionDataStore.data.map { prefs ->
            val json = prefs[cookiesKey(communityId)]
            !json.isNullOrBlank() && decodeCookies(json).isNotEmpty()
        }

    suspend fun saveFromWebView(communityId: String): Boolean {
        val auth = CommunityCatalog.findSiteAuth(communityId) ?: return false
        CookieManager.getInstance().flush()
        val collected = linkedMapOf<String, Cookie>()
        for (url in auth.cookieUrls) {
            val httpUrl = url.toHttpUrlOrNull() ?: continue
            val raw = CookieManager.getInstance().getCookie(url) ?: continue
            for (part in raw.split(';')) {
                val nv = part.trim()
                if (nv.isBlank() || !nv.contains('=')) continue
                val name = nv.substringBefore('=').trim()
                val value = nv.substringAfter('=').trim()
                if (name.isBlank()) continue
                val domain = httpUrl.topPrivateDomain() ?: httpUrl.host
                val cookie = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(domain)
                    .path("/")
                    .build()
                collected["${cookie.domain}|${cookie.name}"] = cookie
            }
        }
        if (collected.isEmpty()) return false
        val list = collected.values.toList()
        persist(communityId, list)
        HttpClient.cookieJar.replaceForHost(list)
        return looksLoggedIn(list)
    }

    suspend fun clear(communityId: String) {
        val auth = CommunityCatalog.findSiteAuth(communityId)
        context.siteSessionDataStore.edit { prefs ->
            prefs.remove(cookiesKey(communityId))
        }
        if (auth == null) return
        val cm = CookieManager.getInstance()
        for (url in auth.cookieUrls) {
            val raw = cm.getCookie(url) ?: continue
            for (part in raw.split(';')) {
                val name = part.trim().substringBefore('=').trim()
                if (name.isBlank()) continue
                cm.setCookie(url, "$name=; Max-Age=0; Path=/")
            }
        }
        cm.flush()
        HttpClient.cookieJar.clearHosts(
            auth.cookieUrls.mapNotNull { it.toHttpUrlOrNull()?.topPrivateDomain() }.distinct()
        )
    }

    /** 앱 시작 시 저장된 쿠키를 OkHttp·WebView에 복원 */
    suspend fun applyPersistedCookies() {
        val all = mutableListOf<Cookie>()
        val prefs = context.siteSessionDataStore.data.first()
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        for (auth in CommunityCatalog.siteAuthList) {
            val json = prefs[cookiesKey(auth.communityId)] ?: continue
            val cookies = decodeCookies(json)
            if (cookies.isEmpty()) continue
            all += cookies
            for (cookie in cookies) {
                for (url in auth.cookieUrls) {
                    cm.setCookie(url, cookie.toWebViewSetCookie())
                }
            }
        }
        cm.flush()
        if (all.isNotEmpty()) {
            HttpClient.cookieJar.replaceForHost(all)
        }
    }

    fun injectIntoWebView(communityId: String) {
        val auth = CommunityCatalog.findSiteAuth(communityId) ?: return
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        for (url in auth.cookieUrls) {
            val httpUrl = url.toHttpUrlOrNull() ?: continue
            for (cookie in HttpClient.cookieJar.loadForRequest(httpUrl)) {
                cm.setCookie(url, cookie.toWebViewSetCookie())
            }
        }
        cm.flush()
    }

    private suspend fun persist(communityId: String, cookies: List<Cookie>) {
        context.siteSessionDataStore.edit { prefs ->
            prefs[cookiesKey(communityId)] = encodeCookies(cookies)
        }
    }

    private fun looksLoggedIn(cookies: List<Cookie>): Boolean {
        if (cookies.isEmpty()) return false
        val names = cookies.map { it.name.lowercase() }.toSet()
        return names.any { name ->
            name.contains("sess") ||
                name.contains("login") ||
                name.contains("member") ||
                name.contains("user") ||
                name.contains("slr") ||
                name == "userid" ||
                name == "user_id"
        } || cookies.size >= 2
    }

    private fun encodeCookies(cookies: List<Cookie>): String {
        val arr = JSONArray()
        for (c in cookies) {
            arr.put(
                JSONObject()
                    .put("name", c.name)
                    .put("value", c.value)
                    .put("domain", c.domain)
                    .put("path", c.path)
                    .put("secure", c.secure)
                    .put("httpOnly", c.httpOnly)
                    .put("expiresAt", c.expiresAt)
            )
        }
        return arr.toString()
    }

    private fun decodeCookies(json: String): List<Cookie> {
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name")
                    val value = o.optString("value")
                    val domain = o.optString("domain")
                    if (name.isBlank() || domain.isBlank()) continue
                    val builder = Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(domain.removePrefix("."))
                        .path(o.optString("path").ifBlank { "/" })
                    if (o.optBoolean("secure")) builder.secure()
                    if (o.optBoolean("httpOnly")) builder.httpOnly()
                    val expires = o.optLong("expiresAt", -1L)
                    if (expires > 0) builder.expiresAt(expires)
                    add(builder.build())
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun Cookie.toWebViewSetCookie(): String = buildString {
        append(name).append('=').append(value)
        append("; Domain=").append(domain)
        append("; Path=").append(path.ifBlank { "/" })
        if (secure) append("; Secure")
        if (httpOnly) append("; HttpOnly")
    }
}
