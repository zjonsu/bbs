package com.bbs.reader.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 메모리 CookieJar. 사이트 로그인 쿠키를 host 단위로 보관한다.
 */
class AppCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val key = hostKey(url.host)
        val map = store.getOrPut(key) { ConcurrentHashMap() }
        for (cookie in cookies) {
            map[cookie.name] = cookie
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()
        for ((host, cookies) in store) {
            if (!hostMatches(host, url.host)) continue
            val expired = mutableListOf<String>()
            for ((name, cookie) in cookies) {
                if (cookie.expiresAt in 1 until now) {
                    expired += name
                    continue
                }
                if (!cookie.matches(url)) continue
                result += cookie
            }
            expired.forEach { cookies.remove(it) }
        }
        return result
    }

    fun replaceForHost(cookies: List<Cookie>) {
        for (cookie in cookies) {
            val key = hostKey(cookie.domain)
            val map = store.getOrPut(key) { ConcurrentHashMap() }
            map[cookie.name] = cookie
        }
    }

    fun clearHosts(hosts: Collection<String>) {
        for (host in hosts) {
            store.remove(hostKey(host))
            store.remove(hostKey(host.removePrefix(".")))
        }
    }

    fun clearAll() {
        store.clear()
    }

    private fun hostKey(host: String): String = host.removePrefix(".").lowercase()

    private fun hostMatches(storedHost: String, requestHost: String): Boolean {
        val stored = hostKey(storedHost)
        val req = hostKey(requestHost)
        return req == stored || req.endsWith(".$stored")
    }
}
