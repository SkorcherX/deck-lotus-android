package com.decklotus.companion.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Bridges OkHttp requests with Android WebView's CookieManager.
 * Ensures Cloudflare Access CF_Authorization cookies obtained via captive portal
 * are automatically passed on all API calls.
 */
class CloudflareCookieJar : CookieJar {

    private val cookieManager: CookieManager
        get() = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        for (cookie in cookies) {
            cookieManager.setCookie(urlString, cookie.toString())
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val urlString = url.toString()
        val rawCookieHeader = cookieManager.getCookie(urlString) ?: return emptyList()

        val cookieList = mutableListOf<Cookie>()
        val pairs = rawCookieHeader.split(";")
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val value = parts[1].trim()
                val cookie = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host)
                    .path("/")
                    .build()
                cookieList.add(cookie)
            }
        }
        return cookieList
    }

    fun hasCloudflareAuthCookie(url: String): Boolean {
        val cookies = cookieManager.getCookie(url) ?: return false
        return cookies.contains("CF_Authorization", ignoreCase = true)
    }

    fun clearCookies() {
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }
}